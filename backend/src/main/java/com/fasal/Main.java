package com.fasal;

import com.fasal.api.AuthRoutes;
import com.fasal.api.FarmerRoutes;
import com.fasal.api.HubRoutes;
import com.fasal.api.RoutingRoutes;
import com.fasal.api.SeedRoutes;
import com.fasal.api.SuperAdminRoutes;
import com.fasal.db.DatabaseConnection;

import java.sql.Connection;
import java.sql.SQLException;

import static spark.Spark.before;
import static spark.Spark.options;
import static spark.Spark.port;

// Application entry point: configures Spark, verifies the database is reachable,
// then registers every HTTP route the FASAL backend exposes.
public class Main {

    // The TCP port the Spark server will listen on.
    private static final int SERVER_PORT = 4567;
    // Exit code returned to the operating system when startup fails.
    private static final int EXIT_CODE_DB_FAILURE = 1;
    // Header values for the CORS preflight response.
    private static final String CORS_ALLOWED_ORIGIN  = "*";
    private static final String CORS_ALLOWED_METHODS = "GET, POST, PUT, DELETE, OPTIONS";
    private static final String CORS_ALLOWED_HEADERS = "Content-Type, Authorization";

    // Boots the whole backend. Called by the JVM on `java -jar fasal-backend.jar`.
    public static void main(String[] args) {
        verifyDatabaseOrExit();
        configureServer();
        registerRoutes();
        System.out.println("FASAL backend running on http://localhost:" + SERVER_PORT);
    }

    // Tries to open a database connection once. If that fails the user must fix
    // their MySQL setup before the server can do anything useful.
    private static void verifyDatabaseOrExit() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null || conn.isClosed()) {
                throw new SQLException("Database connection was null or closed.");
            }
            System.out.println("Database connection OK.");
        } catch (SQLException e) {
            System.err.println("FATAL: Could not connect to MySQL.");
            System.err.println("Check that MySQL is running and that the credentials in");
            System.err.println("DatabaseConnection.java match your local setup.");
            System.err.println("Details: " + e.getMessage());
            System.exit(EXIT_CODE_DB_FAILURE);
        }
    }

    // Sets the server port and adds a global CORS filter so any browser can call the API.
    private static void configureServer() {
        port(SERVER_PORT);

        // Adds CORS headers to every response before the route handler runs.
        before((request, response) -> {
            response.header("Access-Control-Allow-Origin",  CORS_ALLOWED_ORIGIN);
            response.header("Access-Control-Allow-Methods", CORS_ALLOWED_METHODS);
            response.header("Access-Control-Allow-Headers", CORS_ALLOWED_HEADERS);
        });

        // Handles the browser's CORS preflight request for every URL.
        options("/*", (request, response) -> {
            response.header("Access-Control-Allow-Origin",  CORS_ALLOWED_ORIGIN);
            response.header("Access-Control-Allow-Methods", CORS_ALLOWED_METHODS);
            response.header("Access-Control-Allow-Headers", CORS_ALLOWED_HEADERS);
            return "OK";
        });
    }

    // Asks each module to declare its own HTTP routes.
    private static void registerRoutes() {
        AuthRoutes.register();
        FarmerRoutes.register();
        HubRoutes.register();
        RoutingRoutes.register();
        SuperAdminRoutes.register();
        SeedRoutes.register();
    }
}
