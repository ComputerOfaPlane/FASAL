# User Flow Diagrams — FASAL

End-to-end journeys per persona. Each diagram is a Mermaid flowchart that renders natively on GitHub / VS Code / Obsidian.

---

## 1. Farmer — Full Journey

```mermaid
flowchart TD
    A([Open farmer.html]) --> B{Auth token in<br/>localStorage?}
    B -- No --> C[Show 5-step wizard]
    B -- Yes --> M[Show Dashboard]

    C --> S1[Step 1: Welcome screen<br/>'Your Market, Direct']
    S1 --> S2[Step 2: Name + Phone]
    S2 -- Invalid --> S2err[Show error - retry]
    S2err --> S2
    S2 -- Valid --> S3[Step 3: Password + Confirm]
    S3 -- Mismatch / <6 --> S3err[Show error - retry]
    S3err --> S3
    S3 -- Valid --> S4[Step 4: Pick village/spoke]
    S4 -- Loads --> S4api[GET /api/spokes]
    S4api --> S4
    S4 -- Submit --> S5[Step 5: Loading]
    S5 --> S5api[POST /api/auth/register]
    S5api -- 200 --> S5ok[Show 'Welcome to FASAL, name!'<br/>Save token to localStorage]
    S5api -- 400 phone in use --> S5err[Show error - Retry]
    S5err --> S5api
    S5ok --> M

    M --> Tabs{Tab?}
    Tabs -- Home --> H[Load /api/farmer/listings every 30s<br/>Show 3 summary tiles<br/>Render listings sorted by Q asc]
    Tabs -- Mandi --> Md[Mandi tab]
    Tabs -- Contracts --> Cn[Coming Soon placeholder]
    Tabs -- Messages --> Ms[Coming Soon placeholder]

    Md --> MdForm[Form: type, qty, harvest date]
    Md --> MdList[Show all listings at hub]
    MdForm -- Submit --> MdPost[POST /api/farmer/listings]
    MdPost -- 200 --> MdToast[Toast: 'Listing added!'<br/>Reload listings, clear form]
    MdPost -- Error --> MdErr[Toast: error message]

    M -- Click logout --> Out[clearAuth + reload]
    Out --> C
```

### Highlights

| Step | Validation | Endpoint |
|---|---|---|
| 2 | Name length ≥ 2, phone `/^\d{10}$/` | — |
| 3 | Password length ≥ 6, confirmation matches | — |
| 4 | One spoke must be selected | `GET /api/spokes` |
| 5 | — | `POST /api/auth/register` |

The Home tab uses `setInterval(refreshDashboard, 30000)`. The interval is cleared when the user switches away from the Home tab or logs out.

---

## 2. Hub Admin — Full Journey

```mermaid
flowchart TD
    A([Open hub-admin.html]) --> B{Auth token<br/>role=HUB_ADMIN?}
    B -- No --> L[Show login form]
    B -- Yes --> M[Show main app shell]

    L --> Ls[Submit phone + password]
    Ls --> Lapi[POST /api/auth/login]
    Lapi -- role != HUB_ADMIN --> Lerr[Show 'Access denied -<br/>Hub Admins only']
    Lerr --> L
    Lapi -- 200 + HUB_ADMIN --> Lok[Save token]
    Lok --> M

    M --> Sec{Sidebar section?}
    Sec -- Overview --> O[loadOverview]
    O --> O1[Parallel:<br/>GET /api/hub/:id/inventory<br/>GET /api/hub/:id/demand<br/>GET /api/hub/:id/vehicles<br/>GET /api/hub/:id/routes]
    O1 --> O2[Compute counts:<br/>items, demand, idle, routes-today]
    O2 --> O3[Render 4 stat cards]

    Sec -- Inventory --> I[loadInventory<br/>every 60s]
    I --> I1[GET /api/hub/:id/inventory]
    I1 --> I2[Sort by Q ascending<br/>Render table with Q badges]

    Sec -- Demand --> D[GET /api/hub/:id/demand<br/>Render table]
    Sec -- Surplus --> Sp[GET /api/hub/:id/surplus<br/>Highlight pos green / neg red]
    Sec -- Vehicles --> V[GET /api/hub/:id/vehicles<br/>Render cards]
    V --> V1{In transit?}
    V1 -- Yes --> V2[Show 'View Route' button]
    V2 -- Click --> V3[GET /api/hub/:id/routes<br/>Expand inline route detail]

    Sec -- Run Algorithm --> RA[Show intro + 'Run Now' button]
    RA -- Click --> RA0[Pre-fetch:<br/>/api/farmer/produce-types if needed<br/>/api/admin/hubs if needed<br/>/api/hub/:id/surplus snapshot]
    RA0 --> RA1[POST /api/routing/run<br/>body: id]
    RA1 --> RA2[Reveal 6 step cards<br/>400ms stagger]
    RA2 --> Card1[Card 1: Surplus Found]
    Card1 --> Card2[Card 2: Demands Matched]
    Card2 --> Card3[Card 3: Priority Order<br/>by lambda]
    Card3 --> Card4[Card 4: Route Planned<br/>haversine distances]
    Card4 --> Card5[Card 5: Cold Storage Check]
    Card5 --> Card6[Card 6: Route Saved<br/>id + status PLANNED]
```

### Inventory Auto-Refresh Lifecycle

