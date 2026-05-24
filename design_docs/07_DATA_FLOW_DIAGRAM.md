# Data Flow Diagram (DFD) — FASAL

DFDs are presented at three levels, following Yourdon/DeMarco conventions:

* **Level 0** — Context diagram: the whole system as one bubble, with external entities and data flows in/out.
* **Level 1** — Major processes inside the system, plus data stores.
* **Level 2** — Decomposition of the routing engine into its 7 sub-processes.

Mermaid's `flowchart` notation is used. Shape conventions:

| Shape | Mermaid | Meaning |
|---|---|---|
| Rectangle | `[Process]` | A process |
| Stadium | `([External entity])` | An external actor |
| Cylinder | `[(Data Store)]` | A persistent data store |
| Arrow with label | `-->|...|` | A labelled data flow |

---

## 1. Level 0 — Context Diagram

The entire FASAL system is treated as one black box. External entities are the three user personas plus the routing engine's "trigger" (effectively, a Hub Admin or Super Admin pressing a button).

```mermaid
flowchart LR
    F((Farmer))
    H((Hub Admin))
    S((Super Admin))

    FASAL[FASAL System]

    F -->|Register: name, phone, password, spoke| FASAL
    F -->|Login: phone, password| FASAL
    F -->|Create listing: type, qty, harvest_date| FASAL
    FASAL -->|Token, own listings with live Q| F

    H -->|Login: phone, password| FASAL
    H -->|Run routing: hub_id| FASAL
    FASAL -->|Token, inventory, demand, surplus,<br/>vehicles, RouteResult, routes| H

    S -->|Login: phone, password| FASAL
    S -->|Toggle layers, run demo| FASAL
    S -->|POST /api/seed/reset| FASAL
    FASAL -->|Token, hubs, spokes, vehicles, routes,<br/>overview stats, RouteResult| S
```

---

## 2. Level 1 — Major Processes Inside FASAL

This unpacks the FASAL bubble into 7 numbered processes and the 5 persistent data stores. Same external entities as before.

```mermaid
flowchart TB
    F((Farmer))
    H((Hub Admin))
    S((Super Admin))

    P1[1.0 Authenticate]
    P2[2.0 Manage Listings]
    P3[3.0 Hub Read Operations]
    P4[4.0 Run Routing Engine]
    P5[5.0 Admin Read Operations]
    P6[6.0 Serve Static Frontend]
    P7[7.0 Reset Demo Data]

    DS1[(D1 users + sessions)]
    DS2[(D2 reference: hubs, hub_distances,<br/>spokes, produce_types)]
    DS3[(D3 operational:<br/>produce_listings, inventory, demand)]
    DS4[(D4 fleet: vehicles)]
    DS5[(D5 routes + route_stops + route_cargo)]

    F -->|Reg / Login req| P1
    H -->|Login req| P1
    S -->|Login req| P1
    P1 -->|"INSERT users; SELECT users;<br/>INSERT sessions"| DS1
    P1 -->|Token + profile| F
    P1 -->|Token + profile| H
    P1 -->|Token + profile| S

    F -->|GET own listings;<br/>POST new listing| P2
    P2 -->|"SELECT/INSERT produce_listings"| DS3
    P2 -->|"SELECT spokes,<br/>produce_types"| DS2
    P2 -->|Listings w/ live Q| F

    H -->|GET inv/dem/sur/veh/routes for hub| P3
    P3 -->|"SELECT inventory, demand"| DS3
    P3 -->|"SELECT vehicles"| DS4
    P3 -->|"SELECT routes,<br/>route_stops, route_cargo"| DS5
    P3 -->|"SELECT produce_types,<br/>hubs"| DS2
    P3 -->|Tables + computed surplus| H

    H -->|POST routing/run| P4
    S -->|POST routing/run| P4
    P4 -->|Read inventory, demand,<br/>distances, produce_types| DS3
    P4 -->|Read produce_types,<br/>hub_distances| DS2
    P4 -->|Pick IDLE vehicle;<br/>UPDATE to IN_TRANSIT| DS4
    P4 -->|INSERT routes,<br/>route_stops, route_cargo| DS5
    P4 -->|RouteResult JSON| H
    P4 -->|RouteResult JSON| S

    S -->|GET admin/hubs/spokes/vehicles/<br/>routes/overview| P5
    P5 -->|SELECT counts and lists| DS1
    P5 -->|SELECT hubs, spokes| DS2
    P5 -->|SELECT vehicles| DS4
    P5 -->|"SELECT routes,<br/>route_stops, route_cargo"| DS5
    P5 -->|Overview + lists| S

    F -->|GET /farmer.html, css, js| P6
    H -->|GET /hub-admin.html, css, js| P6
    S -->|GET /super-admin.html, css, js| P6
    P6 -->|file bytes from frontend-web/| F
    P6 -->|file bytes| H
    P6 -->|file bytes| S

    S -->|POST seed/reset| P7
    P7 -->|TRUNCATE then INSERT| DS1
    P7 -->|TRUNCATE then INSERT| DS2
    P7 -->|TRUNCATE then INSERT| DS3
    P7 -->|TRUNCATE then INSERT| DS4
    P7 -->|TRUNCATE then INSERT| DS5
    P7 -->|Success message| S
```

