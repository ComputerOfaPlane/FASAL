# Class Design Document — FASAL

## 1. Package Overview

```
com.fasal
├── Main                       (entry point)
├── db
│   └── DatabaseConnection     (JDBC singleton)
├── models                     (12 POJOs)
├── algorithm
│   ├── QualityCalculator
│   └── RoutingEngine          (uses inner classes Surplus, MatchedDemand)
├── services                   (5 static utility classes)
│   ├── AuthService
│   ├── FarmerService
│   ├── HubService
│   ├── VehicleService
│   └── SuperAdminService
└── api                        (6 route classes)
    ├── AuthRoutes
    ├── FarmerRoutes
    ├── HubRoutes
    ├── RoutingRoutes
    ├── SuperAdminRoutes
    └── SeedRoutes
```

All `services` and `api` classes follow the same pattern: a `final class` (effectively — no `final` keyword but no subclasses), a `private` constructor, and `public static` methods. This is intentional — no dependency-injection container, no service locator, just clear static calls.

---

## 2. Models — Class Diagram

```mermaid
classDiagram
    class Hub {
        -int id
        -String name
        -String city
        -double latitude
        -double longitude
        +getters/setters
    }
    class Spoke {
        -int id
        -String name
        -int hubId
        -double latitude
        -double longitude
    }
    class Farmer {
        -int id
        -String name
        -String phone
        -String passwordHash
        -String role
        -Integer spokeId
        -Integer hubId
        -Timestamp createdAt
        Note: represents any user row, not just farmers
    }
    class ProduceType {
        -int id
        -String name
        -double lambdaValue
        -String unit
    }
    class Produce {
        -int id
        -int farmerId
        -int produceTypeId
        -double quantityKg
        -LocalDate harvestDate
        -int hubId
        -String status
        -Timestamp createdAt
        -String produceName
        -double lambdaValue
        -double currentQuality
    }
    class Inventory {
        -int id
        -int hubId
        -int produceTypeId
        -double quantityKg
        -LocalDate avgHarvestDate
        -Timestamp lastUpdated
        -String produceName
        -double lambdaValue
        -double currentQuality
    }
    class Demand {
        -int id
        -int hubId
        -int produceTypeId
        -double requiredQuantityKg
        -double minQualityThreshold
        -Timestamp createdAt
        -String produceName
        -double lambdaValue
    }
    class Vehicle {
        -int id
        -String name
        -double capacityKg
        -int currentHubId
        -String status
    }
    class Route {
        -int id
        -int vehicleId
        -Timestamp createdAt
        -String status
        -boolean requiresColdStorage
        -String vehicleName
        -List~RouteStop~ stops
        -List~RouteCargo~ cargo
    }
    class RouteStop {
        -int id
        -int routeId
        -int hubId
        -int stopOrder
        -Timestamp arrivedAt
        -String hubName
    }
    class RouteCargo {
        -int id
        -int routeId
        -int produceTypeId
        -double quantityKg
        -int sourceHubId
        -int destinationHubId
        -String produceName
        -String sourceHubName
        -String destinationHubName
    }
    class RouteResult {
        -int routeId
        -String vehicleName
        -int vehicleId
        -List~RouteStop~ stops
        -List~RouteCargo~ cargo
        -boolean requiresColdStorage
        -Map~Integer,Double~ qualityAtEachStop
        -String humanReadableSummary
        -String coldStorageReason
    }

    Route "1" *-- "many" RouteStop
    Route "1" *-- "many" RouteCargo
    RouteResult "1" *-- "many" RouteStop
    RouteResult "1" *-- "many" RouteCargo
```

**Composition (♦):** `Route` owns its `stops` and `cargo` lists; serialised together in JSON output. `RouteResult` is the algorithm's output container — same composition relationship.

---

## 3. Algorithm Layer — Class Diagram

