# Entity-Relationship Diagram — FASAL

## 1. Conceptual Overview

13 tables grouped by domain:

| Group | Tables | Purpose |
|---|---|---|
| Geography | `hubs`, `hub_distances`, `spokes` | The physical network |
| Identity | `users`, `sessions` | Who can sign in and how |
| Catalogue | `produce_types` | Master list of produce with λ |
| Operational data | `produce_listings`, `inventory`, `demand` | What was harvested, what's in stock, what's wanted |
| Fleet | `vehicles` | The trucks |
| Routing | `routes`, `route_stops`, `route_cargo` | Planned/active/completed deliveries |

## 2. Full ER Diagram (Mermaid)

```mermaid
erDiagram
    HUBS ||--o{ HUB_DISTANCES : "from"
    HUBS ||--o{ HUB_DISTANCES : "to"
    HUBS ||--o{ SPOKES : "owns"
    HUBS ||--o{ USERS : "manages (HUB_ADMIN)"
    HUBS ||--o{ PRODUCE_LISTINGS : "stored at"
    HUBS ||--o{ INVENTORY : "stocks"
    HUBS ||--o{ DEMAND : "requires"
    HUBS ||--o{ VEHICLES : "parked at"
    HUBS ||--o{ ROUTE_STOPS : "visited at"
    HUBS ||--o{ ROUTE_CARGO : "source"
    HUBS ||--o{ ROUTE_CARGO : "destination"

    SPOKES ||--o{ USERS : "farmers belong to"

    USERS ||--o{ SESSIONS : "owns"
    USERS ||--o{ PRODUCE_LISTINGS : "creates"

    PRODUCE_TYPES ||--o{ PRODUCE_LISTINGS : "is a"
    PRODUCE_TYPES ||--o{ INVENTORY : "is a"
    PRODUCE_TYPES ||--o{ DEMAND : "is a"
    PRODUCE_TYPES ||--o{ ROUTE_CARGO : "is a"

    VEHICLES ||--o{ ROUTES : "assigned to"
    ROUTES ||--o{ ROUTE_STOPS : "has"
    ROUTES ||--o{ ROUTE_CARGO : "carries"

    HUBS {
        int id PK
        varchar name
        varchar city
        double latitude
        double longitude
    }
    HUB_DISTANCES {
        int hub_id_from PK,FK
        int hub_id_to PK,FK
        double distance_km
        double travel_time_hours
    }
    SPOKES {
        int id PK
        varchar name
        int hub_id FK
        double latitude
        double longitude
    }
    USERS {
        int id PK
        varchar name
        varchar phone UK
        varchar password_hash
        enum role "FARMER|HUB_ADMIN|SUPER_ADMIN"
        int spoke_id FK "nullable"
        int hub_id FK "nullable"
        timestamp created_at
    }
    SESSIONS {
        varchar id PK "UUID no-dashes"
        int user_id FK
        timestamp created_at
    }
    PRODUCE_TYPES {
        int id PK
        varchar name
        double lambda_value "per-day decay"
        varchar unit "default kg"
    }
    PRODUCE_LISTINGS {
        int id PK
        int farmer_id FK
        int produce_type_id FK
        double quantity_kg
        date harvest_date
        int hub_id FK
        enum status "PENDING|IN_TRANSIT|DELIVERED"
        timestamp created_at
    }
    INVENTORY {
        int id PK
        int hub_id FK
        int produce_type_id FK
        double quantity_kg
        date avg_harvest_date
        timestamp last_updated
    }
    DEMAND {
        int id PK
        int hub_id FK
        int produce_type_id FK
        double required_quantity_kg
        double min_quality_threshold "0.0-1.0"
        timestamp created_at
    }
    VEHICLES {
        int id PK
        varchar name
        double capacity_kg
        int current_hub_id FK
        enum status "IDLE|IN_TRANSIT"
    }
    ROUTES {
        int id PK
        int vehicle_id FK
        timestamp created_at
        enum status "PLANNED|ACTIVE|COMPLETED"
        boolean requires_cold_storage
    }
    ROUTE_STOPS {
        int id PK
        int route_id FK
        int hub_id FK
        int stop_order
        timestamp arrived_at "nullable"
    }
    ROUTE_CARGO {
        int id PK
        int route_id FK
        int produce_type_id FK
        double quantity_kg
        int source_hub_id FK
        int destination_hub_id FK
    }
```

## 3. Cardinality Reference