### Process Catalogue

| # | Process | Inputs | Outputs | Reads | Writes |
|---|---|---|---|---|---|
| 1.0 | Authenticate | phone, password, [name, role, spoke/hub] | token, profile | D1, D2 | D1 |
| 2.0 | Manage Listings | listing payload / GET request | listings with Q | D2, D3 | D3 |
| 3.0 | Hub Read Operations | hub_id | tables for that hub | D2, D3, D4, D5 | — |
| 4.0 | Run Routing Engine | hub_id | RouteResult | D2, D3, D4 | D4, D5 |
| 5.0 | Admin Read Operations | — | system-wide lists + stats | D1, D2, D4, D5 | — |
| 6.0 | Serve Static Frontend | URL path | HTML/CSS/JS bytes | filesystem | — |
| 7.0 | Reset Demo Data | — | success message | — | D1..D5 |

---

## 3. Level 2 — Decomposition of 4.0 "Run Routing Engine"

The most complex process by far is the routing engine. Expanded here into its 7 sub-processes matching the code structure (`RoutingEngine.runRouting()`).

```mermaid
flowchart TD
    HUB[hub_id input]
    OUT[RouteResult output]

    P41[4.1 calculateSurplus]
    P42[4.2 findMatchingDemands]
    P43[4.3 prioritiseDemands]
    P44[4.4 buildRoute<br/>greedy nearest neighbour]
    P45[4.5 evaluateColdStorage]
    P46[4.6 persistRoute<br/>single transaction]
    P47[4.7 buildResult]

    D2[(D2 reference)]
    D3[(D3 operational)]
    D4[(D4 vehicles)]
    D5[(D5 routes + stops + cargo)]

    HUB --> P41
    P41 -->|"SELECT inventory + produce_types<br/>SELECT demand SUM"| D3
    P41 -->|"List of Surplus<br/>{produceTypeId, qty,<br/>harvestDate, lambda}"| P42

    P42 -->|"SELECT demand JOIN hubs<br/>JOIN hub_distances<br/>WHERE hub_id != source"| D3
    P42 -->|hub_distances reads| D2
    P42 -->|"List of MatchedDemand<br/>filtered by min_quality"| P43

    P43 -->|sort by lambda desc| P44

    P44 -->|"pick IDLE vehicle"| D4
    P44 -->|"hub_distances reads<br/>during nearest-neighbour loop"| D2
    P44 -->|"Stop list + Cargo list +<br/>qualityAtEachStop"| P45

    P45 -->|"replay cumulative hours<br/>and project Q per stop"| D2
    P45 -->|"requiresColdStorage flag<br/>+ reason string"| P46

    P46 -->|"INSERT routes<br/>INSERT route_stops<br/>INSERT route_cargo"| D5
    P46 -->|"UPDATE vehicles SET<br/>status, current_hub_id"| D4
    P46 -->|route_id, vehicle metadata| P47

    P47 -->|"fillStopHubNames:<br/>SELECT name FROM hubs<br/>WHERE id IN (...)"| D2
    P47 -->|"compose human-readable summary"| OUT
```

