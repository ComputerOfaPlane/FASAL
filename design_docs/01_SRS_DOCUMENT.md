# Software Requirements Specification (SRS) — FASAL

**Project:** FASAL — Fast And Scalable Agricultural Logic
**Version:** 1.0
**Status:** Demo / Educational

---

## 1. Introduction

### 1.1 Purpose

This document describes the software requirements for **FASAL**, a three-tier agricultural-logistics platform that connects farmers, hub administrators, and a national operations administrator around a freshness-aware routing algorithm.

It is intended for:
* Product owners verifying feature completeness.
* Backend developers implementing the API and domain logic.
* Frontend developers building the three user-facing portals.
* QA engineers writing test cases.
* DevOps engineers planning deployment.

### 1.2 Scope

FASAL is a self-contained demo system that:
* Lets **farmers** list freshly harvested produce against their nearest village (spoke).
* Lets **hub administrators** see local inventory, demand, surplus, and dispatch trucks via a routing algorithm that takes freshness decay into account.
* Lets a **super administrator** view a national map of hubs, spokes, vehicles, and routes, and visualise/step-through the routing algorithm.

The platform models freshness as an exponential decay function `Q(t) = e^(-λt)` and flags routes that need refrigerated (cold-storage) transport.

**Out of scope** for this version: multi-vehicle co-ordination, real-time GPS tracking, contracts, messaging, billing, refresh tokens, OAuth, mobile apps.

### 1.3 Definitions, Acronyms, Abbreviations

| Term | Meaning |
|---|---|
| Hub | A large city centre serving as a warehouse and dispatch point. |
| Spoke | A smaller village/town whose produce funnels into one parent hub. |
| Q(t) | Freshness as a function of time. Q(t) = e^(-λ · t). |
| λ (lambda) | Per-day decay rate of a specific produce type. |
| Cold Storage | Refrigerated transport. Modelled as a binary flag in the DB; 30% decay rate in the freshness chart. |
| Surplus | `inventory_qty - local_demand_qty` for one produce type at one hub. |
| Mandi | Hindi for "marketplace"; the name of the produce-listing tab in the farmer portal. |
| SRS | Software Requirements Specification (this document). |
| SHA-256 | Cryptographic hash function used for passwords. |
| JDBC | Java Database Connectivity — Java's standard DB API. |
| CORS | Cross-Origin Resource Sharing — browser security mechanism. |
| PNA | Private Network Access — Chrome/Edge policy blocking `file://` → `localhost`. |

### 1.4 References

* `README.md` — setup and run guide.
* `PROJECT_CONTEXT.md` — comprehensive project context.
* `GITHUB_ISSUES.md` — known issues queued for upstream.
* Mermaid diagrams: <https://mermaid.js.org/>
* Spark Java: <http://sparkjava.com/>

### 1.5 Overview

* Section 2 paints the big picture (product perspective, functions, users, constraints).
* Section 3 lists every functional requirement, numbered FR-XX.
* Section 4 lists non-functional requirements, numbered NFR-XX.
* Section 5 covers external interfaces (HTTP API, DB, browser).
* Section 6 catalogues data requirements.

---

## 2. Overall Description

### 2.1 Product Perspective

FASAL is a stand-alone product, not part of a larger ecosystem. It is deliberately built without third-party PaaS dependencies — the entire stack runs on a developer laptop with **Java + Maven + MySQL**, no Docker, no npm.

```
┌──────────────────────────────────────────┐
│  Browser (Chrome / Edge / Firefox)        │
└──────────────────────────────────────────┘
                ↕ HTTP + JSON
┌──────────────────────────────────────────┐
│  Spark Java backend (port 4567)          │
│  — Serves both REST API and HTML/CSS/JS  │
└──────────────────────────────────────────┘
                ↕ JDBC
┌──────────────────────────────────────────┐
│  MySQL (port 3306)                       │
└──────────────────────────────────────────┘
```

### 2.2 Product Functions (Headlines)

* **Farmer registration** via a 5-step wizard (name → phone → password → spoke → done).
* **Produce listing creation** with quantity + harvest date.
* **Live freshness tracking** (Q(t) computed on every read).
* **Hub admin dashboard** — inventory, demand, surplus tables; vehicle list.
* **Run Routing Algorithm** button — 7-step engine returning a planned route.
* **Super admin map view** — nationwide hubs, spokes, vehicles, routes overlay.
* **Decay-curve visualiser** — Chart.js graph of Q(t) with cold-storage simulation.
* **Step-through algorithm demo** — animated live demo of route execution on the map.
* **Demo data reset endpoint** — `POST /api/seed/reset` rebuilds the demo state.

