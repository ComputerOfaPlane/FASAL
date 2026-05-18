package com.fasal.api;

import com.fasal.db.DatabaseConnection;
import com.fasal.services.AuthService;
import com.google.gson.Gson;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static spark.Spark.post;

// SeedRoutes exposes one endpoint: POST /api/seed/reset, which wipes every
// table and reseeds the database with the same data that seed.sql produces.
public class SeedRoutes {

    // HTTP status code used when seeding fails.
    private static final int HTTP_SERVER_ERROR = 500;
    // Response content type used by the endpoint.
    private static final String CONTENT_TYPE_JSON = "application/json";
    // Default truck capacity in kilograms used when seeding the fleet.
    private static final double DEFAULT_TRUCK_CAPACITY_KG = 1000.0;
    // Status string used for newly-seeded vehicles.
    private static final String VEHICLE_STATUS_IDLE = "IDLE";
    // Shared Gson instance for JSON output.
    private static final Gson GSON = new Gson();

    // Private constructor - this class is used statically.
    private SeedRoutes() { }

    // Wires the seed endpoint onto the Spark server.
    public static void register() {
        post("/api/seed/reset", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            try {
                resetAndReseed();
                Map<String, Object> ok = new HashMap<>();
                ok.put("message", "Database reset and reseeded successfully");
                return GSON.toJson(ok);
            } catch (Exception e) {
                System.err.println("POST /api/seed/reset failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                Map<String, Object> err = new HashMap<>();
                err.put("error", "Could not reset database: " + e.getMessage());
                return GSON.toJson(err);
            }
        });
    }

    // Deletes every row from every data table and then re-inserts the seed data.
    private static void resetAndReseed() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            execNoArgs(conn, "SET FOREIGN_KEY_CHECKS = 0");
            String[] truncates = new String[] {
                "TRUNCATE TABLE route_cargo",
                "TRUNCATE TABLE route_stops",
                "TRUNCATE TABLE routes",
                "TRUNCATE TABLE vehicles",
                "TRUNCATE TABLE demand",
                "TRUNCATE TABLE inventory",
                "TRUNCATE TABLE produce_listings",
                "TRUNCATE TABLE sessions",
                "TRUNCATE TABLE users",
                "TRUNCATE TABLE spokes",
                "TRUNCATE TABLE produce_types",
                "TRUNCATE TABLE hub_distances",
                "TRUNCATE TABLE hubs"
            };
            for (String sql : truncates) {
                execNoArgs(conn, sql);
            }
            execNoArgs(conn, "SET FOREIGN_KEY_CHECKS = 1");

            insertHubs(conn);
            insertHubDistances(conn);
            insertSpokes(conn);
            insertProduceTypes(conn);
            insertUsers(conn);
            insertInventory(conn);
            insertDemand(conn);
            insertVehicles(conn);