| From | To | Cardinality | Notes |
|---|---|---|---|
| `hubs` | `spokes` | 1 : N | Each spoke has exactly one parent hub |
| `hubs` | `hub_distances.hub_id_from` | 1 : N | Each hub is the *from* in N rows |
| `hubs` | `hub_distances.hub_id_to` | 1 : N | And the *to* in N rows |
| `spokes` | `users.spoke_id` | 1 : N (nullable) | Only farmers carry a spoke_id |
| `hubs` | `users.hub_id` | 1 : N (nullable) | Only HUB_ADMINs carry a hub_id |
| `users` | `sessions` | 1 : N | One row per active token |
| `users` | `produce_listings.farmer_id` | 1 : N | A farmer creates many listings |
| `produce_types` | `produce_listings` | 1 : N | A produce type is referenced by many listings |
| `produce_types` | `inventory` | 1 : N | Stock per type per hub |
| `produce_types` | `demand` | 1 : N | Demand per type per hub |
| `produce_types` | `route_cargo` | 1 : N | One produce type can be carried on many cargo rows |
| `hubs` | `inventory` | 1 : N | Stock per hub |
| `hubs` | `demand` | 1 : N | Demand per hub |
| `hubs` | `vehicles.current_hub_id` | 1 : N | Trucks currently parked at a hub |
| `vehicles` | `routes` | 1 : N | A vehicle may have many historical routes |
| `routes` | `route_stops` | 1 : N | Ordered by `stop_order` |
| `routes` | `route_cargo` | 1 : N | Multiple cargo items per route |
| `hubs` | `route_stops` | 1 : N | A hub is visited as part of many route_stops |
| `hubs` | `route_cargo.source_hub_id` | 1 : N | Cargo originates from a hub |
| `hubs` | `route_cargo.destination_hub_id` | 1 : N | Cargo terminates at a hub |

## 4. Keys & Indexes

### Primary Keys
* Surrogate `INT AUTO_INCREMENT` on every table except:
  * `sessions.id` (`VARCHAR(64)`) — the Bearer token itself.
  * `hub_distances` (composite `(hub_id_from, hub_id_to)`).

### Unique Constraints
* `users.phone` — login handle.

### Foreign Keys

All relationships above are enforced with `FOREIGN KEY ... REFERENCES ...`. Drop order in `schema.sql` is the reverse of the dependency graph:

```
route_cargo → route_stops → routes → vehicles → demand → inventory →
produce_listings → sessions → users → spokes → produce_types →
hub_distances → hubs
```

### Indexes

* `idx_spokes_hub` on `spokes(hub_id)`.
* `idx_users_phone` on `users(phone)`, `idx_users_role` on `users(role)`.
* `idx_sessions_user` on `sessions(user_id)`.
* `idx_listings_farmer`, `idx_listings_hub` on `produce_listings`.
* `idx_inventory_hub`, `idx_inventory_produce`.
* `idx_demand_hub`, `idx_demand_produce`.
* `idx_vehicles_hub` on `vehicles(current_hub_id)`.
* `idx_routes_vehicle`, `idx_routes_status` on `routes`.
* `idx_route_stops_route`, `idx_route_cargo_route`.

## 5. Notes & Constraints

* `users.spoke_id` and `users.hub_id` are both nullable; exactly one is non-null for FARMER and HUB_ADMIN, both null for SUPER_ADMIN. This is enforced by convention (seed/registration code), not by a DB CHECK constraint.
* `route_stops.stop_order` is intended to start at `1` for the source hub and increment from there. Not enforced by the DB.
* `route_cargo.source_hub_id` is always the route's source hub (the first `route_stops.hub_id`). Not enforced by the DB.
* `vehicles.status` and `vehicles.current_hub_id` are mutated only by `RoutingEngine.persistRoute()` (set to IN_TRANSIT + last stop's hub).
* `produce_listings.status` is set to `PENDING` on creation and otherwise never updated by the current code (a real production system would flip it to IN_TRANSIT when packed onto a route).

## 6. Derived / Computed Data (not in any column)

| Concept | How it's derived | Where |
|---|---|---|
| `currentQuality` (Q(t)) | `Math.exp(-λ · daysSinceHarvest)` | In Java on every read of `inventory` / `produce_listings` |
| `surplus_qty` | `inventory_qty − demand_qty` per produce type at a hub | `HubService.getSurplus()` |
| `projected_Q_at_arrival` | `Math.exp(-λ · (daysSinceHarvest + travelHours/24))` | `QualityCalculator.calculateQualityAtArrival` |
| `requires_cold_storage` | True if any cargo's projected_Q at arrival < its demand's `min_quality_threshold` | `RoutingEngine.evaluateColdStorage()` |
