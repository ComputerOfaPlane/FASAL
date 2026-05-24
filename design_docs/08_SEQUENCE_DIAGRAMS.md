# Sequence Diagrams — FASAL

End-to-end call sequences for every meaningful interaction in the system. Each diagram shows the chain from Browser → Spark routes → Service → Algorithm → JDBC → MySQL.

---

## 1. Farmer Registration

```mermaid
sequenceDiagram
    autonumber
    actor F as Farmer
    participant UI as farmer.js
    participant API as api.js
    participant FR as FarmerRoutes
    participant AR as AuthRoutes
    participant AS as AuthService
    participant DBC as DatabaseConnection
    participant DB as MySQL

    F->>UI: complete 5-step wizard
    UI->>UI: validate name, phone, password, spoke
    UI->>API: apiGet('/api/spokes') [step 4]
    API->>FR: GET /api/spokes
    FR->>DB: SELECT id, name, hub_id FROM spokes
    DB-->>FR: rows
    FR-->>API: JSON [spokes]
    API-->>UI: spokes
    UI->>F: render dropdown
    F->>UI: pick spoke + advance to step 5
    UI->>API: apiPost('/api/auth/register', payload)
    API->>AR: POST /api/auth/register
    AR->>AS: register(name, phone, pw, "FARMER", spokeId, null)
    AS->>DB: SELECT 1 FROM users WHERE phone=? LIMIT 1
    DB-->>AS: empty
    AS->>AS: hashPassword(pw) -> SHA-256 hex
    AS->>DB: INSERT INTO users(...)
    DB-->>AS: generated user_id
    AS->>AS: token = UUID.randomUUID().toString().replace("-","")
    AS->>DB: INSERT INTO sessions(id, user_id)
    DB-->>AS: ok
    AS-->>AR: { token, user_id, role, hub_id, spoke_id, name }
    AR-->>API: 200 + JSON
    API-->>UI: response
    UI->>UI: saveAuth(...) to localStorage
    UI->>F: show "Welcome, name!" + Go to Dashboard
```

---

## 2. Login (any role)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as page script
    participant API as api.js
    participant AR as AuthRoutes
    participant AS as AuthService
    participant DB as MySQL

    U->>UI: enter phone + password, click Sign In
    UI->>API: apiPost('/api/auth/login', { phone, password })
    API->>AR: POST /api/auth/login
    AR->>AS: login(phone, password)
    AS->>DB: SELECT * FROM users WHERE phone=?
    DB-->>AS: user row (with stored password_hash)
    AS->>AS: hashPassword(password) and compare
    alt match
        AS->>AS: token = UUID...replace("-","")
        AS->>DB: INSERT INTO sessions(id, user_id)
        DB-->>AS: ok
        AS-->>AR: { token, user_id, role, hub_id, spoke_id, name }
        AR-->>API: 200 + JSON
        API-->>UI: response
        UI->>UI: saveAuth(...)
        UI->>UI: showMainApp() / showDashboard()
    else mismatch
        AS-->>AR: null
        AR-->>API: 400 { "error": "Invalid phone or password." }
        API-->>UI: throws Error
        UI->>U: show inline error message
    end
```

---

## 3. Authenticated GET — Hub Inventory

```mermaid
sequenceDiagram
    autonumber
    actor H as Hub Admin
    participant UI as hub-admin.js
    participant API as api.js
    participant HR as HubRoutes
    participant AS as AuthService
    participant HS as HubService
    participant QC as QualityCalculator
    participant DB as MySQL

    H->>UI: switchSection('inventory')
    UI->>UI: setInterval(refreshInventory, 60000)
    UI->>API: apiGet('/api/hub/1/inventory')
    API->>HR: GET /api/hub/1/inventory<br/>Authorization: Bearer <token>
    HR->>AS: validateToken(token)
    AS->>DB: SELECT s.user_id, u.role,... FROM sessions s JOIN users u
    DB-->>AS: session row
    AS-->>HR: { user_id, role, hub_id, spoke_id, name }
    HR->>HS: getInventory(1)
    HS->>DB: SELECT i.*, pt.name, pt.lambda_value FROM inventory ...
    DB-->>HS: rows
    loop for each row
        HS->>QC: calculateQuality(lambda, harvestDate)
        QC-->>HS: Q value (e^(-λ·t))
        HS->>HS: set inv.currentQuality
    end
    HS-->>HR: List<Inventory>
    HR-->>API: 200 + JSON
    API-->>UI: parsed array
    UI->>UI: sort by currentQuality asc
    UI->>H: render table with Q badges and bars