### 2.3 User Characteristics

| User class | Tech literacy | Devices | Expected sessions/day |
|---|---|---|---|
| Farmer | Low; mobile-first | Smartphone (Android) | 1–3 |
| Hub Admin | Medium; basic dashboards | Desktop + tablet | 5–10 |
| Super Admin | High; data-heavy views | Desktop | 1–2 |

### 2.4 Constraints

* **Tech stack is fixed**: Java 11+, Maven, Spark Java 2.9.x, Gson, MySQL 8.0+, vanilla HTML/CSS/JS. No additional runtimes.
* **Single-process backend**: no clustering, no message bus, no caching layer.
* **Single-truck routing**: each run plans one route for one IDLE vehicle from one source hub.
* **Bearer token in `Authorization` header**; tokens stored in `localStorage`.
* **No HTTPS** in the local demo. TLS must be added by a reverse proxy in production.
* **No server-side role enforcement** beyond "is logged in"; UI enforces role separation.

### 2.5 Assumptions and Dependencies

* MySQL is running on `localhost:3306` and accepts the credentials in `DatabaseConnection.java`.
* The browser has internet access (Leaflet + Chart.js load from CDNs; this can be eliminated by vendoring those libraries if offline-first is required).
* Date/time at the server reflects the user's locale and is consistent across restarts so that `Q(t)` calculations remain accurate.

---

## 3. Functional Requirements

### 3.1 Authentication & Authorisation

| ID | Requirement |
|---|---|
| FR-AUTH-01 | A new user shall be able to register as `FARMER` by providing name, phone (10 digits), password (≥ 6 chars), and a spoke ID. |
| FR-AUTH-02 | The system shall reject registration if the phone number is already in use, returning HTTP 400. |
| FR-AUTH-03 | The system shall store passwords as SHA-256 hex digests (never plaintext). |
| FR-AUTH-04 | An existing user shall log in with phone + password and receive a Bearer token. |
| FR-AUTH-05 | The system shall persist a session row mapping the token to the user ID. |
| FR-AUTH-06 | Every authenticated request shall carry `Authorization: Bearer <token>`. |
| FR-AUTH-07 | Tokens shall not expire unless their session row is deleted (e.g. by `schema.sql` rebuild or `/api/seed/reset`). |
| FR-AUTH-08 | The frontend shall enforce role separation: Farmer Portal accepts only `FARMER`, Hub Admin Panel only `HUB_ADMIN`, National Console only `SUPER_ADMIN`. |

### 3.2 Farmer Portal

| ID | Requirement |
|---|---|
| FR-FARMER-01 | A farmer shall see a 5-step onboarding wizard if not logged in. |
| FR-FARMER-02 | Step 2 shall validate that name length ≥ 2 and phone matches `^\d{10}$`. |
| FR-FARMER-03 | Step 3 shall validate that password length ≥ 6 and confirmation matches. |
| FR-FARMER-04 | Step 4 shall populate a spoke dropdown from `GET /api/spokes`. |
| FR-FARMER-05 | On reaching step 5, the system shall automatically POST `/api/auth/register` and surface success or error. |
| FR-FARMER-06 | After login, the farmer shall see a Home tab with 3 summary tiles: total listings, total kg, average Q(t). |
| FR-FARMER-07 | Listings on Home shall be sorted by `currentQuality` ascending (most at-risk first). |
| FR-FARMER-08 | Home shall auto-refresh listings every 30 seconds. |
| FR-FARMER-09 | The Mandi tab shall let a farmer create a new listing (produce type + quantity + harvest date). |
| FR-FARMER-10 | Harvest date shall default to today and shall not be allowed in the future. |
| FR-FARMER-11 | The Mandi tab shall display all current listings at the farmer's hub. |
| FR-FARMER-12 | Quality bars shall show green for Q ≥ 0.7, yellow for 0.4 ≤ Q < 0.7, red for Q < 0.4. |
| FR-FARMER-13 | Contracts and Messages tabs shall display a "Coming Soon" placeholder. |
| FR-FARMER-14 | The farmer shall be able to log out via the ⏻ icon in the header. |

### 3.3 Hub Admin Panel