### 7 Sub-process Definitions

| # | Sub-process | Pure / IO | DB tables involved |
|---|---|---|---|
| 4.1 | `calculateSurplus` | IO | reads inventory, demand, produce_types |
| 4.2 | `findMatchingDemands` | IO | reads demand, produce_types, hubs, hub_distances |
| 4.3 | `prioritiseDemands` | Pure | none |
| 4.4 | `buildRoute` | IO | reads vehicles (pickIdleVehicle), hub_distances |
| 4.5 | `evaluateColdStorage` | IO | reads hub_distances |
| 4.6 | `persistRoute` | IO | writes routes, route_stops, route_cargo; updates vehicles |
| 4.7 | `buildResult` | IO | reads hubs for join-back of names |

### Transactional Boundaries

Sub-process 4.6 is the only one that **writes** to the database. It wraps all three INSERTs and the one UPDATE in `conn.setAutoCommit(false); ... conn.commit();`. If any insert or update fails, the whole route insertion is rolled back. (Currently there is no explicit `rollback()` in the catch block — an exception simply prevents `commit()` from running, and the JDBC connection's auto-close discards the uncommitted transaction.)

---

## 4. Data Dictionary (Selected Flows)

| Flow | Shape (JSON) | Origin process | Destination process |
|---|---|---|---|
| Login req | `{ phone, password }` | Browser | 1.0 |
| Login resp | `{ token, user_id, role, hub_id, spoke_id, name }` | 1.0 | Browser |
| Listing create req | `{ produce_type_id, quantity_kg, harvest_date }` | Browser | 2.0 |
| Listing read resp | `[ { id, farmerId, produceTypeId, quantityKg, harvestDate, hubId, status, createdAt, produceName, lambdaValue, currentQuality } ]` | 2.0 | Browser |
| Hub inventory resp | `[ Inventory ]` with `currentQuality` computed in Java | 3.0 | Browser |
| Hub surplus resp | `[ { produce_type_id, produce_type, inventory_qty, demand_qty, surplus_qty } ]` (snake_case keys) | 3.0 | Browser |
| Routing req | `{ hub_id }` | Browser | 4.0 |
| Routing resp | `RouteResult` JSON — see PROJECT_CONTEXT.md §8.3 | 4.0 | Browser |
| Admin overview resp | `{ total_listings, active_routes, idle_vehicles, hubs_count, total_users, total_vehicles }` | 5.0 | Browser |
| Static asset resp | HTML / CSS / JS / image bytes | 6.0 | Browser |
| Seed reset resp | `{ message: "Database reset and reseeded successfully" }` | 7.0 | Browser |

---

## 5. Trust Boundary

The diagrams above show only one trust boundary: between the browser and the backend. Everything inside the backend is in one JVM process with the same trust level. The JDBC link to MySQL is also inside the trust boundary (loopback on `localhost:3306`).

```mermaid
flowchart LR
    subgraph "Untrusted: end user's browser"
        UI[FASAL SPA]
    end
    subgraph "Trusted: developer/operator machine"
        BE[Spark backend]
        DB[(MySQL)]
        BE <--> DB
    end
    UI -->|"HTTP + Bearer token"| BE
    BE -->|"HTTP responses"| UI
```

Implications:

* All input from the browser must be treated as hostile → use `PreparedStatement` everywhere (already done).
* Tokens stored in `localStorage` are vulnerable to XSS → every HTML insertion goes through `esc()` (already done).
* CORS is `*` for the demo — acceptable inside this trust boundary; tighten for production.
