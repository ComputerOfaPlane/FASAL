# FASAL — Fast And Scalable Agricultural Logic

**Setup and Run Guide**

FASAL is a self-contained agricultural-logistics demo: farmers list produce, hub admins manage stock and demand, and a routing engine plans cross-hub deliveries while tracking freshness decay using `Q(t) = e^(-λt)`.

Three pieces run locally — a Java backend, a MySQL database, and three plain HTML/CSS/JS pages.

---

## Prerequisites

You need exactly three things on your machine:

| Tool      | Minimum version | Check with        |
| --------- | --------------- | ----------------- |
| **Java**  | 11+             | `java -version`   |
| **Maven** | 3.6+            | `mvn -version`    |
| **MySQL** | 8.0+            | `mysql --version` |

> No npm, no Node.js, no Python, no Docker.
> No frontend build step — HTML/CSS/JS files are opened directly in your browser.

---

## Quick Start

A clean machine with the prerequisites installed should be running FASAL in under 5 minutes.

1. **Get the code.** Clone or download this repository to a folder of your choice.

2. **Open a terminal in the project root** — the `FASAL/` directory that contains `start.sh`, `database/`, `backend/`, and `frontend-web/`.

3. **Make sure MySQL is running and you know a username + password that can create a database.** The default `root` user works on most local installs. You can optionally create a dedicated user:

   ```sql
   mysql -u root -p
   CREATE USER 'fasal_user'@'localhost' IDENTIFIED BY 'fasal_password';
   GRANT ALL PRIVILEGES ON fasal_db.* TO 'fasal_user'@'localhost';
   FLUSH PRIVILEGES;
   EXIT;
   ```

4. **(Optional)** review the DB credentials in `backend/src/main/java/com/fasal/db/DatabaseConnection.java`. The startup script will overwrite these from your prompt inputs, but you can also edit them by hand:

   ```java
   private static final String DB_USER = "root";
   private static final String DB_PASSWORD = "";
   ```

5. **Run the startup script** from the project root.

   On **Mac / Linux**:

   ```bash
   chmod +x start.sh
   ./start.sh
   ```

   On **Windows**:

   ```bat
   start.bat
   ```

   The script will:
   - check that `mysql`, `java`, and `mvn` are on your PATH
   - prompt for your DB username and password
   - create `fasal_db`, load the schema, load the seed data
   - patch `DatabaseConnection.java` with your credentials
   - compile and start the backend on `http://localhost:4567`

6. **Open the frontend pages in your browser** (just double-click them — no web server needed):

   - `frontend-web/farmer.html` — Farmer Portal
   - `frontend-web/hub-admin.html` — Hub Admin Panel
   - `frontend-web/super-admin.html` — National Operations Console

7. **Sign in** with one of the demo accounts below and explore.

---

## Demo Accounts

All passwords are intentionally simple for demo use — **replace them before any real deployment.**

| Role          | Phone        | Password    | Notes                             |
| ------------- | ------------ | ----------- | --------------------------------- |
| SUPER_ADMIN   | 9000000000   | admin123    | National console                  |
| HUB_ADMIN     | 9000000001   | hub123      | Delhi Hub                         |
| HUB_ADMIN     | 9000000002   | hub123      | Mumbai Hub                        |
| HUB_ADMIN     | 9000000003   | hub123      | Nagpur Hub                        |
| HUB_ADMIN     | 9000000004   | hub123      | Chennai Hub                       |
| HUB_ADMIN     | 9000000005   | hub123      | Kolkata Hub                       |
| HUB_ADMIN     | 9000000006   | hub123      | Ahmedabad Hub                     |
| FARMER        | 9100000001   | farmer123   | Ravi Kumar — Sonipat (Delhi)      |
| FARMER        | 9100000002   | farmer123   | Sunita Devi — Kamptee (Nagpur)    |
| FARMER        | 9100000003   | farmer123   | Mohan Singh — Sanand (Ahmedabad)  |

---

## Resetting Demo Data

If the demo state gets messy, reset everything to the original seed state.

**Option A — via cURL:**

```bash
curl -X POST http://localhost:4567/api/seed/reset
```

**Option B — via the browser console:**

```javascript
fetch('http://localhost:4567/api/seed/reset', { method: 'POST' })
  .then(r => r.json())
  .then(console.log)
```