            conn.commit();
        }
    }

    // Helper: run a statement that takes no parameters.
    private static void execNoArgs(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    // Seeds the six hub rows with explicit IDs.
    private static void insertHubs(Connection conn) throws SQLException {
        String sql = "INSERT INTO hubs (id, name, city, latitude, longitude) VALUES (?, ?, ?, ?, ?)";
        Object[][] rows = new Object[][] {
            {1, "Delhi Hub",     "Delhi",     28.6139, 77.2090},
            {2, "Mumbai Hub",    "Mumbai",    19.0760, 72.8777},
            {3, "Nagpur Hub",    "Nagpur",    21.1458, 79.0882},
            {4, "Chennai Hub",   "Chennai",   13.0827, 80.2707},
            {5, "Kolkata Hub",   "Kolkata",   22.5726, 88.3639},
            {6, "Ahmedabad Hub", "Ahmedabad", 23.0225, 72.5714}
        };
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] r : rows) {
                ps.setInt(1, (int) r[0]);
                ps.setString(2, (String) r[1]);
                ps.setString(3, (String) r[2]);
                ps.setDouble(4, (double) r[3]);
                ps.setDouble(5, (double) r[4]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // Seeds the 6x6 driving distance matrix.
    private static void insertHubDistances(Connection conn) throws SQLException {
        double[][] data = new double[][] {
            {1, 2, 1400, 24}, {2, 1, 1400, 24},
            {1, 3, 1050, 17}, {3, 1, 1050, 17},
            {1, 4, 2200, 35}, {4, 1, 2200, 35},
            {1, 5, 1500, 24}, {5, 1, 1500, 24},
            {1, 6,  950, 15}, {6, 1,  950, 15},
            {2, 3,  830, 14}, {3, 2,  830, 14},
            {2, 4, 1340, 22}, {4, 2, 1340, 22},
            {2, 5, 2000, 32}, {5, 2, 2000, 32},
            {2, 6,  530,  9}, {6, 2,  530,  9},
            {3, 4, 1170, 19}, {4, 3, 1170, 19},
            {3, 5, 1140, 19}, {5, 3, 1140, 19},
            {3, 6, 1010, 16}, {6, 3, 1010, 16},
            {4, 5, 1670, 28}, {5, 4, 1670, 28},
            {4, 6, 1820, 30}, {6, 4, 1820, 30},
            {5, 6, 2150, 35}, {6, 5, 2150, 35}
        };
        String sql = "INSERT INTO hub_distances (hub_id_from, hub_id_to, distance_km, travel_time_hours) "
                   + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (double[] r : data) {
                ps.setInt(1, (int) r[0]);
                ps.setInt(2, (int) r[1]);
                ps.setDouble(3, r[2]);
                ps.setDouble(4, r[3]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // Seeds the 18 spoke rows.
    private static void insertSpokes(Connection conn) throws SQLException {
        Object[][] rows = new Object[][] {
            { 1, "Sonipat",        1, 28.9931, 77.0151},
            { 2, "Faridabad",      1, 28.4089, 77.3178},
            { 3, "Ghaziabad",      1, 28.6692, 77.4538},
            { 4, "Thane",          2, 19.2183, 72.9781},
            { 5, "Panvel",         2, 18.9894, 73.1175},
            { 6, "Bhiwandi",       2, 19.3002, 73.0635},
            { 7, "Kamptee",        3, 21.2300, 79.1950},
            { 8, "Hingna",         3, 21.0700, 78.9900},
            { 9, "Saoner",         3, 21.3850, 78.9100},
            {10, "Tambaram",       4, 12.9229, 80.1275},
            {11, "Avadi",          4, 13.1147, 80.1006},
            {12, "Sriperumbudur",  4, 12.9694, 79.9433},
            {13, "Howrah",         5, 22.5958, 88.2636},
            {14, "Barasat",        5, 22.7244, 88.4811},
            {15, "Salt Lake",      5, 22.5808, 88.4178},
            {16, "Sanand",         6, 22.9919, 72.3819},
            {17, "Bavla",          6, 22.8300, 72.3600},
            {18, "Mehmedabad",     6, 22.8281, 72.7561}
        };
        String sql = "INSERT INTO spokes (id, name, hub_id, latitude, longitude) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] r : rows) {
                ps.setInt(1, (int) r[0]);
                ps.setString(2, (String) r[1]);
                ps.setInt(3, (int) r[2]);
                ps.setDouble(4, (double) r[3]);
                ps.setDouble(5, (double) r[4]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // Seeds the 10 produce types.
    private static void insertProduceTypes(Connection conn) throws SQLException {
        Object[][] rows = new Object[][] {
            { 1, "milk",    0.45,  "liters"},
            { 2, "spinach", 0.35,  "kg"},
            { 3, "banana",  0.12,  "kg"},
            { 4, "tomato",  0.10,  "kg"},
            { 5, "mango",   0.08,  "kg"},
            { 6, "apple",   0.025, "kg"},
            { 7, "potato",  0.015, "kg"},
            { 8, "onion",   0.018, "kg"},
            { 9, "wheat",   0.002, "kg"},
            {10, "rice",    0.001, "kg"}
        };
        String sql = "INSERT INTO produce_types (id, name, lambda_value, unit) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] r : rows) {
                ps.setInt(1, (int) r[0]);
                ps.setString(2, (String) r[1]);
                ps.setDouble(3, (double) r[2]);
                ps.setString(4, (String) r[3]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // Seeds the 10 user accounts.
    private static void insertUsers(Connection conn) throws SQLException {
        String adminHash  = AuthService.hashPassword("admin123");
        String hubHash    = AuthService.hashPassword("hub123");
        String farmerHash = AuthService.hashPassword("farmer123");

        Object[][] rows = new Object[][] {
            { 1, "FASAL Admin",      "9000000000", adminHash,  "SUPER_ADMIN", null, null},
            { 2, "Admin Delhi",      "9000000001", hubHash,    "HUB_ADMIN",   null,    1},
            { 3, "Admin Mumbai",     "9000000002", hubHash,    "HUB_ADMIN",   null,    2},
            { 4, "Admin Nagpur",     "9000000003", hubHash,    "HUB_ADMIN",   null,    3},
            { 5, "Admin Chennai",    "9000000004", hubHash,    "HUB_ADMIN",   null,    4},
            { 6, "Admin Kolkata",    "9000000005", hubHash,    "HUB_ADMIN",   null,    5},
            { 7, "Admin Ahmedabad",  "9000000006", hubHash,    "HUB_ADMIN",   null,    6},
            { 8, "Ravi Kumar",       "9100000001", farmerHash, "FARMER",         1, null},
            { 9, "Sunita Devi",      "9100000002", farmerHash, "FARMER",         7, null},
            {10, "Mohan Singh",      "9100000003", farmerHash, "FARMER",        16, null}
        };
        String sql = "INSERT INTO users (id, name, phone, password_hash, role, spoke_id, hub_id) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] r : rows) {
                ps.setInt(1, (int) r[0]);
                ps.setString(2, (String) r[1]);
                ps.setString(3, (String) r[2]);
                ps.setString(4, (String) r[3]);
                ps.setString(5, (String) r[4]);
                if (r[5] == null) ps.setNull(6, java.sql.Types.INTEGER);
                else ps.setInt(6, (int) r[5]);
                if (r[6] == null) ps.setNull(7, java.sql.Types.INTEGER);
                else ps.setInt(7, (int) r[6]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // Seeds the inventory rows with dates relative to today.
    private static void insertInventory(Connection conn) throws SQLException {
        LocalDate today = LocalDate.now();
        Object[][] rows = new Object[][] {
            {1, 4, 500.0,   5}, {1, 8, 400.0,   8}, {1, 9, 600.0,  60}, {1, 7, 300.0,  30},
            {2, 3, 350.0,   4}, {2, 10, 800.0, 90}, {2, 8, 100.0,   6}, {2, 2,  80.0,   2},
            {3, 5, 600.0,   6}, {3, 9, 400.0,  70}, {3, 4, 100.0,   5}, {3, 6, 200.0,  10},
            {4, 10, 500.0, 80}, {4, 2,  50.0,   2}, {4, 3, 150.0,   5},
            {5, 7, 700.0,  25}, {5, 10, 600.0, 100}, {5, 2,  80.0,   3}, {5, 1,  50.0,   1},
            {6, 9, 800.0,  50}, {6, 1, 100.0,   1}, {6, 8, 250.0,   7}
        };
        String sql = "INSERT INTO inventory (hub_id, produce_type_id, quantity_kg, avg_harvest_date) "
                   + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] r : rows) {
                ps.setInt(1, (int) r[0]);
                ps.setInt(2, (int) r[1]);
                ps.setDouble(3, (double) r[2]);
                ps.setDate(4, Date.valueOf(today.minusDays((int) r[3])));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // Seeds the demand rows.
    private static void insertDemand(Connection conn) throws SQLException {
        Object[][] rows = new Object[][] {
            {1, 5, 300.0, 0.50}, {1, 2, 100.0, 0.60},
            {2, 4, 300.0, 0.40}, {2, 9, 200.0, 0.30},
            {3, 8, 150.0, 0.40}, {3, 10, 200.0, 0.20},
            {4, 4, 400.0, 0.50}, {4, 8, 250.0, 0.50}, {4, 5, 350.0, 0.40},
            {5, 5, 350.0, 0.40}, {5, 4, 200.0, 0.50}, {5, 1,  80.0, 0.70},
            {6, 3, 200.0, 0.40}, {6, 2, 100.0, 0.60}
        };
        String sql = "INSERT INTO demand (hub_id, produce_type_id, required_quantity_kg, min_quality_threshold) "
                   + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] r : rows) {
                ps.setInt(1, (int) r[0]);
                ps.setInt(2, (int) r[1]);
                ps.setDouble(3, (double) r[2]);
                ps.setDouble(4, (double) r[3]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // Seeds 2 vehicles per hub.
    private static void insertVehicles(Connection conn) throws SQLException {
        String[] cities = {"Delhi", "Mumbai", "Nagpur", "Chennai", "Kolkata", "Ahmedabad"};
        String sql = "INSERT INTO vehicles (id, name, capacity_kg, current_hub_id, status) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int nextId = 1;
            for (int hubIndex = 0; hubIndex < cities.length; hubIndex++) {
                int hubId = hubIndex + 1;
                String cityName = cities[hubIndex];
                for (int truck = 1; truck <= 2; truck++) {
                    ps.setInt(1, nextId++);
                    ps.setString(2, cityName + "-Truck-" + truck);
                    ps.setDouble(3, DEFAULT_TRUCK_CAPACITY_KG);
                    ps.setInt(4, hubId);
                    ps.setString(5, VEHICLE_STATUS_IDLE);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }
}
