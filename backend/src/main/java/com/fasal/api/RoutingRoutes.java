package com.fasal.api;

import com.fasal.algorithm.RoutingEngine;
import com.fasal.db.DatabaseConnection;
import com.fasal.models.Route;
import com.fasal.models.RouteResult;
import com.fasal.services.AuthService;
import com.fasal.services.HubService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import spark.Request;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;

// RoutingRoutes exposes:
//   POST /api/routing/run     - runs the routing engine for a given hub
//   GET  /api/routing/routes  - lists every PLANNED or ACTIVE route in the system
public class RoutingRoutes {

    // HTTP status codes used by the responses below.
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_BAD_REQUEST  = 400;
    private static final int HTTP_SERVER_ERROR = 500;
    // Response content type used by every endpoint.
    private static final String CONTENT_TYPE_JSON = "application/json";
    // The prefix of the Authorization header value when a Bearer token is used.
    private static final String BEARER_PREFIX = "Bearer ";
    // Status values considered "live" for the list endpoint below.
    private static final String STATUS_PLANNED = "PLANNED";
    private static final String STATUS_ACTIVE  = "ACTIVE";
    // Shared Gson instance for converting Java objects into JSON strings.
    private static final Gson GSON = new Gson();

    // Private constructor - this class is used statically.
    private RoutingRoutes() { }

    // Wires the routing endpoints onto the Spark server.
    public static void register() {

        // POST /api/routing/run - picks the first IDLE truck at the given hub and
        // runs the full RoutingEngine, returning the resulting RouteResult.
        post("/api/routing/run", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            if (!isAuthenticated(request, response)) {
                return GSON.toJson(error("Authentication required."));
            }
            try {
                JsonObject body = JsonParser.parseString(request.body()).getAsJsonObject();
                if (!body.has("hub_id") || body.get("hub_id").isJsonNull()) {
                    response.status(HTTP_BAD_REQUEST);
                    return GSON.toJson(error("hub_id is required."));
                }
                int hubId = body.get("hub_id").getAsInt();
                RouteResult result = new RoutingEngine().runRouting(hubId);
                return GSON.toJson(result);
            } catch (Exception e) {
                System.err.println("POST /api/routing/run failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not run routing: " + e.getMessage()));
            }
        });

        // GET /api/routing/routes - every PLANNED or ACTIVE route in the system.
        get("/api/routing/routes", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            if (!isAuthenticated(request, response)) {
                return GSON.toJson(error("Authentication required."));
            }
            try {
                List<Integer> ids = new ArrayList<>();
                String sql = "SELECT id FROM routes WHERE status = ? OR status = ? ORDER BY id DESC";
                try (Connection conn = DatabaseConnection.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, STATUS_PLANNED);
                    ps.setString(2, STATUS_ACTIVE);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            ids.add(rs.getInt("id"));
                        }
                    }
                }
                List<Route> routes = HubService.loadRoutesByIds(ids);
                return GSON.toJson(routes);
            } catch (Exception e) {
                System.err.println("GET /api/routing/routes failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not load routes: " + e.getMessage()));
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
