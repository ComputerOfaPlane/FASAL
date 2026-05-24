# FASAL — Complete Project Context

> **What this document is.** A single self-contained reference covering the entire FASAL project end-to-end: domain concepts, architecture, database schema, backend code, frontend code, build steps, known bugs, applied fixes, and runtime behaviour. Drop this into any LLM and it will know everything about the project without further explanation. Equally useful as a human reference.
>
> **Repository:** <https://github.com/computerofaplane/fasal>
> **Author of context file:** captured during a Windows 11 / Edge 148 debugging session.

---

## Table of Contents

1. [What FASAL Is](#1-what-fasal-is)
2. [Domain Concepts (Q(t), λ, Cold Storage)](#2-domain-concepts)
3. [Tech Stack & Architecture](#3-tech-stack--architecture)
4. [Project File Layout](#4-project-file-layout)
5. [Database Schema (13 Tables)](#5-database-schema)
6. [Seed Data (What's in the Demo DB)](#6-seed-data)
7. [The Routing Algorithm — Deep Dive](#7-the-routing-algorithm)
8. [Backend Java Code — Class by Class](#8-backend-java-code)
9. [HTTP API Reference (Every Endpoint)](#9-http-api-reference)
10. [Authentication & Sessions](#10-authentication--sessions)
11. [Frontend — Shared (api.js, base.css)](#11-frontend--shared)
12. [Frontend — Farmer Portal](#12-frontend--farmer-portal)
13. [Frontend — Hub Admin Panel](#13-frontend--hub-admin-panel)
14. [Frontend — Super Admin / National Console](#14-frontend--super-admin)
15. [Build System (pom.xml)](#15-build-system)
16. [Start Scripts (start.sh / start.bat)](#16-start-scripts)
17. [How to Run from a Cold Start](#17-how-to-run)
18. [Demo Accounts (All 10)](#18-demo-accounts)
19. [Known Bugs & The Fixes Applied](#19-known-bugs--fixes-applied)
20. [Coding Conventions & Stylistic Notes](#20-coding-conventions)
21. [GitHub Issues Filed / To File](#21-github-issues)
22. [Future Work & Limitations](#22-future-work)

---

## 1. What FASAL Is

**FASAL = Fast And Scalable Agricultural Logic.** A complete three-tier demo of an agricultural-logistics platform for India:

* **Farmers** list freshly harvested produce at their nearest village ("spoke").
* **Hubs** (six big city centres — Delhi, Mumbai, Nagpur, Chennai, Kolkata, Ahmedabad) collect produce from spokes and act as warehouses.
* **Vehicles** (trucks) move produce between hubs.
* **Demand** is recorded per hub: "we want N kg of X at minimum freshness Q".
* A **routing algorithm** decides which trucks to dispatch where, taking into account:
  * which hub has too much of what (surplus),
  * which hub needs it (demand),
  * how long it takes to drive there (distance),
  * how fresh the produce will still be when it arrives (Q(t) projection),
  * whether cold storage is needed to keep it fresh enough.

The project is deliberately **self-contained**: one Maven backend (Java + Spark + JDBC), one MySQL database, three plain HTML pages. **No npm, no Node, no Python, no Docker.**

It exists primarily as an educational / demo artefact — a runnable end-to-end example that a beginner can read and understand.

---

## 2. Domain Concepts

### 2.1 The Freshness Function — Q(t)

Produce freshness decays exponentially with time:

```
Q(t) = e^(-λ · t)
```

Where:
| Symbol | Meaning |
|---|---|
| `t` | Days elapsed since harvest |
| `λ` (lambda) | Decay rate per day (specific to produce type) |
| `Q(t)` | Freshness; ranges from `1.0` (just picked) → `0.0` (rotten) |

The whole UI shows Q as a percentage. The colour bands used in the frontend:

| Q(t) range | Class | Colour | Meaning |
|---|---|---|---|
| ≥ 0.70 | `green` | green | Fresh |
| 0.40 – 0.69 | `yellow` | amber | Ageing |
| < 0.40 | `red` | red | At risk |

### 2.2 Lambda Values for the 10 Seeded Produce Types

| ID | Produce | λ (per day) | ≈ shelf life unrefrigerated | Category |
|---|---|---|---|---|
| 1 | milk | 0.45 | 1–2 days | Very fast decay |
| 2 | spinach | 0.35 | 2–3 days | Very fast decay |
| 3 | banana | 0.12 | 5–7 days | Medium decay |
| 4 | tomato | 0.10 | 7–10 days | Medium decay |
| 5 | mango | 0.08 | 8–12 days | Medium decay |
| 6 | apple | 0.025 | 30–40 days | Slow decay |
| 7 | potato | 0.015 | 60–90 days | Very slow decay |
| 8 | onion | 0.018 | 50–80 days | Very slow decay |
| 9 | wheat | 0.002 | 1–2 years | Near-zero decay |
| 10 | rice | 0.001 | 2+ years | Near-zero decay |

### 2.3 Projected Freshness at Arrival

Convert truck travel time (hours) to days, add to days-since-harvest, then plug into Q(t):

```
projected_Q = e^( -λ · (days_since_harvest + travel_hours / 24) )
```

### 2.4 Days Until Threshold

Solve Q(t) = threshold for t:

```
threshold = e^(-λ · t)
ln(threshold) = -λ · t
t = -ln(threshold) / λ
```

If `λ = 0` (e.g. essentially imperishable grains) the formula returns `+∞`.

### 2.5 Cold Storage

A demand row carries `min_quality_threshold` — the buyer's lowest acceptable arrival freshness. The routing engine projects Q at the destination and compares: if projected < min, the route gets flagged `requires_cold_storage = true` and a reason string is recorded ("tomato would arrive at Q=0.38, below minimum Q=0.50").

In the Super Admin freshness-decay chart, cold storage is modelled as **slowing decay to 30% of normal** — i.e. the cold-storage line is `Q(t) = e^(-λ · 0.3 · t)`. This is a simulation/visualisation; the backend's cold-storage check is a binary YES/NO flag, not a continuous rate adjustment.

### 2.6 Hubs vs Spokes

* A **hub** is one of six big city centres (warehouse + dispatch).
* A **spoke** is a smaller village or town that funnels its produce to its parent hub.
* Three spokes per hub, 18 spokes total.
* Farmers register against a spoke. Hub admins manage a hub.

---

## 3. Tech Stack & Architecture

### 3.1 Backend

| Dependency | Version | Role |
|---|---|---|
| Java | 11+ | Language |
| Maven | 3.6+ | Build tool |
| Spark Java (`com.sparkjava:spark-core`) | 2.9.4 | HTTP framework, embeds Jetty 9.4 |
| Gson (`com.google.code.gson:gson`) | 2.10.1 | JSON serialisation |
| MySQL Connector/J (`com.mysql:mysql-connector-j`) | 8.0.33 | JDBC driver |
| SLF4J Simple (`org.slf4j:slf4j-simple`) | 2.0.7 | Logging facade (silences Spark warnings) |
| Maven Shade Plugin | 3.4.1 | Builds one runnable fat JAR |
| Exec Maven Plugin (`org.codehaus.mojo:exec-maven-plugin`) | 3.1.0 | Enables `mvn exec:java` |

### 3.2 Database

* MySQL 8.0+
* Database name: `fasal_db`
* Character set: `utf8mb4` / `utf8mb4_unicode_ci`
* 13 tables, foreign keys, indexes (see [§5](#5-database-schema))

### 3.3 Frontend

* Plain HTML5 + CSS3 + ES6+ JavaScript
* No bundler, no transpiler, no package.json
* Two CDN libraries used only by the Super Admin page:
  * Leaflet 1.9.4 — `https://unpkg.com/leaflet@1.9.4/dist/leaflet.{css,js}`
  * Chart.js (latest) — `https://cdn.jsdelivr.net/npm/chart.js`

### 3.4 High-level Architecture

```
┌──────────────────────────────────────────────┐
│  Browser                                     │
│  ┌────────────┐ ┌────────────┐ ┌───────────┐ │
│  │farmer.html │ │hub-admin   │ │super-admin│ │
│  │(mobile-1st)│ │(sidebar)   │ │(map+chart)│ │
│  └────────────┘ └────────────┘ └───────────┘ │
│  Shared: js/api.js, css/base.css             │
└──────────────────────────────────────────────┘
                      ↓ HTTP/JSON
                      ↓ Bearer token in Authorization header
┌──────────────────────────────────────────────┐
│  Spark Java backend  (port 4567)             │
│  ─────────────────────────────────────────── │
│  api/         AuthRoutes, FarmerRoutes,      │
│               HubRoutes, RoutingRoutes,      │
│               SuperAdminRoutes, SeedRoutes   │
│  services/    AuthService, FarmerService,    │
│               HubService, VehicleService,    │
│               SuperAdminService              │
│  algorithm/   QualityCalculator,             │
│               RoutingEngine (7 steps)        │
│  models/      12 POJOs                       │
│  db/          DatabaseConnection (JDBC)      │
│  Main.java    Entry point + CORS + static    │
│               file handler (catch-all GET)   │
└──────────────────────────────────────────────┘
                      ↓ JDBC
┌──────────────────────────────────────────────┐
│  MySQL  (port 3306)                          │
│  Database: fasal_db                          │
│  13 tables, FKs, indexes                     │
└──────────────────────────────────────────────┘
```

### 3.5 Same-Origin Note

Originally the README told users to "open `farmer.html` directly from disk". Modern Chrome/Edge block that (`file://` → `http://localhost` is a Private Network Access violation). The applied fix is that **Spark also serves the `frontend-web/` folder on port 4567**, so the HTML and the API share the same origin and the browser stops complaining. See [§19](#19-known-bugs--fixes-applied).

---

## 4. Project File Layout

```
FASAL/
├── start.sh                      ← Mac / Linux bootstrap script
├── start.bat                     ← Windows bootstrap script
├── README.md                     ← Original setup guide
├── PROJECT_CONTEXT.md            ← (this file)
├── GITHUB_ISSUES.md              ← Ready-to-paste GitHub issues
│
├── database/
│   ├── schema.sql                ← 13 tables, FKs, indexes
│   └── seed.sql                  ← Demo data (hubs, spokes, users, etc.)
│
├── backend/
│   ├── pom.xml                   ← Maven config
│   └── src/main/java/com/fasal/
│       ├── Main.java                          ← Entry point, port 4567,
│       │                                        CORS, static-file handler
│       ├── db/
│       │   └── DatabaseConnection.java        ← JDBC singleton
│       ├── models/                            ← Twelve POJOs
│       │   ├── Hub.java
│       │   ├── Spoke.java
│       │   ├── Farmer.java     (represents any user row)
│       │   ├── Produce.java    (one produce listing)
│       │   ├── ProduceType.java (master type with λ)
│       │   ├── Vehicle.java
│       │   ├── Inventory.java
│       │   ├── Demand.java
│       │   ├── Route.java
│       │   ├── RouteStop.java
│       │   ├── RouteCargo.java
│       │   └── RouteResult.java               ← Routing-engine output
│       ├── algorithm/
│       │   ├── QualityCalculator.java         ← Q(t) math helpers
│       │   └── RoutingEngine.java             ← 7-step routing logic
│       ├── services/                          ← Data access layer
│       │   ├── AuthService.java               ← SHA-256, UUID sessions
│       │   ├── FarmerService.java
│       │   ├── HubService.java
│       │   ├── VehicleService.java
│       │   └── SuperAdminService.java
│       └── api/                               ← Spark HTTP routes
│           ├── AuthRoutes.java
│           ├── FarmerRoutes.java
│           ├── HubRoutes.java
│           ├── RoutingRoutes.java
│           ├── SuperAdminRoutes.java
│           └── SeedRoutes.java                ← POST /api/seed/reset
│
└── frontend-web/
    ├── farmer.html               ← Farmer Portal (mobile-first wizard)
    ├── hub-admin.html            ← Hub Admin Panel (sidebar + tables + demo)
    ├── super-admin.html          ← National Console (Leaflet + Chart.js)
    ├── css/
    │   ├── base.css              ← Design tokens + utility classes
    │   ├── farmer.css
    │   ├── hub-admin.css
    │   └── super-admin.css
    └── js/
        ├── api.js                ← Shared fetch wrapper + auth storage
        ├── farmer.js
        ├── hub-admin.js
        └── super-admin.js
```

Total ≈ **45 files**, ≈ **3,500 lines of code** plus ≈ **1,200 lines of comments**.

---

## 5. Database Schema

Defined in `database/schema.sql`. Drops and recreates everything on each run.

### 5.1 Tables

```
hubs                 6 hubs (Delhi, Mumbai, Nagpur, Chennai, Kolkata, Ahmedabad)
hub_distances        full 6×6 driving-distance matrix (30 ordered pairs)
spokes               18 spokes (3 per hub)
users                10 users (1 super admin + 6 hub admins + 3 farmers)
sessions             active login tokens (token → user_id)
produce_types        10 produce types with λ values
produce_listings     farmer-created listings (id, farmer, qty, harvest_date, hub)
inventory            current stock at each hub
demand               required produce at each hub (with min_quality_threshold)
vehicles             12 trucks (2 per hub, 1000 kg, status IDLE|IN_TRANSIT)
routes               planned/active/completed routes
route_stops          ordered stops along a route
route_cargo          cargo carried on a route (produce, qty, source→dest)
```

### 5.2 Column-by-column schema

```sql
-- 6 hubs
CREATE TABLE hubs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  city VARCHAR(100) NOT NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL
);

-- Full driving-distance matrix between every ordered pair of hubs
CREATE TABLE hub_distances (
  hub_id_from INT NOT NULL,
  hub_id_to   INT NOT NULL,
  distance_km DOUBLE NOT NULL,
  travel_time_hours DOUBLE NOT NULL,
  PRIMARY KEY (hub_id_from, hub_id_to),
  FOREIGN KEY (hub_id_from) REFERENCES hubs(id),
  FOREIGN KEY (hub_id_to)   REFERENCES hubs(id)
);

-- Smaller villages/towns each owned by one parent hub
CREATE TABLE spokes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  hub_id INT NOT NULL,
  latitude DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL,
  FOREIGN KEY (hub_id) REFERENCES hubs(id),
  INDEX idx_spokes_hub (hub_id)
);

-- Every login-capable user
CREATE TABLE users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  phone VARCHAR(15) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,           -- SHA-256 hex
  role ENUM('FARMER','HUB_ADMIN','SUPER_ADMIN') NOT NULL,
  spoke_id INT NULL,                             -- only for FARMER
  hub_id   INT NULL,                             -- only for HUB_ADMIN
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (spoke_id) REFERENCES spokes(id),
  FOREIGN KEY (hub_id)   REFERENCES hubs(id),
  INDEX idx_users_phone (phone),
  INDEX idx_users_role  (role)
);

-- Bearer tokens map to a user
CREATE TABLE sessions (
  id VARCHAR(64) PRIMARY KEY,                    -- the token (UUID, no dashes)
  user_id INT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_sessions_user (user_id)
);

-- Master list of produce types with their decay rate
CREATE TABLE produce_types (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  lambda_value DOUBLE NOT NULL,                  -- the λ in Q(t) = e^(-λt)
  unit VARCHAR(20) DEFAULT 'kg'
);

-- A farmer-created listing
CREATE TABLE produce_listings (
  id INT AUTO_INCREMENT PRIMARY KEY,
  farmer_id INT NOT NULL,
  produce_type_id INT NOT NULL,
  quantity_kg DOUBLE NOT NULL,
  harvest_date DATE NOT NULL,
  hub_id INT NOT NULL,
  status ENUM('PENDING','IN_TRANSIT','DELIVERED') DEFAULT 'PENDING',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (farmer_id)       REFERENCES users(id),
  FOREIGN KEY (produce_type_id) REFERENCES produce_types(id),
  FOREIGN KEY (hub_id)          REFERENCES hubs(id),
  INDEX idx_listings_farmer (farmer_id),
  INDEX idx_listings_hub    (hub_id)
);

-- Aggregated stock at each hub
CREATE TABLE inventory (
  id INT AUTO_INCREMENT PRIMARY KEY,
  hub_id INT NOT NULL,
  produce_type_id INT NOT NULL,
  quantity_kg DOUBLE NOT NULL,
  avg_harvest_date DATE NOT NULL,
  last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
              ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (hub_id)          REFERENCES hubs(id),
  FOREIGN KEY (produce_type_id) REFERENCES produce_types(id)
);

-- What a hub wants to receive
CREATE TABLE demand (
  id INT AUTO_INCREMENT PRIMARY KEY,
  hub_id INT NOT NULL,
  produce_type_id INT NOT NULL,
  required_quantity_kg DOUBLE NOT NULL,
  min_quality_threshold DOUBLE NOT NULL,         -- 0.0 – 1.0
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (hub_id)          REFERENCES hubs(id),
  FOREIGN KEY (produce_type_id) REFERENCES produce_types(id)
);

-- The truck fleet
CREATE TABLE vehicles (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  capacity_kg DOUBLE NOT NULL,
  current_hub_id INT NOT NULL,
  status ENUM('IDLE','IN_TRANSIT') DEFAULT 'IDLE',
  FOREIGN KEY (current_hub_id) REFERENCES hubs(id)
);

-- One planned/active/completed delivery
CREATE TABLE routes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  vehicle_id INT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  status ENUM('PLANNED','ACTIVE','COMPLETED') DEFAULT 'PLANNED',
  requires_cold_storage BOOLEAN DEFAULT FALSE,
  FOREIGN KEY (vehicle_id) REFERENCES vehicles(id),
  INDEX idx_routes_status (status)
);

-- Ordered stops on a route
CREATE TABLE route_stops (
  id INT AUTO_INCREMENT PRIMARY KEY,
  route_id INT NOT NULL,
  hub_id INT NOT NULL,
  stop_order INT NOT NULL,                       -- 1 = source hub
  arrived_at TIMESTAMP NULL,
  FOREIGN KEY (route_id) REFERENCES routes(id),
  FOREIGN KEY (hub_id)   REFERENCES hubs(id)
);

-- The cargo carried by a route
CREATE TABLE route_cargo (
  id INT AUTO_INCREMENT PRIMARY KEY,
  route_id INT NOT NULL,
  produce_type_id INT NOT NULL,
  quantity_kg DOUBLE NOT NULL,
  source_hub_id INT NOT NULL,
  destination_hub_id INT NOT NULL,
  FOREIGN KEY (route_id)           REFERENCES routes(id),
  FOREIGN KEY (produce_type_id)    REFERENCES produce_types(id),
  FOREIGN KEY (source_hub_id)      REFERENCES hubs(id),
  FOREIGN KEY (destination_hub_id) REFERENCES hubs(id)
);
```

### 5.3 Foreign-Key Graph (Drop Order)

`schema.sql` drops in this order so DDL succeeds even if tables already exist:

```
route_cargo → route_stops → routes → vehicles → demand → inventory
→ produce_listings → sessions → users → spokes → produce_types
→ hub_distances → hubs
```

---

## 6. Seed Data

`database/seed.sql` populates the demo. Highlights below — full data is in the file.

### 6.1 Hubs (6)

| ID | Name | City | Lat | Lng | Rationale |
|---|---|---|---|---|---|
| 1 | Delhi Hub | Delhi | 28.6139 | 77.2090 | National capital, huge consumer market |
| 2 | Mumbai Hub | Mumbai | 19.0760 | 72.8777 | Western financial centre, busiest port |
| 3 | Nagpur Hub | Nagpur | 21.1458 | 79.0882 | Central India, "Orange City", citrus surplus |
| 4 | Chennai Hub | Chennai | 13.0827 | 80.2707 | Southern port, rice-consuming region |
| 5 | Kolkata Hub | Kolkata | 22.5726 | 88.3639 | Eastern port, dense population |
| 6 | Ahmedabad Hub | Ahmedabad | 23.0225 | 72.5714 | Strong dairy cooperatives, grain |

### 6.2 Hub Distance Matrix (30 ordered pairs)

Selected km / hours examples (full matrix in seed.sql):

| From | To | km | hours |
|---|---|---|---|
| Delhi | Mumbai | 1400 | 24 |
| Delhi | Nagpur | 1050 | 17 |
| Mumbai | Ahmedabad | 530 | 9 |
| Nagpur | Chennai | 1170 | 19 |
| Kolkata | Ahmedabad | 2150 | 35 |

(And each pair has the reverse direction with identical values.)

### 6.3 Spokes (18, three per hub)

| Hub | Spokes |
|---|---|
| Delhi (1) | Sonipat, Faridabad, Ghaziabad |
| Mumbai (2) | Thane, Panvel, Bhiwandi |
| Nagpur (3) | Kamptee, Hingna, Saoner |
| Chennai (4) | Tambaram, Avadi, Sriperumbudur |
| Kolkata (5) | Howrah, Barasat, Salt Lake |
| Ahmedabad (6) | Sanand, Bavla, Mehmedabad |

Spoke IDs run 1..18 in that order.

### 6.4 Produce Types

See §2.2. IDs 1..10 in order: milk, spinach, banana, tomato, mango, apple, potato, onion, wheat, rice. Milk uses unit `liters`; everything else uses `kg`.

### 6.5 Users (10)

* 1 super admin (id 1)
* 6 hub admins (ids 2–7), one per hub
* 3 farmers (ids 8–10), at spokes 1, 7, 16 (i.e. spread across three hub regions)

Passwords are stored as SHA-256 hex digests, generated in SQL with `SHA2('admin123', 256)` etc. The Java `AuthService.hashPassword(...)` uses `MessageDigest.getInstance("SHA-256")` to produce the **same** hex format for verification.

### 6.6 Inventory (22 rows)

Designed so the routing demo has interesting surpluses on first run.

| Hub | Surplus items (illustrative) |
|---|---|
| Delhi (1) | 500 kg tomato, 400 kg onion, 600 kg wheat, 300 kg potato |
| Mumbai (2) | 350 kg banana, 800 kg rice, 100 kg onion, 80 kg spinach |
| Nagpur (3) | 600 kg mango, 400 kg wheat, 100 kg tomato, 200 kg apple |
| Chennai (4) | 500 kg rice, 50 kg spinach, 150 kg banana |
| Kolkata (5) | 700 kg potato, 600 kg rice, 80 kg spinach, 50 kg milk |
| Ahmedabad (6) | 800 kg wheat, 100 kg milk, 250 kg onion |

Harvest dates are seeded with `DATE_SUB(CURDATE(), INTERVAL N DAY)` so Q(t) is meaningful no matter when the demo runs.

### 6.7 Demand (14 rows)

Designed to create cross-hub matches. Examples:

* Chennai needs tomato (400 kg, min Q 0.50) — Delhi has surplus.
* Chennai needs mango (350 kg, min Q 0.40) — Nagpur has surplus.
* Mumbai needs tomato (300 kg, min Q 0.40) — Delhi has surplus.
* Kolkata needs milk (80 kg, min Q 0.70) — Ahmedabad has fresh milk.

### 6.8 Vehicles (12)

Two per hub, all 1000 kg capacity, all IDLE:
`Delhi-Truck-1`, `Delhi-Truck-2`, `Mumbai-Truck-1`, ..., `Ahmedabad-Truck-2`.

---

## 7. The Routing Algorithm

Implemented in `backend/src/main/java/com/fasal/algorithm/RoutingEngine.java`. The public entry point is `RouteResult runRouting(int sourceHubId)`. Work is split into **seven** clearly-named private steps. Below is the full chronological flow.

### Step 1 — `calculateSurplus(sourceHubId)`

* SQL #1: `SELECT i.produce_type_id, i.quantity_kg, i.avg_harvest_date, pt.name, pt.lambda_value FROM inventory i JOIN produce_types pt ... WHERE i.hub_id = ?`
* SQL #2: `SELECT produce_type_id, SUM(required_quantity_kg) FROM demand WHERE hub_id = ? GROUP BY produce_type_id` — local demand for the same hub.
* For each produce type: `surplus = inventory_qty - local_demand`. Keep only positives.
* Returns a `List<Surplus>` where Surplus is an inner class `{ produceTypeId, produceName, quantityKg, avgHarvestDate, lambdaValue }`.
* If empty → returns early with `RouteResult` whose `humanReadableSummary = "This hub has no surplus produce to ship."`.

### Step 2 — `findMatchingDemands(surpluses, sourceHubId)`

* SQL: joins `demand`, `produce_types`, `hubs`, and `hub_distances` to fetch every demand at OTHER hubs together with the travel time from source.
* For each demand row:
  1. Skip if source has no surplus of that produce type.
  2. Compute `projectedQ = QualityCalculator.calculateQualityAtArrival(λ, avgHarvestDate, travelHours)`.
  3. Skip if `projectedQ < min_quality_threshold` — produce would arrive too spoiled.
* Returns `List<MatchedDemand>` with `{ demandId, destinationHubId, destinationHubName, produceTypeId, produceName, requiredQuantityKg, minQualityThreshold, travelTimeHoursFromSource, distanceKmFromSource, projectedQualityFromSource, avgHarvestDate, lambdaValue }`.
* If empty → returns early with `humanReadableSummary = "No matching demand was found for the available surplus."`.

### Step 3 — `prioritiseDemands(matchedDemands)`

* Sort matched demands by `lambdaValue` **descending** — most perishable first. This is the priority-queue idea, implemented as a one-shot sort.

### Step 4 — `buildRoute(...)`

* Pick an IDLE vehicle at source via `pickIdleVehicle(sourceHubId)`. If none → return empty result with reason "No idle vehicle is currently available at this hub."
* Initialise:
  * `remainingSurplus` map: produceTypeId → remaining kg available.
  * `remainingCapacityKg = vehicle.capacityKg`.
  * `currentHubId = sourceHubId`.
  * `cumulativeHours = 0`.
  * `outStops = [ { hubId: sourceHubId, stopOrder: 1 } ]` and `outQualityAtStop[1] = 1.0`.
* While `pending` demands exist AND `remainingCapacityKg > 0`:
  1. **Nearest-neighbour pick**: `findNearestPendingDemand(currentHubId, pending)` looks up `hub_distances.travel_time_hours` for each pending demand and picks the one with the shortest hop.
  2. `available = remainingSurplus[produceTypeId]`. If 0, drop this demand and continue.
  3. `assignedKg = min(demand.requiredQuantityKg, available, remainingCapacityKg)`.
  4. Compute leg travel time `lookupTravelHours(currentHubId, destination)`. Add to `cumulativeHours`.
  5. Compute `qualityAtArrival = QualityCalculator.calculateQualityAtArrival(λ, avgHarvest, cumulativeHours)`.
  6. Append a new `RouteStop` (with `stopOrder` incremented), append a new `RouteCargo` (`source_hub_id = sourceHubId`, `destination_hub_id = nearest.destinationHubId`, `quantity_kg = assignedKg`).
  7. Subtract `assignedKg` from both `remainingSurplus[produceTypeId]` and `remainingCapacityKg`.
  8. Move `currentHubId` to the chosen destination. Drop the served demand from `pending`.

### Step 5 — `evaluateColdStorage(stops, cargo, prioritisedDemands, sourceHubId, reasonOut)`

* Recompute `cumulativeHours` per stop_order using `hub_distances` (we don't trust the running variable from step 4 because rounding could differ).
* For each cargo item: project Q at its destination's travel-time-so-far. If `Q < min_quality_threshold` for that demand, set `coldNeeded = true` and append a sentence to the reason string like `"tomato would arrive at Q=0.38, below minimum Q=0.50."`.

### Step 6 — `persistRoute(vehicle, stops, cargo, requiresColdStorage)`

Wrapped in a manual transaction (`conn.setAutoCommit(false); ... conn.commit();`):

1. `INSERT INTO routes (vehicle_id, status, requires_cold_storage) VALUES (?, 'PLANNED', ?)` — captures generated key.
2. `INSERT INTO route_stops (route_id, hub_id, stop_order)` for each stop, batched.
3. `INSERT INTO route_cargo (route_id, produce_type_id, quantity_kg, source_hub_id, destination_hub_id)` for each cargo item, batched.
4. `UPDATE vehicles SET status='IN_TRANSIT', current_hub_id=<last stop>` for the chosen vehicle.

### Step 7 — `buildResult(...)`

* Fill `hubName` on each RouteStop via a one-shot `IN (...)` SELECT.
* Construct a `RouteResult` POJO with: `routeId`, `vehicleName`, `vehicleId`, full `stops` list, full `cargo` list, `requiresColdStorage`, `qualityAtEachStop` map, `coldStorageReason`, and a generated human-readable summary like:

> `Vehicle Delhi-Truck-1 will travel Delhi Hub → Chennai Hub delivering 400kg tomato. Cold storage required: NO.`

### 7.1 Important Algorithmic Properties

* **Greedy, not optimal.** Nearest-neighbour can be far from the truly shortest tour; documented explicitly in code comments and the UI ("This is not perfect but it is fast and practical.").
* **Capacity-constrained.** Single truck, single trip — once capacity is full the rest of the demands are dropped this run.
* **Priority by λ.** Most perishable served first, mitigating the freshness risk of running out of capacity for spinach/milk.
* **Idempotent in the sense that:** every call to `runRouting(sourceHubId)` creates a brand-new route row and consumes one IDLE truck. Calling repeatedly will eventually leave no IDLE trucks at that hub (returns `"No idle vehicle available"` after that).

### 7.2 Helper — `QualityCalculator`

All freshness math lives in `algorithm/QualityCalculator.java`:

```java
public static double calculateQuality(double lambda, LocalDate harvestDate)
public static double calculateQualityAtArrival(double lambda, LocalDate harvestDate, double travelTimeHours)
public static double daysUntilThreshold(double lambda, LocalDate harvestDate, double threshold)
public static boolean needsColdStorage(double lambda, LocalDate harvestDate, double travelTimeHours, double minQualityThreshold)
```

Internal constants:

```java
private static final double HOURS_PER_DAY = 24.0;
private static final double INFINITE_DAYS = Double.POSITIVE_INFINITY;
public  static final double MAX_QUALITY   = 1.0;
public  static final double MIN_QUALITY   = 0.0;
```

`daysBetween(from, to)` is clamped to `>= 0` so future harvest dates are treated as "today".

---

## 8. Backend Java Code

Java package: `com.fasal`. Java target 11. All classes are deliberately commented in plain English (every method, including getters/setters in POJOs) — this is a stated design goal of the project.

### 8.1 `Main.java`

**Responsibilities**: verify DB → resolve frontend folder → configure Spark → register routes → register catch-all static-file handler → print banner.

Key constants:

```java
private static final int    SERVER_PORT          = 4567;
private static final int    EXIT_CODE_DB_FAILURE = 1;
private static final String CORS_ALLOWED_ORIGIN  = "*";
private static final String CORS_ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS";
private static final String CORS_ALLOWED_HEADERS = "Content-Type, Authorization";
private static File frontendRoot = null;   // resolved once at startup
```

Boot sequence:

```java
public static void main(String[] args) {
    verifyDatabaseOrExit();
    resolveFrontendRoot();       // looks at ../frontend-web, frontend-web, ./frontend-web
    configureServer();           // port + CORS before-filter + OPTIONS handler
    registerRoutes();            // all /api/* routes
    registerStaticFileHandler(); // catch-all GET, MUST run last
    // banner with URLs
}
```

The catch-all static-file handler is the post-fix design (see [§19](#19-known-bugs--fixes-applied)). It:

* Redirects `GET /` to `/farmer.html`.
* For `GET /*`:
  * If path is null or starts with `/api/` → 404 (so unknown API routes don't leak HTML).
  * Resolves to a `File` under `frontendRoot`.
  * Path-traversal guard via `getCanonicalPath().startsWith(rootCanonical)` → 403 on escape.
  * 404 if not found or it's a directory.
  * Sets MIME type via `guessMimeType(...)` (handles html, css, js, json, svg, png, jpg, ico, woff, woff2).
  * Streams the file body to `response.raw().getOutputStream()` in 8 KB chunks. Returns `null` so Spark knows we already wrote the body.

### 8.2 `db/DatabaseConnection.java`

A static utility class with three swappable constants and `Connection getConnection()`. Currently in the demo:

```java
private static final String DB_URL      = "jdbc:mysql://localhost:3306/fasal_db"
                                        + "?useSSL=false"
                                        + "&allowPublicKeyRetrieval=true"
                                        + "&serverTimezone=UTC"
                                        + "&characterEncoding=UTF-8";
private static final String DB_USER     = "root";
private static final String DB_PASSWORD = "root";  // overwritten by start.sh / start.bat
private static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";

static { Class.forName(JDBC_DRIVER); }  // best-effort

public static Connection getConnection() throws SQLException { ... }
```

The setup script rewrites the two credential constants via `sed` (Unix) or PowerShell `-replace` (Windows). See [§16](#16-start-scripts) for the substitution detail.

### 8.3 `models/` — 12 POJOs

All POJOs follow the same pattern: private fields, no-arg constructor, public getters/setters, every method has a brief plain-English comment. None of them use frameworks or annotations.

| Class | Mirrors table | Notable joined-in fields |
|---|---|---|
| `Hub` | hubs | — |
| `Spoke` | spokes | — |
| `Farmer` | users (any role; not just farmers) | — |
| `Produce` | produce_listings | `produceName`, `lambdaValue`, `currentQuality` (computed) |
| `ProduceType` | produce_types | — |
| `Vehicle` | vehicles | — |
| `Inventory` | inventory | `produceName`, `lambdaValue`, `currentQuality` |
| `Demand` | demand | `produceName`, `lambdaValue` |
| `Route` | routes | `vehicleName`; carries nested `List<RouteStop>` and `List<RouteCargo>` |
| `RouteStop` | route_stops | `hubName` |
| `RouteCargo` | route_cargo | `produceName`, `sourceHubName`, `destinationHubName` |
| `RouteResult` | (not a table) | Full output of `RoutingEngine.runRouting`. Fields: `routeId`, `vehicleId`, `vehicleName`, `stops`, `cargo`, `requiresColdStorage`, `qualityAtEachStop` (Map<Integer,Double>), `humanReadableSummary`, `coldStorageReason`. |

### 8.4 `algorithm/`

Already detailed in [§7](#7-the-routing-algorithm).

### 8.5 `services/`

Static utility classes (no DI, no spring); each one wraps a slice of DB access.

* `AuthService` — `register`, `login`, `validateToken`, `hashPassword` (lowercase hex SHA-256). Uses a UUID-without-dashes string as the session token. Creates a row in `sessions` on every successful login/registration.
* `FarmerService` — `createListing`, `getListingsByFarmer`, `getAllListingsAtHub`, `getAllProduceTypes`, `getAllSpokes`. Live Q(t) is computed in Java, not SQL.
* `HubService` — `getInventory`, `getDemand`, `getSurplus` (computed in-memory from inventory minus local demand), `getVehicles`, `getRoutes`, and a shared `loadRoutesByIds(...)` helper (`static`, package-private) used by SuperAdminService too.
* `VehicleService` — `getAll`, `getByHub`, `updateStatus`, `updateCurrentHub`.
* `SuperAdminService` — `getAllHubs`, `getAllSpokes`, `getAllVehicles`, `getAllRoutes`, `getOverviewStats`. The overview returns `{ total_listings, active_routes, idle_vehicles, hubs_count, total_users, total_vehicles }`.

All services throw `SQLException` upward; the route layer catches and converts to JSON errors.

### 8.6 `api/`

Each routes class registers HTTP handlers in a `public static void register()` method called from `Main.registerRoutes()`.

* `AuthRoutes` — `POST /api/auth/register`, `POST /api/auth/login`. Plain text body parse via Gson `JsonParser`.
* `FarmerRoutes` — `POST /api/farmer/listings`, `GET /api/farmer/listings`, `GET /api/farmer/produce-types` (public), `GET /api/spokes` (public; used by farmer registration form).
* `HubRoutes` — `GET /api/hub/:hubId/{inventory,demand,surplus,vehicles,routes}`. All require authentication; do not check role.
* `RoutingRoutes` — `POST /api/routing/run`, `GET /api/routing/routes`.
* `SuperAdminRoutes` — `GET /api/admin/{hubs,spokes,vehicles,routes,overview}`. All require authentication; do not enforce role (the **frontend** enforces it, not the backend).
* `SeedRoutes` — `POST /api/seed/reset`. Truncates all data tables (with `SET FOREIGN_KEY_CHECKS = 0` wrapper) and reseeds in Java, using `AuthService.hashPassword(...)` for the same SHA-256 hashes the SQL seed uses. Inventory dates are computed from `LocalDate.now()` so they always reflect "now".

Each routes file has its own static `error(message)` helper that returns `Map.of("error", message)` and reused HTTP status codes:

```java
private static final int HTTP_UNAUTHORIZED = 401;
private static final int HTTP_BAD_REQUEST  = 400;
private static final int HTTP_SERVER_ERROR = 500;
```

Authentication helper pattern (repeated in HubRoutes, RoutingRoutes, SuperAdminRoutes):

```java
private static boolean isAuthenticated(Request req, Response res) {
    String header = req.headers("Authorization");
    if (header == null) { res.status(HTTP_UNAUTHORIZED); return false; }
    String token = header.startsWith("Bearer ") ? header.substring(7) : header;
    if (AuthService.validateToken(token) == null) {
        res.status(HTTP_UNAUTHORIZED);
        return false;
    }
    return true;
}
```

---

## 9. HTTP API Reference

All endpoints live under `http://localhost:4567`. JSON in, JSON out. Authenticated routes expect `Authorization: Bearer <token>`. CORS is open (`Access-Control-Allow-Origin: *`).

### 9.1 Auth

| Method | Path | Auth | Body | Response |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | Public | `{ name, phone, password, role?, spoke_id?, hub_id? }` (role defaults to FARMER) | `{ token, user_id, role, hub_id, spoke_id, name }` |
| `POST` | `/api/auth/login` | Public | `{ phone, password }` | `{ token, user_id, role, hub_id, spoke_id, name }` |
| `GET`  | `/api/spokes` | Public | — | `[ { id, name, hubId, latitude, longitude } ]` |

### 9.2 Farmer

| Method | Path | Auth | Body / Notes | Response |
|---|---|---|---|---|
| `GET` | `/api/farmer/produce-types` | Public | — | `[ { id, name, lambdaValue, unit } ]` |
| `POST` | `/api/farmer/listings` | Bearer | `{ produce_type_id, quantity_kg, harvest_date }` (harvest_date in ISO yyyy-MM-dd) | `{ listing_id, message }` |
| `GET` | `/api/farmer/listings` | Bearer | The current farmer's listings | `[ Produce ]` with live `currentQuality` |

### 9.3 Hub

All require any authenticated user. The `:hubId` path parameter is an int.

| Method | Path | Returns |
|---|---|---|
| `GET` | `/api/hub/:hubId/inventory` | `[ Inventory ]` with live `currentQuality` |
| `GET` | `/api/hub/:hubId/demand` | `[ Demand ]` |
| `GET` | `/api/hub/:hubId/surplus` | `[ { produce_type_id, produce_type, inventory_qty, demand_qty, surplus_qty } ]` — snake_case keys |
| `GET` | `/api/hub/:hubId/vehicles` | `[ Vehicle ]` |
| `GET` | `/api/hub/:hubId/routes` | `[ Route ]` with stops + cargo nested |

### 9.4 Routing

| Method | Path | Body | Returns |
|---|---|---|---|
| `POST` | `/api/routing/run` | `{ hub_id }` | `RouteResult` (see [§7](#7-the-routing-algorithm) Step 7) |
| `GET` | `/api/routing/routes` | — | `[ Route ]` for status `PLANNED` or `ACTIVE`, with stops + cargo |

### 9.5 Super Admin

| Method | Path | Returns |
|---|---|---|
| `GET` | `/api/admin/hubs` | `[ Hub ]` |
| `GET` | `/api/admin/spokes` | `[ Spoke ]` |
| `GET` | `/api/admin/vehicles` | `[ Vehicle ]` |
| `GET` | `/api/admin/routes` | `[ Route ]` with stops + cargo |
| `GET` | `/api/admin/overview` | `{ total_listings, active_routes, idle_vehicles, hubs_count, total_users, total_vehicles }` |

### 9.6 Maintenance

| Method | Path | Effect |
|---|---|---|
| `POST` | `/api/seed/reset` | Truncates every data table (sessions wiped too) and reseeds. Returns `{ message: "Database reset and reseeded successfully" }`. |

### 9.7 Static (post-fix)

| Method | Path | Effect |
|---|---|---|
| `GET` | `/` | Redirects to `/farmer.html` |
| `GET` | `/farmer.html`, `/hub-admin.html`, `/super-admin.html`, `/css/*`, `/js/*`, etc. | Streams the file from `frontend-web/` on disk |

### 9.8 Error shape

All error responses are JSON: `{ "error": "<message>" }`. Common statuses: 400 (bad input / wrong creds), 401 (no/invalid token), 500 (uncaught server-side error).

---

## 10. Authentication & Sessions

* **Hashing**: SHA-256 of the plain password, lowercase hex (`String.format("%02x", b)`). Implemented in `AuthService.hashPassword(...)`. Matches `SHA2('plain', 256)` from MySQL.
* **Token**: `UUID.randomUUID().toString().replace("-", "")` — 32-char hex, no dashes.
* **Storage**: `sessions(id PRIMARY KEY, user_id, created_at)`.
* **Validation**: `AuthService.validateToken(token)` joins sessions to users and returns `{ user_id, role, hub_id, spoke_id, name }` or `null`.
* **Lifetime**: No expiry implemented. Tokens are valid forever until the sessions row is deleted (e.g. on schema rebuild).
* **Logout (frontend-only)**: `localStorage.removeItem('fasal_auth')` plus reload — there is **no** `/api/auth/logout` endpoint that deletes the session row server-side.
* **Bearer prefix**: All authenticated frontend calls send `Authorization: Bearer <token>`. The backend tolerates a missing `Bearer ` prefix (`header.substring(7)` only if startsWith).
* **Roles**: stored on the user row. Three values: `FARMER`, `HUB_ADMIN`, `SUPER_ADMIN`. Constants in `AuthService.ROLE_FARMER` etc.
* **Where roles are enforced**: in the **frontend** (`farmer.js`, `hub-admin.js`, `super-admin.js` each check `getAuth().role === 'X'` on init). The backend only checks "is a valid token present" — it does **not** restrict which role can hit `/api/admin/*` for example. A hub admin can call `/api/admin/hubs` and it will succeed.

---

## 11. Frontend — Shared

### 11.1 `js/api.js`

The single source of truth for backend communication. Loaded first on every page.

```javascript
const API_BASE = 'http://localhost:4567';
const AUTH_STORAGE_KEY = 'fasal_auth';

async function apiFetch(path, options)   // wrapper around fetch with auth + JSON
async function apiGet(path)
async function apiPost(path, body)

function saveAuth(token, userId, role, hubId, spokeId, name)
function getToken()
function getAuth()       // returns { token, userId, role, hubId, spokeId, name } | null
function clearAuth()
function isLoggedIn()
```

`apiFetch` behaviour:

* Adds `Content-Type: application/json` and `Authorization: Bearer <token>` (if token saved).
* Treats network failure as `Error('Network error - is the backend running? (...)')` with `.status = 0`.
* Reads the body as text once, tries `JSON.parse`, falls back to raw string.
* On non-2xx → throws an `Error` with `.message`, `.status`, and `.body` attached.
* Returns the parsed body otherwise.

### 11.2 `css/base.css`

Design tokens shared by all three frontends:

```css
:root {
  --color-primary:        #4CAF50;
  --color-primary-dark:   #388E3C;
  --color-primary-light:  #C8E6C9;
  --color-warning:        #FF9800;
  --color-danger:         #F44336;
  --color-text:           #212121;
  --color-text-secondary: #757575;
  --color-surface:        #FFFFFF;
  --color-background:     #F5F5F5;
  --color-border:         #E0E0E0;
  --radius-card:          12px;
  --shadow-card:          0 2px 8px rgba(0,0,0,0.10);
  --font-size-base:       16px;
  --font-size-large:      20px;
  --font-size-small:      13px;
}
```

Shared utility classes: `.card`, `.btn`, `.btn-primary`, `.btn-secondary`, `.btn-full`, `.badge`, `.badge-{green|yellow|red|grey}`, `.quality-bar` + `.quality-fill.{green|yellow|red}`, `.toast` + `.toast.{success|error}`, `.spinner`, text helpers (`.text-secondary`, `.text-small`, `.text-large`, `.text-center`), margin helpers (`.mt-1..4`, `.mb-1..4`).

All form `input/select/textarea` are styled to a minimum height of **48px** for finger-friendly tap targets.

---

## 12. Frontend — Farmer Portal

`frontend-web/farmer.html` + `farmer.css` + `farmer.js`. Mobile-first, designed for 390 px width, max-width 480 px container on desktop.

### 12.1 Top-level Screens (only one visible at a time)

* `#screen-onboarding` — 5-step wizard, shown when logged out.
* `#screen-login` — for existing users to sign in instead of registering.
* `#screen-main` — app shell once logged in.

### 12.2 The 5-Step Onboarding Wizard

| Step | Heading | Behaviour |
|---|---|---|
| 1 | Welcome (FASAL logo, "Your Market, Direct", "आपका बाज़ार, सीधा") | `Get Started` → step 2 |
| 2 | Name + phone | Validates `name.length >= 2` and `^\d{10}$` for phone |
| 3 | Password + confirm | Validates `length >= 6` and `password === confirm` |
| 4 | Spoke dropdown | Populated from `GET /api/spokes`; cached in `spokesCache` |
| 5 | Success / loading / error | Automatically POSTs `/api/auth/register` on entry; shows tick + "Welcome to FASAL, {name}!" on success; saves token via `saveAuth(...)` |

Progress bar across the top + numbered step indicators (1..5) with `.active` and `.done` styling. Step changes use a CSS `stepFadeIn` keyframe animation.

### 12.3 Main App (post-login)

Four tabs in a fixed bottom nav (mobile) / centred 480 px container (desktop):

| Tab | Endpoint(s) | Notes |
|---|---|---|
| Home / Dashboard | `GET /api/farmer/listings` (every 30 s) | 3 summary tiles (Listings, Total kg, Avg Q(t)); listings sorted by `currentQuality` **ascending** (most at-risk first) |
| Mandi | `GET /api/farmer/produce-types` (cached), `POST /api/farmer/listings`, `GET /api/farmer/listings` for the "browse" block | Form: produce type, quantity, harvest date (default = today; `max = today`) |
| Contracts | — | "Coming soon" placeholder |
| Messages | — | "Coming soon" placeholder |

### 12.4 Logout

Top-right "⏻" icon button → `clearInterval(dashboardInterval)`, `clearAuth()`, `showOnboardingScreen()`.

### 12.5 Defensive Coding Patterns

* All HTML insertion goes through an `esc(...)` helper that escapes `&<>"'`.
* `formatDate(input)` is lenient — handles ISO strings, numbers (epoch ms), and Java `{ year, month, day }` shapes (a guard for serialisation edge cases).
* `qualityClass(q)` returns `green | yellow | red`.

---

## 13. Frontend — Hub Admin Panel

`frontend-web/hub-admin.html` + `hub-admin.css` + `hub-admin.js`. Sidebar on desktop (≥768 px), bottom nav on mobile.

### 13.1 Auth Gate

```javascript
const auth = getAuth();
if (!isLoggedIn() || !auth || auth.role !== 'HUB_ADMIN') {
    showLogin();
} else {
    showMainApp();
}
```

Login uses `POST /api/auth/login`. The frontend rejects any returned role !== `HUB_ADMIN` with **"Access denied - Hub Admins only."**

### 13.2 Sections

Six sections, switched via `data-section` attribute on sidebar / bottom-nav buttons:

| Section | What it does |
|---|---|
| Overview | 4 stat cards: Inventory Items, Active Demands, Idle Vehicles, Routes Today |
| Inventory | Table sorted by Q(t) ascending; auto-refreshes every 60 s |
| Demand | Table of demand rows with min-quality badges |
| Surplus | Table with positive surplus rows highlighted green, shortages red |
| Vehicles | Cards; in-transit vehicles get a `View Route` button that expands inline |
| Run Algorithm | The headline demo (see §13.3) |

### 13.3 Run Algorithm — 6 Animated Step Cards

When the user clicks **Run Algorithm Now**, the page:

1. Caches `GET /api/farmer/produce-types` (for λ lookups) and `GET /api/admin/hubs` (for haversine distance).
2. Fetches `GET /api/hub/{hubId}/surplus` as the pre-run surplus snapshot.
3. POSTs `/api/routing/run` with `{ hub_id }`.
4. Reveals six cards with 400 ms stagger:

| # | Title | Source data |
|---|---|---|
| 1 | 📦 Surplus Found | Pre-run surplus snapshot, filtered to positives |
| 2 | 🤝 Demands Matched | `result.cargo`, grouped by destination hub, with `qualityAtEachStop` mapped via the stop_order |
| 3 | ⏱️ Priority Order | `result.cargo` sorted by λ desc; shows days-to-Q-0.5 ≈ `ln(2) / λ` |
| 4 | 🗺️ Route Planned | `result.stops`; per-leg distance/time approximated via **haversine** from `hubsCache` (formula: `2·R·atan2(√a, √(1−a))`, R=6371 km, then `hours ≈ km / 60`) |
| 5 | ❄️ Cold Storage Check | `result.requiresColdStorage` + `result.coldStorageReason` |
| 6 | ✅ Route Saved | `result.routeId`, `result.vehicleName`, status `PLANNED`, `result.humanReadableSummary` |

Each card uses a CSS `revealCard` keyframe (opacity 0 + translateY → 1 + 0).

### 13.4 View Route (in-transit vehicles)

For each `IN_TRANSIT` vehicle on the Vehicles section, an inline panel can expand showing route ID, the stop path joined with `→`, the cargo list, and a `❄️` indicator if `requiresColdStorage`.

---

## 14. Frontend — Super Admin

`frontend-web/super-admin.html` + `super-admin.css` + `super-admin.js`. The most visually rich of the three.

### 14.1 Layout

```
┌──────────────────────────────────────────────┐
│ Sticky stats bar:                            │
│  🌾 FASAL Console  [Listings][Hubs]          │
│                    [In Transit][Active]  ⏻   │
├──────────────────────────────────────────────┤
│ Toggle row: Hubs | Spokes | Vehicles |       │
│             Routes | (Clear All)             │
├──────────────────────────────────────────────┤
│ Leaflet Map (60vh, India centred)            │
├──────────────────────────────────────────────┤
│ Panel row (1 col mobile, 2 cols ≥1024px):    │
│  Freshness Decay Chart | Algorithm Demo      │
└──────────────────────────────────────────────┘
```

### 14.2 Leaflet Map Layers

| Layer | Visualisation | Data |
|---|---|---|
| Hubs | Big green `circleMarker` (radius 14) | `GET /api/admin/hubs` |
| Spokes | Small grey `circleMarker` (radius 5) | `GET /api/admin/spokes` |
| Vehicles | Custom `divIcon` with 🚛 emoji, offset around hub coords in a small circle | `GET /api/admin/vehicles` |
| Routes | Polyline; ACTIVE = solid blue `#2196F3`, PLANNED = dashed orange `#FF9800` | `GET /api/admin/routes` |

Hub popups load lazily on `popupopen`: `GET /api/hub/:hubId/{surplus,demand}` in parallel, render top-3 surplus + top-3 demand + a "⚡ Run Algorithm Here" button that sets the demo dropdown and triggers `runDemoAlgorithm()`.

Vehicle tooltips are `sticky: true` and include cargo lists when status is `IN_TRANSIT`.

### 14.3 Freshness Decay Chart (Chart.js)

Produce dropdown defaults to **tomato** if present. The chart shows three lines for days 0..30:

| Line | Formula | Style |
|---|---|---|
| Normal Storage | `e^(-λt)` | Solid green |
| Cold Storage (30% rate) | `e^(-λ · 0.3 · t)` | Solid blue |
| Typical min threshold | constant `0.5` | Dashed red |

Y axis fixed `[0, 1]`. Title shows `<produce> — λ = <value> per day`.

### 14.4 Step-Through Algorithm Demo

Source-hub dropdown + `▶ Run Algorithm for Selected Hub` button. Flow:

1. Map flies to the source hub (`map.flyTo([lat,lng], 6, { duration: 1.0 })`).
2. A pulsing green "active vehicle" `divIcon` is dropped on the source.
3. For each consecutive pair of stops:
   * Draws an orange polyline segment (`#FF6F00`, weight 4).
   * Animates the active-vehicle marker along the segment over 1500 ms via `requestAnimationFrame`.
   * Opens a popup at the destination showing stop order, cargo, projected Q at arrival, cold-storage flag.
   * Appends a side-panel "demo step card" mirroring the popup.
   * Pauses 1500 ms before the next leg.
4. Ends with a "🎉 Route Complete" popup at the final hub and a summary card in the side panel.
5. Triggers `loadRoutes()` 600 ms later so the saved route appears as a normal layer line.

The pulsing icon uses a CSS `vehiclePulse` keyframe animation (1.5 s, box-shadow + scale).

### 14.5 Toggle Buttons

Each toggle in the row mirrors a flag in a `toggleState` object. Click handler flips the flag and either rebuilds that layer group or removes it from the map. `Clear All` removes every layer including the demo overlay and unchecks every pill.

---

## 15. Build System

### 15.1 `backend/pom.xml`

* `<modelVersion>` 4.0.0, `<groupId>` `com.fasal`, `<artifactId>` `fasal-backend`, `<version>` 1.0.0, `<packaging>` jar.
* Java source / target 11. UTF-8 source encoding.
* Dependencies (versions in [§3.1](#31-backend)).
* Plugins:
  * **maven-shade-plugin** (3.4.1) — bound to `package` phase. Creates `target/fasal-backend.jar` with all deps. Manifest `Main-Class` = `com.fasal.Main`. `createDependencyReducedPom=false`.
  * **exec-maven-plugin** (3.1.0) — pre-configured with `<mainClass>com.fasal.Main</mainClass>` so `mvn exec:java` works without flags.

### 15.2 Build Commands

```bash
# Compile only (fast, used by start.sh)
cd backend
mvn -q compile

# Run via Maven exec plugin (what start.sh does at the end)
mvn -q exec:java -Dexec.mainClass="com.fasal.Main"

# Alternative: build the fat JAR and run it
mvn -q package -DskipTests
java -jar target/fasal-backend.jar
```

The fat JAR is fully self-contained (Spark, Gson, MySQL driver, SLF4J).

---

## 16. Start Scripts

### 16.1 `start.sh` (Mac / Linux)

```
1. cd to the script's directory
2. Verify mysql, java, mvn are on PATH (exit 1 if not)
3. Prompt for MySQL username (default root) and password (silent)
4. export MYSQL_PWD="$DB_PASS"     # avoid password-in-process-listing
5. CREATE DATABASE IF NOT EXISTS fasal_db
6. mysql ... < database/schema.sql
7. mysql ... < database/seed.sql
8. unset MYSQL_PWD
9. Rewrite DB_USER and DB_PASSWORD constants in DatabaseConnection.java via sed
   - GNU sed vs BSD sed detected at runtime
10. cd backend; mvn -q compile
11. Print banner + demo accounts
12. mvn -q exec:java -Dexec.mainClass="com.fasal.Main"   # foreground
```

### 16.2 `start.bat` (Windows)

Same flow in Windows batch, with these specifics:

* `where mysql/java/mvn` to verify presence.
* `set /p DB_USER="..."` and `set /p DB_PASS="..."` (password is visible — Windows batch can't silence stdin easily).
* `set MYSQL_PWD=...` env var, cleared at the end.
* Substitution into `DatabaseConnection.java` via PowerShell:
  ```bat
  powershell -NoProfile -Command "(Get-Content '%DB_CONN_FILE%') -replace 'private static final String DB_USER = \".*\";', 'private static final String DB_USER = \"%DB_USER%\";' | Set-Content '%DB_CONN_FILE%'"
  ```
* `call mvn -q compile` / `call mvn -q exec:java ...` (must use `call` because `mvn` is itself a batch file).

### 16.3 Known Brittleness

If the MySQL password contains any of `"`, `\`, `$`, `&`, `|`, `/`, the regex substitution produces invalid Java. Documented in the README troubleshooting; a clean fix (using env vars instead of source rewriting) is proposed in `GITHUB_ISSUES.md` Issue #4.

---

## 17. How to Run

### 17.1 Prerequisites

| Tool | Min version | Verify |
|---|---|---|
| Java | 11+ | `java -version` |
| Maven | 3.6+ | `mvn -version` |
| MySQL | 8.0+ | `mysql --version` |

### 17.2 Cold-start Sequence

```bash
# 1. Get the code
git clone https://github.com/computerofaplane/fasal
cd fasal   # or however your local copy is named (e.g. FASAL/)

# 2. (Mac/Linux)
chmod +x start.sh
./start.sh
# Enter MySQL username (default root) and password when prompted.

# 2. (Windows)
start.bat
```

### 17.3 After Startup

The console should print:

```
Database connection OK.
Frontend root resolved to: C:\...\FASAL\frontend-web
FASAL backend running on http://localhost:4567
Open in your browser:
  http://localhost:4567/farmer.html
  http://localhost:4567/hub-admin.html
  http://localhost:4567/super-admin.html
```

Then open any of those URLs and sign in with one of the demo accounts ([§18](#18-demo-accounts)).

### 17.4 Resetting the DB

Three options, all equivalent:

```bash
# A. cURL
curl -X POST http://localhost:4567/api/seed/reset

# B. Browser console while signed in
fetch('http://localhost:4567/api/seed/reset', { method: 'POST' }).then(r=>r.json()).then(console.log)

# C. Re-run seed.sql by hand
mysql -u root -p fasal_db < database/seed.sql
```

`/api/seed/reset` also wipes the sessions table → every saved token becomes invalid. After running it, do `localStorage.clear()` in your browser and log in again, or apply the fix proposed in `GITHUB_ISSUES.md` Issue #3.

---

## 18. Demo Accounts

All passwords are intentionally trivial for demo use. **Never deploy with these.**

| Role | Phone | Password | Notes |
|---|---|---|---|
| SUPER_ADMIN | 9000000000 | admin123 | National console |
| HUB_ADMIN | 9000000001 | hub123 | Delhi Hub |
| HUB_ADMIN | 9000000002 | hub123 | Mumbai Hub |
| HUB_ADMIN | 9000000003 | hub123 | Nagpur Hub |
| HUB_ADMIN | 9000000004 | hub123 | Chennai Hub |
| HUB_ADMIN | 9000000005 | hub123 | Kolkata Hub |
| HUB_ADMIN | 9000000006 | hub123 | Ahmedabad Hub |
| FARMER | 9100000001 | farmer123 | Ravi Kumar — Sonipat (Delhi region) |
| FARMER | 9100000002 | farmer123 | Sunita Devi — Kamptee (Nagpur region) |
| FARMER | 9100000003 | farmer123 | Mohan Singh — Sanand (Ahmedabad region) |

---

## 19. Known Bugs & Fixes Applied

This section captures every real issue surfaced while running the demo, plus the fixes either applied or proposed.

### 19.1 🐛 file:// origin blocks browser → backend fetch (BLOCKING, applied workaround)

**Symptom**: User double-clicks `frontend-web/super-admin.html` (or any of the three) → page opens via `file://` → login form appears → click Sign In → red error: **"Network error - is the backend running? (Failed to fetch)"**. Backend log shows no incoming request.

**Root cause**: Chrome / Edge enforce [Private Network Access](https://chromestatus.com/feature/5436853517811712). A page on a `file://` origin cannot `fetch()` `http://localhost:*` — the browser blocks the request *before it leaves the browser*. The backend's `Access-Control-Allow-Origin: *` is irrelevant because the request never arrives.

**Verification** (PowerShell call directly to backend succeeds):

```powershell
> Invoke-WebRequest -Method POST -Uri "http://localhost:4567/api/auth/login" `
    -ContentType "application/json" `
    -Body '{"phone":"9000000000","password":"admin123"}'
StatusCode: 200
Body: {"role":"SUPER_ADMIN","user_id":1,"name":"FASAL Admin","token":"…"}
```

**Fix applied**: Spark backend now serves the `frontend-web/` folder too, so the HTML page and the API share the same origin `http://localhost:4567`. Browser stops complaining. See §19.2 for the implementation detail.

### 19.2 🐛 `Spark.staticFiles.externalLocation()` returned 400 Bad Request (root cause of the static-files workaround)

**Symptom**: First fix attempt for §19.1 was the obvious Spark idiom:

```java
staticFiles.externalLocation(new File("../frontend-web").getAbsolutePath());
```

After restart, `GET /farmer.html` returned `HTTP/1.1 400 Bad Request` with body `Bad request` directly from Jetty. The API still worked (`/api/admin/hubs` returned 401 correctly), proving the new JVM was running. The static-file lookup itself silently failed.

**Root cause**: Unclear — appears to be an environment-specific interaction between Spark 2.9.4, Jetty 9.4.48, JDK 17, Windows path handling, and the `..` in the relative path. Reproducible on the test machine; not exhaustively debugged.

**Fix applied**: Drop `staticFiles.externalLocation()` entirely. Replace with an explicit `get("/*", ...)` catch-all that reads files off disk and streams them. Same behaviour, no Spark static-file plumbing involved.

**Patch — `backend/src/main/java/com/fasal/Main.java`** (the core delta; full file is in the repo):

```java
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import static spark.Spark.get;

private static File frontendRoot = null;

public static void main(String[] args) {
    verifyDatabaseOrExit();
    resolveFrontendRoot();
    configureServer();
    registerRoutes();
    registerStaticFileHandler();   // MUST be after registerRoutes() so /api/* matches first
    System.out.println("FASAL backend running on http://localhost:" + SERVER_PORT);
    if (frontendRoot != null) {
        System.out.println("Open in your browser:");
        System.out.println("  http://localhost:" + SERVER_PORT + "/farmer.html");
        System.out.println("  http://localhost:" + SERVER_PORT + "/hub-admin.html");
        System.out.println("  http://localhost:" + SERVER_PORT + "/super-admin.html");
    }
}

private static void resolveFrontendRoot() {
    String[] candidates = { "../frontend-web", "frontend-web", "./frontend-web" };
    for (String c : candidates) {
        File dir = new File(c);
        if (dir.exists() && dir.isDirectory()) {
            try { frontendRoot = dir.getCanonicalFile(); }
            catch (IOException e) { frontendRoot = dir.getAbsoluteFile(); }
            System.out.println("Frontend root resolved to: " + frontendRoot.getAbsolutePath());
            return;
        }
    }
    System.err.println("WARN: frontend-web folder not found.");
}

private static void registerStaticFileHandler() {
    if (frontendRoot == null) return;

    get("/", (req, res) -> { res.redirect("/farmer.html"); return ""; });

    get("/*", (req, res) -> {
        String path = req.pathInfo();
        if (path == null || path.startsWith("/api/")) { res.status(404); return "Not found"; }

        File requested = new File(frontendRoot, path);
        String reqCanon, rootCanon;
        try { reqCanon = requested.getCanonicalPath(); rootCanon = frontendRoot.getCanonicalPath(); }
        catch (IOException e) { res.status(404); return "Not found"; }
        if (!reqCanon.startsWith(rootCanon)) { res.status(403); return "Forbidden"; }

        if (!requested.exists() || requested.isDirectory()) { res.status(404); return "Not found"; }

        res.type(guessMimeType(path));
        res.status(200);
        try (InputStream in = new FileInputStream(requested);
             OutputStream out = res.raw().getOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        }
        return null;
    });
}

private static String guessMimeType(String path) {
    String p = path.toLowerCase();
    if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html; charset=UTF-8";
    if (p.endsWith(".css"))  return "text/css; charset=UTF-8";
    if (p.endsWith(".js"))   return "application/javascript; charset=UTF-8";
    if (p.endsWith(".json")) return "application/json; charset=UTF-8";
    if (p.endsWith(".svg"))  return "image/svg+xml";
    if (p.endsWith(".png"))  return "image/png";
    if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
    if (p.endsWith(".ico"))  return "image/x-icon";
    return "application/octet-stream";
}
```

**Properties of this fix**:
* Zero new dependencies.
* `/api/*` routes are registered first, so Spark matches them before the catch-all. The catch-all also explicitly returns 404 for `/api/*` as a safety net.
* Path-traversal-safe (`getCanonicalPath().startsWith(rootCanonical)` enforcement).
* Streams via 8 KB chunks → safe for large assets.
* Sets correct `Content-Type` headers per extension.

### 19.3 🐛 Stale `localStorage` token leaves users stuck on a half-broken dashboard (NOT YET FIXED)

**Symptom**: After a successful login, the token is saved to `localStorage.fasal_auth`. If that token later becomes invalid (e.g. `/api/seed/reset` truncated `sessions`, or the demo was restarted with schema.sql), `isLoggedIn()` still returns true → the SPA goes to the dashboard → every authenticated fetch returns 401 → user sees error toasts with no obvious way to log out.

**Workaround**: Open DevTools and run `localStorage.clear(); location.reload();` — the page returns to the login form.

**Proposed fix** (queued in `GITHUB_ISSUES.md` Issue #3):
1. In `js/api.js`, on `response.status === 401`, call `clearAuth()` before throwing.
2. In each page's `initApp` / first authenticated fetch, treat a thrown `e.status === 401` as a signal to call `showOnboardingScreen()` / `showLogin()`.

### 19.4 🐛 Start scripts substitute MySQL passwords with naïve regex (NOT YET FIXED)

**Symptom**: If the user's MySQL password contains any of `"`, `\`, `$`, `&`, `|`, `/`, the `sed` (Unix) or PowerShell `-replace` (Windows) substitution produces broken Java in `DatabaseConnection.java`, and `mvn compile` fails with string-literal errors.

**Proposed fix** (queued as `GITHUB_ISSUES.md` Issue #4): Stop writing into Java source entirely. Read `FASAL_DB_USER` and `FASAL_DB_PASSWORD` from env vars at runtime:

```java
private static final String DB_USER =
    System.getenv().getOrDefault("FASAL_DB_USER", "root");
private static final String DB_PASSWORD =
    System.getenv().getOrDefault("FASAL_DB_PASSWORD", "");
```

Then `start.sh` just `export`s and `start.bat` just `set`s — no regex pipeline, any password is safe.

### 19.5 ⚠️ Java date serialisation across the wire

Gson does not have built-in support for `LocalDate` in Java 17+ (`InaccessibleObjectException` on reflective field access). The default fallback may emit `LocalDate` as `{ "year": ..., "month": ..., "day": ... }` or fail entirely depending on the JDK.

The frontend `formatDate(...)` helper is **defensive** about this — it accepts:
* an ISO-string (`"2026-05-12"`),
* a number (epoch ms),
* an object with a `year` field,
and falls back to `String(input)` if it cannot parse anything.

This works in practice for the demo on JDK 11 / 17. A proper fix would register a Gson `TypeAdapter` for `LocalDate` in the route handlers.

---

## 20. Coding Conventions

The project enforces a single very clear style for educational purposes:

### 20.1 Java

* **Plain-English comments on every class and every method**, written as if explaining to a non-programmer. This includes getters/setters in POJOs (`// Returns the database ID of this user.` etc.).
* **No magic numbers.** Constants are named (`SERVER_PORT`, `HTTP_BAD_REQUEST`, `HOURS_PER_DAY`, `COLD_STORAGE_FACTOR`).
* **No frameworks except Spark + Gson + JDBC.** No Spring, no Hibernate, no Lombok. POJOs are written by hand.
* **Static utility services.** All services are `final` classes with `private` constructors and `public static` methods. No DI container.
* **Service layer throws `SQLException` upward.** The route layer catches and returns JSON `{ "error": "..." }`.
* **All SQL via `PreparedStatement`** — never string concatenation.
* **Try-with-resources** for `Connection`, `PreparedStatement`, `ResultSet`.

### 20.2 JavaScript

* **No bundler, no transpiler.** ES6+ syntax that runs natively in modern browsers.
* **No inline `onclick=` attributes in HTML.** Every handler is wired via `addEventListener` in the page's `attachListeners()` / `attachEventListeners()` function.
* **`function () {}` over arrow functions for top-level functions**, arrow for short callbacks. Mirrors the existing codebase's pragmatic mix.
* **One source of truth for API access** — every fetch goes through `apiFetch()` in `api.js`.
* **`esc(...)` helper** is mandatory anywhere user/DB content is interpolated into `innerHTML`.
* **Every function has a one-line comment** above it (mirrors the Java convention).

### 20.3 CSS

* **Mobile-first.** Default rules tuned for ≈ 390 px width; `@media (min-width: 768px)` and `(min-width: 1024px)` widen the layout.
* **Design tokens via CSS custom properties** in `base.css` — never hex codes inline.
* **Utility classes** for common spacing/text (`.mt-3`, `.mb-4`, `.text-secondary`, etc.) instead of bespoke per-page styles.
* **Border-radius 12 px+** for cards. Soft shadows. Earthy green palette.

---

## 21. GitHub Issues

Three (optionally four) ready-to-file issues are in `GITHUB_ISSUES.md`:

1. **🐛 Bug** — Login fails with "Failed to fetch" when frontend pages are opened via `file://`.
2. **✨ Enhancement** — Serve `frontend-web/` from the Spark backend (same-origin); includes the full Main.java patch from §19.2.
3. **🐛 Bug** — Stale `localStorage` token leaves users stuck on the dashboard.
4. **🐛 (Optional) Bug** — `start.sh` / `start.bat` corrupt `DatabaseConnection.java` on passwords with special characters.

Each issue has a copy-ready Title and Body. Recommended order: file #1 first, then #2 (link back to #1's number in the Motivation section), then #3, then #4.

---

## 22. Future Work & Limitations

Things the demo deliberately does not implement, listed so anyone reading this knows what to expect:

* **Multi-trip planning.** Routing is single-truck, single-trip. Real-world fleet scheduling needs multi-vehicle assignment, return trips, and a TSP-style optimisation rather than greedy nearest-neighbour.
* **Real-time vehicle tracking.** Vehicles flip between IDLE and IN_TRANSIT but never actually move along a route after `runRouting` returns. There's no "advance to next stop" endpoint and `arrived_at` is never written.
* **Contracts & messaging.** Tabs exist as "Coming soon" placeholders in the farmer portal.
* **Role-based access control on the backend.** All `/api/admin/*` endpoints only check "is the user logged in", not "is the user a SUPER_ADMIN". The frontend enforces roles; the backend trusts the frontend.
* **Session expiry.** Tokens are valid forever (until `sessions` is wiped or schema is rebuilt). No refresh tokens, no rotation.
* **Cookie-based auth.** Currently uses `Authorization: Bearer` headers with `localStorage` storage — fine for a demo, vulnerable to XSS in production.
* **Real freshness sensors.** Q(t) is a model, not a measurement. A production system would pull from IoT temperature loggers and adjust λ in real time.
* **Cold-chain pricing.** The cold-storage flag is binary, not metered. No way to express "needs 30% reduced decay rate for the last 6 hours".
* **Realtime UI updates.** Polling intervals (30 s for farmer dashboard, 60 s for hub inventory) are used instead of WebSockets / SSE.
* **Logout server-side.** There is no `/api/auth/logout` to delete the sessions row; logout is purely a frontend `localStorage.removeItem` + redirect.
* **HTTPS.** Backend runs HTTP only on `localhost:4567`. For LAN/production, terminate TLS at a reverse proxy.

---

## Appendix A — Quick Reference Cheatsheet

### A.1 URLs

| URL | Purpose |
|---|---|
| http://localhost:4567/ | Redirects to `/farmer.html` |
| http://localhost:4567/farmer.html | Farmer Portal |
| http://localhost:4567/hub-admin.html | Hub Admin Panel |
| http://localhost:4567/super-admin.html | National Operations Console |
| http://localhost:4567/api/auth/login | POST login endpoint |
| http://localhost:4567/api/seed/reset | POST DB reset |

### A.2 Demo Credentials Quick-list

```
super admin   9000000000   admin123
delhi admin   9000000001   hub123
mumbai admin  9000000002   hub123
nagpur admin  9000000003   hub123
chennai admin 9000000004   hub123
kolkata admin 9000000005   hub123
ahmd admin    9000000006   hub123
farmer Ravi   9100000001   farmer123
farmer Sunita 9100000002   farmer123
farmer Mohan  9100000003   farmer123
```

### A.3 Common Maintenance Commands

```bash
# Reset everything to seed state
curl -X POST http://localhost:4567/api/seed/reset

# Clear browser-side auth (paste in DevTools Console)
localStorage.clear(); location.reload();

# Recompile and restart backend
cd backend
mvn clean compile
mvn -q exec:java -Dexec.mainClass="com.fasal.Main"

# Direct DB access
mysql -u root -p fasal_db
```

### A.4 Probe the Backend Directly (PowerShell)

```powershell
# Login as super admin
Invoke-WebRequest -Method POST `
  -Uri "http://localhost:4567/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"phone":"9000000000","password":"admin123"}'

# Run routing for Delhi (hub_id 1) — requires a Bearer token in headers
$token = "<paste token from login>"
Invoke-WebRequest -Method POST `
  -Uri "http://localhost:4567/api/routing/run" `
  -ContentType "application/json" `
  -Headers @{ "Authorization" = "Bearer $token" } `
  -Body '{"hub_id":1}'

# Fetch all hubs
Invoke-WebRequest -Method GET `
  -Uri "http://localhost:4567/api/admin/hubs" `
  -Headers @{ "Authorization" = "Bearer $token" }
```

### A.5 The 7 Algorithm Steps in 7 Sentences

1. **Surplus** — find what this hub has more of than it locally needs.
2. **Match** — find other hubs that want it AND where it'll still arrive fresh enough.
3. **Prioritise** — sort matches with the highest-λ produce first.
4. **Build route** — greedy nearest-neighbour, capacity-constrained.
5. **Check cold** — would any cargo arrive below the buyer's minimum Q? If yes, flag it.
6. **Persist** — write `routes`, `route_stops`, `route_cargo` in one transaction; mark the truck IN_TRANSIT.
7. **Return** — a `RouteResult` POJO with the stop sequence, cargo, flags, quality-at-each-stop map, and a human sentence summary.

---

## Appendix B — Glossary

| Term | Meaning |
|---|---|
| Hub | Big city centre (warehouse + dispatch). Six in the demo. |
| Spoke | Smaller village/town funnelling produce to its parent hub. 18 in the demo. |
| Q(t) | Freshness function; e^(-λt). 0.0 = rotten, 1.0 = just picked. |
| λ (lambda) | Per-produce-type decay rate per day. |
| PNA | Private Network Access — Chrome/Edge browser policy that blocks `file://` → `localhost`. |
| Mandi | (Hindi) Marketplace. The "Mandi" tab in the farmer portal shows local listings. |
| `fasal_auth` | localStorage key under which the frontend stores the auth bundle. |
| Greedy nearest-neighbour | Routing heuristic — always go to the closest remaining stop. Fast, not optimal. |
| Cold storage | Refrigerated transport. Modelled as a binary flag in the DB; modelled as 30% decay rate in the chart. |
| Sessions table | Maps Bearer tokens to user IDs. Truncated by `schema.sql` (which is what `start.sh` re-runs). |

---

*End of PROJECT_CONTEXT.md — drop this file into any conversation with an LLM and it has the full picture.*