```

---

## 4. Run Routing Algorithm — Full Chain (the headline diagram)

```mermaid
sequenceDiagram
    autonumber
    actor H as Hub Admin
    participant UI as hub-admin.js
    participant API as api.js
    participant RR as RoutingRoutes
    participant AS as AuthService
    participant RE as RoutingEngine
    participant QC as QualityCalculator
    participant DB as MySQL

    H->>UI: click "Run Algorithm Now"
    UI->>API: pre-fetch /api/farmer/produce-types (cached)
    UI->>API: pre-fetch /api/admin/hubs (cached)
    UI->>API: apiGet('/api/hub/1/surplus') [pre-run snapshot]
    UI->>API: apiPost('/api/routing/run', { hub_id: 1 })
    API->>RR: POST /api/routing/run
    RR->>AS: validateToken(token)
    AS-->>RR: session info
    RR->>RE: new RoutingEngine().runRouting(1)

    Note over RE: Step 1: calculateSurplus
    RE->>DB: SELECT inventory + produce_types WHERE hub_id=1
    DB-->>RE: stock per type
    RE->>DB: SELECT demand WHERE hub_id=1 GROUP BY produce_type_id
    DB-->>RE: local demand totals
    RE->>RE: compute leftover = stock - local; keep positives

    Note over RE: Step 2: findMatchingDemands
    RE->>DB: SELECT demand JOIN hubs JOIN hub_distances WHERE hub_id<>1
    DB-->>RE: candidate matches
    loop for each match
        RE->>QC: calculateQualityAtArrival(lambda, harvest, travel_hours)
        QC-->>RE: projected_Q
        RE->>RE: keep if projected_Q >= min_quality_threshold
    end

    Note over RE: Step 3: prioritiseDemands
    RE->>RE: sort matched by lambdaValue DESC

    Note over RE: Step 4: buildRoute
    RE->>DB: SELECT vehicles WHERE current_hub_id=1 AND status='IDLE' LIMIT 1
    DB-->>RE: vehicle (or none)
    RE->>RE: outStops = [ {hub: source, order: 1} ]
    loop nearest-neighbour while pending and capacity>0
        RE->>DB: SELECT travel_time_hours FROM hub_distances
        DB-->>RE: hours
        RE->>RE: pick nearest pending demand
        RE->>RE: assignedKg = min(demand_qty, remainingSurplus, remainingCapacity)
        RE->>QC: calculateQualityAtArrival(lambda, harvest, cumulativeHours)
        QC-->>RE: arrival Q for this stop
        RE->>RE: append RouteStop + RouteCargo
        RE->>RE: deduct capacity, advance currentHub
    end

    Note over RE: Step 5: evaluateColdStorage
    loop for each cargo item
        RE->>QC: calculateQualityAtArrival at its stop's cumulative hours
        QC-->>RE: q
        RE->>RE: if q < demand.min_quality_threshold: coldNeeded=true; append reason
    end

    Note over RE: Step 6: persistRoute (single transaction)
    RE->>DB: BEGIN
    RE->>DB: INSERT INTO routes(vehicle_id, status='PLANNED', requires_cold_storage)
    DB-->>RE: route_id
    RE->>DB: INSERT INTO route_stops batched
    RE->>DB: INSERT INTO route_cargo batched
    RE->>DB: UPDATE vehicles SET status='IN_TRANSIT', current_hub_id=lastStop
    RE->>DB: COMMIT

    Note over RE: Step 7: buildResult
    RE->>DB: SELECT id, name FROM hubs WHERE id IN (...)
    DB-->>RE: hub names for stops
    RE->>RE: compose humanReadableSummary string
    RE-->>RR: RouteResult
    RR-->>API: 200 + JSON
    API-->>UI: RouteResult

    UI->>UI: animateResult(result, surplusSnapshot)
    loop for cardIdx in 0..5 with 400ms stagger
        UI->>H: reveal card N
    end
    Note over UI,H: Cards: Surplus Found, Demands Matched,<br/>Priority Order, Route Planned,<br/>Cold Storage Check, Route Saved