| ID | Requirement |
|---|---|
| FR-HUB-01 | A hub admin shall sign in with phone + password; non-HUB_ADMIN roles are rejected with "Access denied". |
| FR-HUB-02 | The panel shall display six sections: Overview, Inventory, Demand, Surplus, Vehicles, Run Algorithm. |
| FR-HUB-03 | Overview shall show four stat cards: Inventory Items, Active Demands, Idle Vehicles, Routes Today. |
| FR-HUB-04 | Inventory shall be sorted by Q(t) ascending and auto-refresh every 60 seconds. |
| FR-HUB-05 | Surplus shall highlight positive-surplus rows green and negative-surplus rows red. |
| FR-HUB-06 | Vehicles shall be shown as cards; in-transit vehicles shall expose a "View Route" button. |
| FR-HUB-07 | "Run Algorithm" shall POST `/api/routing/run` and reveal 6 animated step cards in sequence with ~400 ms stagger. |
| FR-HUB-08 | Step cards shall correspond to: Surplus Found, Demands Matched, Priority Order, Route Planned, Cold Storage Check, Route Saved. |

### 3.4 Super Admin / National Console

| ID | Requirement |
|---|---|
| FR-ADMIN-01 | A super admin shall sign in with phone + password; non-SUPER_ADMIN roles are rejected. |
| FR-ADMIN-02 | A sticky stats bar shall show Total Listings, Hubs Online, In Transit, Active Routes. |
| FR-ADMIN-03 | A Leaflet map shall be centred on India (lat 20.5937, lng 78.9629, zoom 5). |
| FR-ADMIN-04 | Toggle pills shall add/remove Hub, Spoke, Vehicle, and Route layers. |
| FR-ADMIN-05 | Hub markers shall open a popup listing top-3 surplus + top-3 demand + a "Run Algorithm Here" button (lazy-loaded on popup open). |
| FR-ADMIN-06 | Vehicle markers shall expose a sticky tooltip with cargo + cold-storage flag for in-transit trucks. |
| FR-ADMIN-07 | Route polylines shall use blue solid for ACTIVE and orange dashed for PLANNED. |
| FR-ADMIN-08 | A Freshness Decay Curve chart shall plot two Q(t) curves (normal and cold-storage 30% rate) plus a dashed reference line at 0.5. |
| FR-ADMIN-09 | A Step-Through Algorithm Demo shall animate a routing run on the map with smooth marker interpolation between stops. |

### 3.5 Routing Algorithm

| ID | Requirement |
|---|---|
| FR-ALG-01 | The engine shall accept a `source_hub_id` and return a `RouteResult`. |
| FR-ALG-02 | The engine shall compute surplus = inventory_qty − local_demand_qty per produce type. |
| FR-ALG-03 | The engine shall match surpluses against demand at other hubs, filtering matches where projected Q at arrival < min_quality_threshold. |
| FR-ALG-04 | The engine shall prioritise matched demands by λ descending. |
| FR-ALG-05 | The engine shall use greedy nearest-neighbour to plan stops, limited by vehicle capacity. |
| FR-ALG-06 | The engine shall flag `requires_cold_storage = true` if any cargo arrives below its demand's `min_quality_threshold`, with a human-readable reason string. |
| FR-ALG-07 | The engine shall persist a `routes` row + `route_stops` rows + `route_cargo` rows in one transaction. |
| FR-ALG-08 | The engine shall mark the assigned vehicle `IN_TRANSIT` and update its `current_hub_id` to the final stop. |
| FR-ALG-09 | The engine shall return a `RouteResult` including a human-readable summary sentence. |

### 3.6 Maintenance

| ID | Requirement |
|---|---|
| FR-MAINT-01 | `POST /api/seed/reset` shall truncate every data table and reseed in Java, producing the same dataset as `seed.sql`. |

---