```mermaid
stateDiagram-v2
    [*] --> NotOnInventory
    NotOnInventory --> OnInventory : switchSection('inventory')
    OnInventory --> Refreshing : every 60s
    Refreshing --> OnInventory : data rendered
    OnInventory --> NotOnInventory : switch to another section
    OnInventory --> [*] : logout
    note right of OnInventory
      inventoryInterval is set
      while on inventory section
      and cleared when leaving.
    end note
```

---

## 3. Super Admin — Full Journey

```mermaid
flowchart TD
    A([Open super-admin.html]) --> B{Auth token<br/>role=SUPER_ADMIN?}
    B -- No --> L[Show login form]
    B -- Yes --> M[Show main console]

    L --> Ls[Submit phone + password]
    Ls --> Lapi[POST /api/auth/login]
    Lapi -- role != SUPER_ADMIN --> Lerr[Show 'Access denied -<br/>Super Admins only']
    Lerr --> L
    Lapi -- 200 + SUPER_ADMIN --> M

    M --> Init[Init flow:<br/>initMap + loadStats + loadAllData<br/>+ populateHubDropdown + initQualityGraph]
    Init --> S1[GET /api/admin/overview]
    Init --> S2[GET /api/admin/hubs]
    Init --> S3[GET /api/admin/spokes]
    Init --> S4[GET /api/admin/vehicles]
    Init --> S5[GET /api/admin/routes]
    Init --> S6[GET /api/farmer/produce-types]
    S1 --> StatsBar[Stats chips:<br/>Listings, Hubs, In Transit, Routes]
    S2 --> Map[Render hub circles on Leaflet]
    S3 --> MapSpokes[Render spoke dots if toggle on]
    S4 --> MapVeh[Render truck divIcons offset around hubs]
    S5 --> MapRt[Render route polylines<br/>blue solid for ACTIVE<br/>orange dashed for PLANNED]
    S6 --> Chart[Chart.js line chart<br/>3 series: normal, cold, threshold]

    M --> Toggle{Toggle a layer?}
    Toggle -- yes --> ToggleApply[Add or remove that layer group]
    ToggleApply --> M

    M --> ClickHub[Click a hub marker]
    ClickHub --> Pop[Open popup with loading state]
    Pop --> PopFetch[Parallel:<br/>GET /api/hub/:id/surplus<br/>GET /api/hub/:id/demand]
    PopFetch --> PopFill[Render top-3 surplus<br/>+ top-3 demand<br/>+ 'Run Algorithm Here' button]
    PopFill --> RunHere{Click 'Run Algorithm Here'?}
    RunHere -- yes --> SetDropdown[Set source-hub dropdown to this hub]
    SetDropdown --> Demo

    M --> ManualPick[Select hub in demo panel]
    ManualPick --> ClickRun[Click 'Run Algorithm']
    ClickRun --> Demo

    Demo --> DemoApi[POST /api/routing/run]
    DemoApi --> DemoFly[map.flyTo source hub]
    DemoFly --> DemoMarker[Place pulsing truck marker at source]
    DemoMarker --> Loop{For each stop transition}
    Loop -- next --> DrawLeg[Draw orange polyline segment]
    DrawLeg --> Animate[Animate marker along leg<br/>~1.5s requestAnimationFrame]
    Animate --> Popup[Open destination popup with cargo + arrival Q]
    Popup --> Card[Append side-panel demo step card]
    Card --> Pause[Wait 1500ms]
    Pause --> Loop
    Loop -- done --> Summary[Final 'Route Complete' popup<br/>+ summary card]
    Summary --> Reload[loadRoutes - new route now appears in regular layer]
```

### Toggle State

The frontend keeps a `toggleState = { hubs: true, spokes: false, vehicles: true, routes: true }` object. Clicking any pill flips the flag and rebuilds (or removes) just that layer group; the rest are untouched.

`Clear All` sets every flag to false and removes every layer including the demo overlay and demo vehicle marker.

---

## 4. Cross-cutting — Logout

```mermaid
flowchart LR
    A[Click logout icon] --> B[clearInterval any polling]
    B --> C[clearAuth - localStorage.removeItem 'fasal_auth']
    C --> D[Redirect to login / onboarding screen]
    D --> E[Show fresh form]
```

There is no server-side logout endpoint — the session row in the DB stays until the next schema rebuild or `/api/seed/reset`. This is documented as a known limitation in `01_SRS_DOCUMENT.md` and `PROJECT_CONTEXT.md §22`.

---

## 5. Cross-cutting — Recovering from a Stale Token (Workaround)

If the user's token has been invalidated server-side (e.g. someone hit `/api/seed/reset` while they were logged in):

```mermaid
flowchart TD
    A[Page loads] --> B{isLoggedIn?}
    B -- yes --> C[showMainApp]
    C --> D[Authenticated fetch attempts]
    D --> E[401 Unauthorized]
    E --> F[Currently: toast error -<br/>user stuck on broken dashboard]
    F --> G([Workaround: DevTools console<br/>localStorage.clear<br/>location.reload])
    G --> H[Login form appears]
```

Proposed fix (queued in `GITHUB_ISSUES.md` Issue #3): treat any `401` in `apiFetch` as a signal to `clearAuth()`; each page's loader catches the thrown `401` and routes back to login automatically.