```

---

## 5. Step-Through Algorithm Demo on the Map (Super Admin)

```mermaid
sequenceDiagram
    autonumber
    actor S as Super Admin
    participant UI as super-admin.js
    participant API as api.js
    participant RR as RoutingRoutes
    participant RE as RoutingEngine
    participant Leaflet as Leaflet map
    participant DB as MySQL

    S->>UI: select source hub from dropdown
    S->>UI: click "▶ Run Algorithm"
    UI->>API: apiPost('/api/routing/run', { hub_id })
    API->>RR: POST /api/routing/run
    RR->>RE: runRouting(hubId)
    Note over RE,DB: same 7-step chain as in §4
    RE-->>RR: RouteResult
    RR-->>API: 200 + JSON
    API-->>UI: RouteResult

    UI->>Leaflet: map.flyTo(sourceHub coords, zoom 6)
    UI->>Leaflet: add pulsing "active vehicle" divIcon at source
    loop for each stop transition (stops[i] → stops[i+1])
        UI->>Leaflet: draw orange polyline segment
        UI->>UI: animateVehicleAlong(from, to, callback)
        Note over UI: 1.5s linear interpolation<br/>via requestAnimationFrame
        UI->>Leaflet: open popup at destination with cargo + arrival Q
        UI->>S: append "demo step card" to side panel
        UI->>UI: wait 1500 ms
    end
    UI->>Leaflet: open "🎉 Route Complete" popup at final hub
    UI->>UI: setTimeout(loadRoutes, 600)
    UI->>API: apiGet('/api/admin/routes')
    API-->>UI: routes list now includes the new one
    UI->>Leaflet: redraw routeLayer with the new PLANNED route
```

---

## 6. Hub Map Popup — Lazy Load

```mermaid
sequenceDiagram
    autonumber
    actor S as Super Admin
    participant Leaflet as Leaflet
    participant UI as super-admin.js
    participant API as api.js
    participant HR as HubRoutes
    participant DB as MySQL

    S->>Leaflet: click a green hub circle
    Leaflet->>Leaflet: open popup with loading state
    Leaflet->>UI: 'popupopen' event
    UI->>API: apiGet('/api/hub/{id}/surplus') (parallel)
    UI->>API: apiGet('/api/hub/{id}/demand') (parallel)
    par
        API->>HR: GET /api/hub/{id}/surplus
        HR->>DB: SELECT inventory SUM ... + SELECT demand SUM ...
        DB-->>HR: data
        HR-->>API: JSON
        API-->>UI: surplus rows
    and
        API->>HR: GET /api/hub/{id}/demand
        HR->>DB: SELECT demand JOIN produce_types
        DB-->>HR: rows
        HR-->>API: JSON
        API-->>UI: demand rows
    end
    UI->>UI: pick top 3 of each
    UI->>Leaflet: popup.setContent(new HTML with "Run Algorithm Here" button)
```

---

## 7. Frontend Static-File Fetch (post-fix)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant B as Browser
    participant M as Main.java<br/>get("/*") catch-all
    participant FS as Disk (frontend-web/)

    U->>B: navigate to http://localhost:4567/farmer.html
    B->>M: GET /farmer.html
    M->>M: path != /api/* → ok
    M->>M: resolve to frontendRoot + "/farmer.html"
    M->>M: getCanonicalPath check (no traversal)
    M->>FS: read farmer.html
    FS-->>M: bytes
    M->>M: setType('text/html; charset=UTF-8')
    M-->>B: 200 + body stream
    B->>U: render page
    B->>M: GET /css/base.css
    M-->>B: 200 + text/css
    B->>M: GET /js/api.js
    M-->>B: 200 + application/javascript
    B->>M: GET /js/farmer.js
    M-->>B: 200 + application/javascript
```

---

## 8. POST `/api/seed/reset`

```mermaid
sequenceDiagram
    autonumber
    actor S as Anyone
    participant API as api.js or curl
    participant SR as SeedRoutes
    participant AS as AuthService
    participant DB as MySQL

    S->>API: POST /api/seed/reset
    API->>SR: route handler
    SR->>DB: SET FOREIGN_KEY_CHECKS = 0
    loop for each table (route_cargo, route_stops, routes, ..., hubs)
        SR->>DB: TRUNCATE TABLE ...
    end
    SR->>DB: SET FOREIGN_KEY_CHECKS = 1

    SR->>DB: BEGIN (setAutoCommit(false))
    SR->>DB: INSERT hubs (6 rows, batch)
    SR->>DB: INSERT hub_distances (30 rows, batch)
    SR->>DB: INSERT spokes (18 rows, batch)
    SR->>DB: INSERT produce_types (10 rows, batch)

    Note over SR: For each user row, call<br/>AuthService.hashPassword(plain)<br/>so the hash matches the API's hash.
    SR->>AS: hashPassword("admin123") / "hub123" / "farmer123"
    AS-->>SR: hex hashes
    SR->>DB: INSERT users (10 rows, batch)

    SR->>DB: INSERT inventory (22 rows, dates from LocalDate.now())
    SR->>DB: INSERT demand (14 rows, batch)
    SR->>DB: INSERT vehicles (12 rows, batch)
    SR->>DB: COMMIT

    SR-->>API: 200 + { "message": "Database reset and reseeded successfully" }
```

Note: `/api/seed/reset` truncates the `sessions` table too — every previously-issued token becomes invalid. Clients should `localStorage.clear()` and log in again after a reset.
