# State Diagrams — FASAL

Three entities in FASAL have meaningful lifecycle state machines:

1. **Vehicle** — `IDLE` ↔ `IN_TRANSIT`
2. **Route** — `PLANNED` → `ACTIVE` → `COMPLETED`
3. **Produce Listing** — `PENDING` → `IN_TRANSIT` → `DELIVERED`

Plus two cross-cutting "logical" state machines worth documenting:

4. **User Session** — the lifecycle of a Bearer token
5. **Onboarding Wizard** — the farmer registration UI state

---

## 1. Vehicle State Machine

A vehicle is created `IDLE` at a hub. When the routing engine picks it up for a route, it flips to `IN_TRANSIT` and its `current_hub_id` is updated to the **last stop** of that route. In the current build there is no "completion" endpoint — vehicles stay `IN_TRANSIT` until manually reset.

```mermaid
stateDiagram-v2
    [*] --> IDLE : Seeded by SeedRoutes<br/>or schema.sql
    IDLE --> IN_TRANSIT : RoutingEngine.persistRoute()<br/>(UPDATE vehicles SET status='IN_TRANSIT',<br/>current_hub_id=lastStop)
    IN_TRANSIT --> IDLE : POST /api/seed/reset<br/>(truncates and re-seeds)
    IN_TRANSIT --> [*] : (no "complete trip" endpoint —<br/>logically never returns to IDLE<br/>without a seed reset)
```

### Implementation Notes

* Transition `IDLE → IN_TRANSIT` happens in `RoutingEngine.persistRoute()` as part of the same transaction that inserts `routes` / `route_stops` / `route_cargo`.
* No transition exists for `IN_TRANSIT → IDLE` — once dispatched, the truck stays "on the road" forever in the demo. This is documented as a known limitation in `PROJECT_CONTEXT.md §22` (Future Work).
* If the user calls `runRouting()` again at the same hub after all trucks are `IN_TRANSIT`, the engine returns a `RouteResult` with `routeId = 0` and `humanReadableSummary = "No idle vehicle is currently available at this hub."`.

### Recovery from "All trucks IN_TRANSIT"

```mermaid
flowchart LR
    A[All trucks at hub IN_TRANSIT] -->|POST /api/seed/reset| B[Reseed - everything IDLE again]
```

---

## 2. Route State Machine

The schema permits `PLANNED`, `ACTIVE`, `COMPLETED`. The current code only ever writes `PLANNED`. `ACTIVE` is a styling hint in the Super Admin map (solid blue) — it would be entered if a future "start trip" endpoint were added. `COMPLETED` is purely defensive — it's filtered out of the renderable routes.

```mermaid
stateDiagram-v2
    [*] --> PLANNED : RoutingEngine.persistRoute()<br/>INSERT INTO routes(...status='PLANNED')
    PLANNED --> ACTIVE : (no transition implemented;<br/>reserved for future "start trip")
    ACTIVE --> COMPLETED : (no transition implemented;<br/>reserved for future "trip done")
    PLANNED --> [*] : POST /api/seed/reset<br/>(TRUNCATE routes)
    ACTIVE --> [*] : POST /api/seed/reset
    COMPLETED --> [*] : POST /api/seed/reset
```

### Where Each State Is Visible

| State | Where it appears |
|---|---|
| PLANNED | The only state actually produced by the code today. Rendered as **dashed orange** polylines on the Super Admin map. Hub Admin "View Route" expander lists these for in-transit vehicles. |
| ACTIVE | Would be rendered as **solid blue** polylines on the Super Admin map. Code path exists; no UI/API to trigger the transition. |
| COMPLETED | Routes layer renderers explicitly skip these (`if (route.status === 'COMPLETED') return;`). |

### Useful SQL to Inspect

```sql
SELECT status, COUNT(*) FROM routes GROUP BY status;
```

---

## 3. Produce Listing State Machine

Produce listings start `PENDING`. The schema allows `IN_TRANSIT` and `DELIVERED`, but the current code never moves them — they stay PENDING. The state field is defensive infrastructure for a future "pack listings onto a route" workflow.

```mermaid
stateDiagram-v2
    [*] --> PENDING : FarmerService.createListing()<br/>INSERT INTO produce_listings(...status='PENDING')
    PENDING --> IN_TRANSIT : (no transition implemented;<br/>reserved for "pack onto route")
    IN_TRANSIT --> DELIVERED : (no transition implemented;<br/>reserved for "arrived")
    PENDING --> [*] : POST /api/seed/reset
    IN_TRANSIT --> [*] : POST /api/seed/reset
    DELIVERED --> [*] : POST /api/seed/reset
```

### Visual Hint in the UI

The farmer dashboard already renders the status with a colour-coded badge — `PENDING` is grey, `IN_TRANSIT` is yellow, `DELIVERED` is green — so when these transitions are implemented later, the UI will Just Work.

---

## 4. User Session State Machine

A Bearer token is a lightweight state machine: it doesn't exist, then it exists, then it's removed.

```mermaid
stateDiagram-v2
    [*] --> AnonymousVisitor : Browser opens any FASAL page
    AnonymousVisitor --> Authenticated : POST /api/auth/login or /register OK<br/>(INSERT into sessions; localStorage.fasal_auth set)
    Authenticated --> Authenticated : every authenticated fetch<br/>(token lives forever)
    Authenticated --> AnonymousVisitor : user clicks logout<br/>(localStorage.removeItem; reload)
    Authenticated --> StaleToken : POST /api/seed/reset OR<br/>schema rebuild wipes sessions
    StaleToken --> AnonymousVisitor : manual localStorage.clear()<br/>(or proposed auto-clear on 401)
    AnonymousVisitor --> [*]
```