```mermaid
classDiagram
    class QualityCalculator {
        <<utility>>
        -final double HOURS_PER_DAY
        -final double INFINITE_DAYS
        +final double MAX_QUALITY
        +final double MIN_QUALITY
        +calculateQuality(lambda, harvestDate) double
        +calculateQualityAtArrival(lambda, harvestDate, travelHours) double
        +daysUntilThreshold(lambda, harvestDate, threshold) double
        +needsColdStorage(lambda, harvestDate, travelHours, minQ) boolean
        -daysBetween(from, to) double
    }
    class RoutingEngine {
        -final String STATUS_PLANNED
        -final String STATUS_IN_TRANSIT
        -final String STATUS_IDLE
        -final int FIRST_STOP_ORDER
        -final double SOURCE_HUB_QUALITY
        +runRouting(sourceHubId) RouteResult
        -calculateSurplus(sourceHubId) List~Surplus~
        -findMatchingDemands(surpluses, sourceHubId) List~MatchedDemand~
        -prioritiseDemands(matched) List~MatchedDemand~
        -buildRoute(surpluses, prioritised, sourceHubId, capacity, outStops, outCargo, outQuality)
        -evaluateColdStorage(stops, cargo, demands, sourceHubId, reasonOut) boolean
        -persistRoute(vehicle, stops, cargo, requiresColdStorage) int
        -buildResult(routeId, vehicle, stops, cargo, quality, requiresColdStorage, coldReason, sourceHubId) RouteResult
        -findNearestPendingDemand(currentHubId, pending) MatchedDemand
        -lookupTravelHours(from, to) double
        -fillStopHubNames(stops)
        -pickIdleVehicle(sourceHubId) Vehicle
        -buildEmptyResult(reason) RouteResult
        -buildSummarySentence(...) String
    }
    class Surplus {
        <<inner static>>
        ~int produceTypeId
        ~String produceName
        ~double quantityKg
        ~LocalDate avgHarvestDate
        ~double lambdaValue
    }
    class MatchedDemand {
        <<inner static>>
        ~int demandId
        ~int destinationHubId
        ~String destinationHubName
        ~int produceTypeId
        ~String produceName
        ~double requiredQuantityKg
        ~double minQualityThreshold
        ~double travelTimeHoursFromSource
        ~double distanceKmFromSource
        ~double projectedQualityFromSource
        ~LocalDate avgHarvestDate
        ~double lambdaValue
    }
    RoutingEngine "1" *-- "many" Surplus
    RoutingEngine "1" *-- "many" MatchedDemand
    RoutingEngine ..> QualityCalculator : uses
    RoutingEngine ..> DatabaseConnection : uses
```

---

## 4. Services Layer — Class Diagram

```mermaid
classDiagram
    class AuthService {
        <<service>>
        +final String ROLE_FARMER
        +final String ROLE_HUB_ADMIN
        +final String ROLE_SUPER_ADMIN
        -final String HASH_ALGORITHM "SHA-256"
        +register(name, phone, password, role, spokeId, hubId) Map
        +login(phone, password) Map
        +validateToken(token) Map
        +hashPassword(password) String
        -isPhoneInUse(phone) boolean
        -findUserByPhone(phone) Farmer
        -createSession(userId) String
    }
    class FarmerService {
        <<service>>
        +createListing(farmerId, produceTypeId, qty, harvestDate) int
        +getListingsByFarmer(farmerId) List~Produce~
        +getAllListingsAtHub(hubId) List~Produce~
        +getAllProduceTypes() List~ProduceType~
        +getAllSpokes() List~Spoke~
        -findHubIdForFarmer(farmerId) int
        -readListings(sql, filterValue) List~Produce~
    }
    class HubService {
        <<service>>
        +getInventory(hubId) List~Inventory~
        +getDemand(hubId) List~Demand~
        +getSurplus(hubId) List~Map~
        +getVehicles(hubId) List~Vehicle~
        +getRoutes(hubId) List~Route~
        ~loadRoutesByIds(ids) List~Route~  "package-private; shared with SuperAdminService"
    }
    class VehicleService {
        <<service>>
        +getAll() List~Vehicle~
        +getByHub(hubId) List~Vehicle~
        +updateStatus(vehicleId, status)
        +updateCurrentHub(vehicleId, hubId)
        -mapRow(ResultSet) Vehicle
    }
    class SuperAdminService {
        <<service>>
        +getAllHubs() List~Hub~
        +getAllSpokes() List~Spoke~
        +getAllVehicles() List~Vehicle~
        +getAllRoutes() List~Route~
        +getOverviewStats() Map
        -countSimple(sql) int
        -countWithOneStatus(sql, status) int
        -countWithTwoStatuses(sql, a, b) int
    }
    class DatabaseConnection {
        <<utility>>
        -final String DB_URL
        -final String DB_USER
        -final String DB_PASSWORD
        -final String JDBC_DRIVER
        +getConnection() Connection
    }

    AuthService ..> DatabaseConnection : uses
    FarmerService ..> DatabaseConnection : uses
    HubService ..> DatabaseConnection : uses
    VehicleService ..> DatabaseConnection : uses
    SuperAdminService ..> DatabaseConnection : uses
    SuperAdminService ..> HubService : delegates loadRoutesByIds
    SuperAdminService ..> VehicleService : delegates getAllVehicles
    FarmerService ..> QualityCalculator : uses
    HubService ..> QualityCalculator : uses
```