**Option C — re-run the SQL by hand:**

```bash
mysql -u root -p fasal_db < database/seed.sql
```

---

## API Quick Reference

All endpoints live under `http://localhost:4567`. JSON in, JSON out. Authenticated routes expect a Bearer token in the `Authorization` header.

### Auth

| Method | Path                  | Auth        | Description                              |
| ------ | --------------------- | ----------- | ---------------------------------------- |
| POST   | `/api/auth/register`  | Public      | Create a new farmer account              |
| POST   | `/api/auth/login`     | Public      | Authenticate; returns a Bearer token     |
| GET    | `/api/spokes`         | Public      | All spokes (for the registration form)   |

### Farmer

| Method | Path                          | Auth        | Description                                  |
| ------ | ----------------------------- | ----------- | -------------------------------------------- |
| GET    | `/api/farmer/produce-types`   | Public      | All produce types with their lambda values   |
| POST   | `/api/farmer/listings`        | Logged in   | Create a produce listing                     |
| GET    | `/api/farmer/listings`        | Logged in   | The current farmer's listings with live Q(t) |

### Hub

| Method | Path                          | Auth        | Description                                  |
| ------ | ----------------------------- | ----------- | -------------------------------------------- |
| GET    | `/api/hub/:hubId/inventory`   | Logged in   | Current stock at the hub, with live Q(t)     |
| GET    | `/api/hub/:hubId/demand`      | Logged in   | Demand entries at the hub                    |
| GET    | `/api/hub/:hubId/surplus`     | Logged in   | Inventory minus local demand per produce     |
| GET    | `/api/hub/:hubId/vehicles`    | Logged in   | Vehicles parked at the hub                   |
| GET    | `/api/hub/:hubId/routes`      | Logged in   | Routes that touch the hub                    |

### Routing

| Method | Path                  | Auth        | Description                                  |
| ------ | --------------------- | ----------- | -------------------------------------------- |
| POST   | `/api/routing/run`    | Logged in   | Run the routing engine for one source hub    |
| GET    | `/api/routing/routes` | Logged in   | All PLANNED or ACTIVE routes                 |

### Super Admin

| Method | Path                    | Auth        | Description                                  |
| ------ | ----------------------- | ----------- | -------------------------------------------- |
| GET    | `/api/admin/hubs`       | Logged in   | Every hub                                    |
| GET    | `/api/admin/spokes`     | Logged in   | Every spoke                                  |
| GET    | `/api/admin/vehicles`   | Logged in   | Every vehicle                                |
| GET    | `/api/admin/routes`     | Logged in   | Every route with stops + cargo               |
| GET    | `/api/admin/overview`   | Logged in   | Summary counts for the dashboard             |

### Maintenance

| Method | Path                  | Auth        | Description                                  |
| ------ | --------------------- | ----------- | -------------------------------------------- |
| POST   | `/api/seed/reset`     | Public      | Wipe and reload all demo data                |

---

## Port Reference

- **Backend HTTP server:** `http://localhost:4567`
- **MySQL:** default `localhost:3306`
- **Frontend:** no server needed — open the `.html` files directly in your browser

To change the backend port, edit the `SERVER_PORT` constant in `backend/src/main/java/com/fasal/Main.java`, and update `API_BASE` in `frontend-web/js/api.js` to match.

---

## Troubleshooting

**`Port 4567 already in use`**

Something else is bound to that port. On Mac/Linux: `lsof -i :4567` then kill that PID. On Windows: `netstat -ano | findstr :4567`. Or change the port in `Main.java` and `api.js`.

**`MySQL connection refused`**

The backend can't reach MySQL. Check:
- The MySQL service is running (`brew services list`, `systemctl status mysql`, or Windows Services).
- The credentials in `DatabaseConnection.java` match your MySQL setup.
- The database exists: `SHOW DATABASES;` should list `fasal_db`.

**CORS errors in the browser console**

The backend must already be running on `http://localhost:4567` before you open any HTML file. CORS is enabled globally by the `before(...)` filter in `Main.java`.

**Blank map on super-admin.html**

The Leaflet library is loaded from `unpkg.com`. Check your internet connection or unblock the host in your firewall/proxy.

**`No idle vehicle available at this hub` from the routing engine**