### Pain Point: StaleToken State

The `StaleToken` state is the bug documented in `GITHUB_ISSUES.md` Issue #3 — the token is present in `localStorage` but the server doesn't recognise it. The frontend's `isLoggedIn()` returns `true` based on `localStorage` alone, so the app routes to the dashboard, every fetch then returns `401`, and the user is stuck.

Proposed fix to make StaleToken → AnonymousVisitor automatic:

```javascript
// in api.js
if (!response.ok) {
  if (response.status === 401) clearAuth();   // auto-recover
  throw ...
}
```

---

## 5. Farmer Onboarding Wizard

The frontend's 5-step wizard is itself a state machine. `farmer.js` keeps the progression in module-level state (`onboardingState`) plus the visible step.

```mermaid
stateDiagram-v2
    [*] --> Step1_Welcome
    Step1_Welcome --> Step2_NamePhone : click "Get Started"
    Step1_Welcome --> LoginScreen : "Sign in" link
    Step2_NamePhone --> Step1_Welcome : Back
    Step2_NamePhone --> Step3_Password : Next (name OK + phone matches /^\d{10}$/)
    Step2_NamePhone --> Step2_NamePhone : invalid input — show inline error
    Step3_Password --> Step2_NamePhone : Back
    Step3_Password --> Step4_Spoke : Next (len>=6 + matches confirm)
    Step3_Password --> Step3_Password : invalid input — show inline error
    Step4_Spoke --> Step3_Password : Back
    Step4_Spoke --> Step5_Loading : "Create Account" (spoke selected)
    Step4_Spoke --> Step4_Spoke : no spoke chosen — show inline error
    Step5_Loading --> Step5_Success : POST /api/auth/register 200
    Step5_Loading --> Step5_Error : POST /api/auth/register failed
    Step5_Success --> MainApp_Dashboard : "Go to Dashboard"
    Step5_Error --> Step5_Loading : "Try Again" (retries register)
    LoginScreen --> MainApp_Dashboard : valid login
    LoginScreen --> Step1_Welcome : "Create an account" link
```

### State Persistence

* The wizard's state (name, phone, password, spokeId) is held in the JS object `onboardingState`, not in the URL or localStorage. A page refresh in the middle of the wizard restarts it from Step 1.
* The progress bar fill width is `(stepNumber / 5) * 100%`.
* Numbered step indicators take the `active` class for the current step and the `done` class for previously-completed steps.

---

## 6. Hub Admin Dashboard — Polling State

```mermaid
stateDiagram-v2
    [*] --> NotPolling
    NotPolling --> Polling_Inventory : switchSection('inventory')<br/>(starts setInterval 60s)
    Polling_Inventory --> NotPolling : switch to another section
    Polling_Inventory --> NotPolling : logout
    Polling_Inventory --> Polling_Inventory : 60s tick → refreshInventory()
```

The same pattern applies to the farmer Home tab, except the interval is 30 seconds (`DASHBOARD_REFRESH_MS`).

---

## 7. Routing Engine — Per-Call State

Within a single `runRouting(hubId)` invocation the engine moves through these internal "states" (steps), each of which can short-circuit to a terminal "Empty Result" state:

```mermaid
stateDiagram-v2
    [*] --> Step1_CalculateSurplus
    Step1_CalculateSurplus --> Step2_FindMatches : surpluses non-empty
    Step1_CalculateSurplus --> EmptyResult : no surplus
    Step2_FindMatches --> Step3_Prioritise : matches non-empty
    Step2_FindMatches --> EmptyResult : no matches
    Step3_Prioritise --> Step4_BuildRoute : vehicle available
    Step3_Prioritise --> EmptyResult : pickIdleVehicle returned null
    Step4_BuildRoute --> Step5_EvaluateCold : cargo non-empty
    Step4_BuildRoute --> EmptyResult : capacity exhausted with no cargo loaded
    Step5_EvaluateCold --> Step6_Persist
    Step6_Persist --> Step7_BuildResult
    Step7_BuildResult --> [*]
    EmptyResult --> [*]
```

Each `EmptyResult` produces a `RouteResult` with `routeId = 0`, `requiresColdStorage = false`, and a `humanReadableSummary` explaining why nothing happened — surfaced to the UI as a single information card instead of the usual 6 step cards.

---

## 8. Combined Lifecycle: Vehicle + Route + Listing

A real-world view of what state each entity is in after one successful `runRouting` call:

| Before run | After run |
|---|---|
| Vehicle: IDLE | Vehicle: IN_TRANSIT |
| Vehicle.current_hub_id: source_hub_id | Vehicle.current_hub_id: last_stop_hub_id |
| Route: (does not exist) | Route: PLANNED |
| route_stops: (does not exist) | route_stops: N rows (source + N-1 destinations) |
| route_cargo: (does not exist) | route_cargo: 1..N rows |
| Produce listings: PENDING | Produce listings: PENDING (unchanged — listing status is decoupled from routing in this build) |
| inventory rows | inventory rows (unchanged — surplus is computed, not deducted from inventory) |

⚠️ Note: the routing engine **does not** decrement `inventory.quantity_kg` after dispatch. This means a subsequent `runRouting` call against the same hub will see the same surplus and may try to dispatch the same goods again to a freshly-idle vehicle (if `seed/reset` were called). In production this would need a "consume from inventory on dispatch" step.