---

## 5. API Layer — Class Diagram

```mermaid
classDiagram
    class Main {
        +final int SERVER_PORT
        +final String CORS_ALLOWED_ORIGIN
        -static File frontendRoot
        +main(args)
        -verifyDatabaseOrExit()
        -resolveFrontendRoot()
        -configureServer()
        -registerRoutes()
        -registerStaticFileHandler()
        -guessMimeType(path) String
    }
    class AuthRoutes {
        <<route>>
        +register()
        -error(message) Map
        -readString(body, field) String
        -readOptionalInt(body, field) Integer
    }
    class FarmerRoutes {
        <<route>>
        +register()
        -requireSession(req, res) Map
        -error(message) Map
    }
    class HubRoutes {
        <<route>>
        +register()
        -parseHubId(req) int
        -isAuthenticated(req, res) boolean
        -error(message) Map
    }
    class RoutingRoutes {
        <<route>>
        +register()
        -isAuthenticated(req, res) boolean
        -error(message) Map
    }
    class SuperAdminRoutes {
        <<route>>
        +register()
        -isAuthenticated(req, res) boolean
        -error(message) Map
    }
    class SeedRoutes {
        <<route>>
        +register()
        -resetAndReseed()
        -execNoArgs(conn, sql)
        -insertHubs(conn) / insertHubDistances / insertSpokes / insertProduceTypes / insertUsers / insertInventory / insertDemand / insertVehicles
    }

    Main ..> AuthRoutes : registers
    Main ..> FarmerRoutes : registers
    Main ..> HubRoutes : registers
    Main ..> RoutingRoutes : registers
    Main ..> SuperAdminRoutes : registers
    Main ..> SeedRoutes : registers

    AuthRoutes ..> AuthService : delegates
    FarmerRoutes ..> AuthService : validateToken
    FarmerRoutes ..> FarmerService : delegates
    HubRoutes ..> AuthService : validateToken
    HubRoutes ..> HubService : delegates
    RoutingRoutes ..> AuthService : validateToken
    RoutingRoutes ..> RoutingEngine : runRouting
    SuperAdminRoutes ..> AuthService : validateToken
    SuperAdminRoutes ..> SuperAdminService : delegates
    SeedRoutes ..> AuthService : hashPassword
    SeedRoutes ..> DatabaseConnection : uses
```

---

## 6. Frontend "Classes" — Pseudo Module Map

Vanilla JS uses no actual classes, but `api.js` and the per-page scripts have a clear module shape:

```mermaid
classDiagram
    class api_js {
        <<module>>
        +const API_BASE
        +const AUTH_STORAGE_KEY
        +apiFetch(path, options) Promise
        +apiGet(path) Promise
        +apiPost(path, body) Promise
        +saveAuth(token, userId, role, hubId, spokeId, name)
        +getToken() string
        +getAuth() object
        +clearAuth()
        +isLoggedIn() boolean
    }
    class farmer_js {
        <<module>>
        -onboardingState
        -DASHBOARD_REFRESH_MS
        -dashboardInterval
        -spokesCache
        -produceTypesCache
        +initApp()
        +attachEventListeners()
        +showStep(n) / advanceFromStepN()
        +registerAccount()
        +login() / logout()
        +switchTab(name)
        +loadDashboard() / refreshDashboard()
        +renderListingCard(l)
        +loadMandi() / submitListing()
        +renderMandiCard(l)
        +showToast(message, type)
        +formatDate / qualityClass / esc
    }
    class hub_admin_js {
        <<module>>
        -INVENTORY_REFRESH_MS
        -ALGO_STEP_DELAY_MS
        -hubsCache, produceTypesCache
        +initApp() / login() / logout()
        +switchSection(name)
        +loadOverview / loadInventory / loadDemand / loadSurplus / loadVehicles
        +runAlgorithm()
        +animateResult(result, surplus)
        +buildSurplusCard / MatchedCard / PriorityCard / RoutePlannedCard / ColdStorageCard / SavedCard
        +haversineKm / parseDateLenient / formatDate / formatDateTime / qualityClass / esc / showToast
    }
    class super_admin_js {
        <<module>>
        -map, hubsData, spokesData, vehiclesData, routesData, produceTypesData
        -hubLayer, spokeLayer, vehicleLayer, routeLayer
        -demoRouteLayer, demoVehicleMarker, qualityChart
        -toggleState
        +initApp() / login() / logout()
        +initMap() / loadStats() / loadAllData()
        +loadHubs / Spokes / Vehicles / Routes
        +toggleHubs / Spokes / Vehicles / Routes / clearAll
        +renderHubMarkers() / populateHubPopup()
        +renderSpokeMarkers / VehicleMarkers / RouteLines
        +initQualityGraph / updateQualityChart(produceTypeId)
        +populateHubDropdown / runDemoAlgorithm / animateDemoOnMap / animateVehicleAlong
    }

    farmer_js ..> api_js : uses
    hub_admin_js ..> api_js : uses
    super_admin_js ..> api_js : uses
```

---

## 7. Key Static Constants by File

A consolidated cheatsheet — useful when reviewing diffs or proposing changes.

| File | Constants |
|---|---|
| `Main.java` | `SERVER_PORT=4567`, `EXIT_CODE_DB_FAILURE=1`, CORS allowed origin/methods/headers |
| `DatabaseConnection.java` | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JDBC_DRIVER` |
| `AuthService.java` | `HASH_ALGORITHM="SHA-256"`, `ROLE_FARMER`, `ROLE_HUB_ADMIN`, `ROLE_SUPER_ADMIN` |
| `RoutingEngine.java` | `STATUS_PLANNED`, `STATUS_IN_TRANSIT`, `STATUS_IDLE`, `FIRST_STOP_ORDER=1`, `SOURCE_HUB_QUALITY=1.0`, `SELF_TRAVEL_HOURS=0.0` |
| `QualityCalculator.java` | `HOURS_PER_DAY=24.0`, `INFINITE_DAYS=+∞`, `MAX_QUALITY=1.0`, `MIN_QUALITY=0.0` |
| Route classes | `HTTP_UNAUTHORIZED=401`, `HTTP_BAD_REQUEST=400`, `HTTP_SERVER_ERROR=500`, `CONTENT_TYPE_JSON="application/json"`, `BEARER_PREFIX="Bearer "` |
| `SeedRoutes.java` | `DEFAULT_TRUCK_CAPACITY_KG=1000.0`, `VEHICLE_STATUS_IDLE="IDLE"` |
| `api.js` | `API_BASE="http://localhost:4567"`, `AUTH_STORAGE_KEY="fasal_auth"` |
| `super-admin.js` | `INDIA_CENTER=[20.5937,78.9629]`, `INDIA_ZOOM=5`, `CHART_MAX_DAYS=30`, `COLD_STORAGE_FACTOR=0.3`, `CHART_MIN_THRESHOLD=0.5`, `DEMO_STEP_PAUSE_MS=1500`, `DEMO_LEG_DURATION_MS=1500` |
| `farmer.js` | `DASHBOARD_REFRESH_MS=30000` |
| `hub-admin.js` | `INVENTORY_REFRESH_MS=60000`, `ALGO_STEP_DELAY_MS=400` |

---

## 8. Dependency Direction (Layer Rule)

```
api  ──► services  ──► algorithm  ──► db
                      └─► models  ◄── (every layer reads/writes models)
```

* `api` depends on `services` (and on `algorithm.RoutingEngine` for the routing endpoint).
* `services` depends on `db` and `algorithm.QualityCalculator`.
* `algorithm` depends on `db` and `models`.
* `models` is leaf — depends on nothing project-internal.

There are no upward dependencies. No cycles.