You have already dispatched every truck at that hub. POST `/api/seed/reset` to reset all vehicles to IDLE.

**`mvn exec:java` cannot find the plugin**

Your Maven config doesn't have the exec plugin in its default groups. Replace the last line of `start.sh` / `start.bat` with:

```bash
mvn -q package -DskipTests && java -jar target/fasal-backend.jar
```

**`mvn` not found**

Install Maven and add its `bin/` directory to your PATH. Mac: `brew install maven`. Linux: `sudo apt install maven`. Windows: download from maven.apache.org.

**Special characters in your MySQL password broke the startup script**

The script does a plain text substitution into `DatabaseConnection.java`. If your password contains `"`, `\`, `$`, or `&`, edit that Java file by hand instead and skip the prompt step.

---

## How the Routing Algorithm Works

The heart of FASAL lives in `RoutingEngine.java`. For any source hub it runs seven clearly-named steps:

1. **calculateSurplus** — what does this hub have too much of?
2. **findMatchingDemands** — which other hubs want it, and will the produce still be fresh on arrival?
3. **prioritiseDemands** — sort by lambda descending (most perishable first).
4. **buildRoute** — greedy nearest-neighbour, capacity-constrained.
5. **evaluateColdStorage** — does any cargo arrive below the buyer's threshold?
6. **persistRoute** — `INSERT` into `routes` / `route_stops` / `route_cargo`; mark the vehicle `IN_TRANSIT`.
7. **buildResult** — return a `RouteResult` with a human-readable summary string.

The Hub Admin panel and the Super Admin console both animate these steps live so you can watch exactly what the algorithm is doing.

---

## Project Structure

```
FASAL/
├── start.sh                ← Mac / Linux bootstrap script
├── start.bat               ← Windows bootstrap script
├── README.md               ← this file
│
├── database/
│   ├── schema.sql          ← 13 tables, FKs, indexes
│   └── seed.sql            ← 6 hubs, 30 distance rows, 18 spokes,
│                             10 users, 22 inventory rows,
│                             14 demand rows, 12 vehicles
│
├── backend/                ← Java backend (Spark + Gson + JDBC)
│   ├── pom.xml             ← Maven config and dependencies
│   └── src/main/java/com/fasal/
│       ├── Main.java                        ← entry point, port 4567
│       ├── db/
│       │   └── DatabaseConnection.java      ← JDBC singleton
│       ├── models/                          ← POJOs (one per DB row)
│       │   ├── Hub.java, Spoke.java, Farmer.java
│       │   ├── Produce.java, ProduceType.java
│       │   ├── Vehicle.java, Inventory.java, Demand.java
│       │   ├── Route.java, RouteStop.java, RouteCargo.java
│       │   └── RouteResult.java             ← the routing-engine output
│       ├── algorithm/
│       │   ├── QualityCalculator.java       ← Q(t) math helpers
│       │   └── RoutingEngine.java           ← the 7-step routing logic
│       ├── services/                        ← database access layer
│       │   ├── AuthService.java             ← SHA-256, UUID sessions
│       │   ├── FarmerService.java
│       │   ├── HubService.java
│       │   ├── VehicleService.java
│       │   └── SuperAdminService.java
│       └── api/                             ← Spark HTTP routes
│           ├── AuthRoutes.java
│           ├── FarmerRoutes.java
│           ├── HubRoutes.java
│           ├── RoutingRoutes.java
│           ├── SuperAdminRoutes.java
│           └── SeedRoutes.java              ← POST /api/seed/reset
│
└── frontend-web/           ← plain HTML/CSS/JS, no build step
    ├── farmer.html         ← Farmer Portal (mobile-first wizard + dashboard)
    ├── hub-admin.html      ← Hub Admin Panel (sidebar + tables + algorithm demo)
    ├── super-admin.html    ← National Console (Leaflet map + Chart.js)
    ├── css/
    │   ├── base.css        ← shared design tokens + utility classes
    │   ├── farmer.css
    │   ├── hub-admin.css
    │   └── super-admin.css
    └── js/
        ├── api.js          ← shared fetch wrapper + token storage
        ├── farmer.js
        ├── hub-admin.js
        └── super-admin.js
```

---

## License

This is a demonstration project. Use, fork, and adapt freely.
