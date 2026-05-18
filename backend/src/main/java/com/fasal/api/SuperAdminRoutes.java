package com.fasal.api;

import com.fasal.services.AuthService;
import com.fasal.services.SuperAdminService;
import com.google.gson.Gson;
import spark.Request;

import java.util.HashMap;
import java.util.Map;

import static spark.Spark.get;

// SuperAdminRoutes exposes the system-wide endpoints used by the super admin
// dashboard: list all hubs, all spokes, all vehicles, all routes, and a small
// overview of counts.
public class SuperAdminRoutes {

    // HTTP status codes used by the responses below.
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_SERVER_ERROR = 500;
    // Response content type used by every endpoint.
    private static final String CONTENT_TYPE_JSON = "application/json";
    // The prefix of the Authorization header value when a Bearer token is used.
    private static final String BEARER_PREFIX = "Bearer ";
    // Shared Gson instance for converting Java objects into JSON strings.
    private static final Gson GSON = new Gson();

    // Private constructor - this class is used statically.
    private SuperAdminRoutes() { }

    // Wires the super-admin endpoints onto the Spark server.
    public static void register() {

        // GET /api/admin/hubs - every hub in the system.
        get("/api/admin/hubs", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            if (!isAuthenticated(request, response)) {
                return GSON.toJson(error("Authentication required."));
            }
            try {
                return GSON.toJson(SuperAdminService.getAllHubs());
            } catch (Exception e) {
                System.err.println("GET /api/admin/hubs failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not load hubs: " + e.getMessage()));
            }
        });

        // GET /api/admin/spokes - every spoke with its parent hub.
        get("/api/admin/spokes", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            if (!isAuthenticated(request, response)) {
                return GSON.toJson(error("Authentication required."));
            }
            try {
                return GSON.toJson(SuperAdminService.getAllSpokes());
            } catch (Exception e) {
                System.err.println("GET /api/admin/spokes failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not load spokes: " + e.getMessage()));
            }
        });

        // GET /api/admin/vehicles - every vehicle in the fleet.
        get("/api/admin/vehicles", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            if (!isAuthenticated(request, response)) {
                return GSON.toJson(error("Authentication required."));
            }
            try {
                return GSON.toJson(SuperAdminService.getAllVehicles());
            } catch (Exception e) {
                System.err.println("GET /api/admin/vehicles failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not load vehicles: " + e.getMessage()));
            }
        });

        // GET /api/admin/routes - every route in the system, with stops and cargo.
        get("/api/admin/routes", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            if (!isAuthenticated(request, response)) {
                return GSON.toJson(error("Authentication required."));
            }
            try {
                return GSON.toJson(SuperAdminService.getAllRoutes());
            } catch (Exception e) {
                System.err.println("GET /api/admin/routes failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not load routes: " + e.getMessage()));
            }
        });

        // GET /api/admin/overview - small dashboard summary.
        get("/api/admin/overview", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            if (!isAuthenticated(request, response)) {
                return GSON.toJson(error("Authentication required."));
            }
            try {
                return GSON.toJson(SuperAdminService.getOverviewStats());
            } catch (Exception e) {
                System.err.println("GET /api/admin/overview failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not load overview: " + e.getMessage()));
            }
        });
    }

    // Helper: returns true when the request carries a valid auth token.
    private static boolean isAuthenticated(Request request, spark.Response response) {
        String header = request.headers("Authorization");
        if (header == null) {
            response.status(HTTP_UNAUTHORIZED);
            return false;
        }
        String token = header.startsWith(BEARER_PREFIX)
                         ? header.substring(BEARER_PREFIX.length())
                         : header;
        if (AuthService.validateToken(token) == null) {
            response.status(HTTP_UNAUTHORIZED);
            return false;
        }
        return true;
    }

    // Helper: returns a JSON-style error envelope as a Java Map.
    private static Map<String, Object> error(String message) {
        Map<String, Object> e = new HashMap<>();
        e.put("error", message);
        return e;
    }
}