## 4. Non-Functional Requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-PERF-01 | Performance | Each authenticated GET shall complete in < 200 ms on a single-user local install. |
| NFR-PERF-02 | Performance | A full routing run shall complete in < 1 second for the seeded dataset. |
| NFR-USAB-01 | Usability | Farmer portal shall be mobile-first, optimised for 390 px width. |
| NFR-USAB-02 | Usability | All interactive elements shall have minimum height 48 px (finger-friendly). |
| NFR-USAB-03 | Usability | Quality bars and status badges shall use colour AND text so the UI works for colour-blind users. |
| NFR-SEC-01 | Security | Passwords shall never appear in logs or process listings. |
| NFR-SEC-02 | Security | All DB access shall use `PreparedStatement` — no string concatenation. |
| NFR-SEC-03 | Security | Path-traversal must be impossible against the static-file handler (`getCanonicalPath` containment check). |
| NFR-MAINT-01 | Maintainability | Every Java class and method shall carry a plain-English comment. |
| NFR-MAINT-02 | Maintainability | There shall be no magic numbers; all constants shall be named. |
| NFR-PORT-01 | Portability | The system shall run on Windows, macOS, and Linux without code changes. |
| NFR-INST-01 | Installability | A user with Java, Maven, and MySQL preinstalled shall reach a running demo within 5 minutes via `start.sh` / `start.bat`. |
| NFR-RELIAB-01 | Reliability | `start.sh` and `start.bat` shall be idempotent — repeated runs shall not corrupt the database. |
| NFR-OBSERV-01 | Observability | The backend shall log startup info: DB OK, frontend root path, server URL. |
| NFR-OBSERV-02 | Observability | All failed API requests shall log a one-line stderr message including the exception text. |

---

## 5. External Interface Requirements

### 5.1 User Interfaces

* **Farmer Portal** (`farmer.html`): mobile-first, 5-step wizard + 4-tab bottom navigation.
* **Hub Admin Panel** (`hub-admin.html`): desktop-first with sidebar nav, responsive bottom-nav on mobile.
* **Super Admin Console** (`super-admin.html`): desktop-first with Leaflet map and Chart.js panels.

### 5.2 Hardware Interfaces

None directly. The backend speaks TCP/IP on port 4567; the DB speaks TCP/IP on port 3306. No specialised hardware (e.g. IoT temperature loggers) is integrated in v1.

### 5.3 Software Interfaces

* **MySQL JDBC driver** (`com.mysql.cj.jdbc.Driver`).
* **Leaflet 1.9.4** via `unpkg.com` CDN.
* **Chart.js** via `cdn.jsdelivr.net`.

### 5.4 Communications Interfaces

* HTTP/1.1 between browser and backend.
* JSON request and response bodies.
* `Authorization: Bearer <token>` for authenticated calls.

---

## 6. Data Requirements

### 6.1 Core Entities

13 tables: `hubs`, `hub_distances`, `spokes`, `users`, `sessions`, `produce_types`, `produce_listings`, `inventory`, `demand`, `vehicles`, `routes`, `route_stops`, `route_cargo`.

(See `04_ER_DIAGRAM.md` for the full ER diagram and `PROJECT_CONTEXT.md §5` for the column-level DDL.)

### 6.2 Data Volumes (Demo Scale)

| Table | Row count |
|---|---|
| hubs | 6 |
| hub_distances | 30 |
| spokes | 18 |
| users | 10 |
| produce_types | 10 |
| inventory | 22 |
| demand | 14 |
| vehicles | 12 |
| sessions | 0 (grows with usage) |
| produce_listings | 0 initially |
| routes / route_stops / route_cargo | 0 initially |

### 6.3 Reference Data

* **Hubs**: Delhi, Mumbai, Nagpur, Chennai, Kolkata, Ahmedabad. With realistic lat/lng coordinates.
* **Produce types**: 10 items with λ values from 0.001 (rice) to 0.45 (milk).
* **Distances**: Approximate road km and travel-time-hours between every ordered hub pair.

### 6.4 Computed Data

* `currentQuality` on each Inventory / Produce row → computed in Java on every read using `QualityCalculator.calculateQuality(λ, harvest_date)`.
* `surplus_qty` per produce type → `inventory_qty − demand_qty`, computed in `HubService.getSurplus()`.

---

## 7. Acceptance Criteria (Smoke-Test Level)

| Step | Expected outcome |
|---|---|
| Run `./start.sh` on a clean machine with prerequisites installed | Backend banner appears within 30 s |
| Visit `http://localhost:4567/farmer.html` | Onboarding wizard renders, no JS console errors |
| Sign in as super admin via super-admin.html | National console loads with map, stats, dropdowns |
| Click "Run Algorithm" on the Hub Admin panel for Delhi | 6 step cards reveal sequentially; final card shows a saved route ID |
| Open Super Admin, run demo for Delhi | A truck animates along a polyline on the map |
| POST `/api/seed/reset` | Returns `{"message":"Database reset and reseeded successfully"}`; existing tokens become invalid |

---

## 8. Document Revision Notes

| Version | Date | Changes |
|---|---|---|
| 1.0 | 2026-05-23 | Initial SRS captured during a Windows 11 / Edge 148 debugging and packaging session. |
