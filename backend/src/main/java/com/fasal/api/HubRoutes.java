package com.fasal.api;

import com.fasal.services.AuthService;
import com.fasal.services.HubService;
import com.google.gson.Gson;
import spark.Request;

import java.util.HashMap;
import java.util.Map;

import static spark.Spark.get;

// HubRoutes exposes the endpoints a hub admin uses to see what's at their hub:
// inventory, demand, surplus, parked vehicles, and recent routes.
public class HubRoutes {

    // HTTP status codes used by the responses below.
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_BAD_REQUEST  = 400;
    private static final int HTTP_SERVER_ERROR = 500;
    // Response content type used by every endpoint.
    private static final String CONTENT_TYPE_JSON = "application/json";
    // The prefix of the Authorization header value when a Bearer token is used.
    private static final String BEARER_PREFIX = "Bearer ";
    // Shared Gson instance for converting Java objects into JSON strings.
    private static final Gson GSON = new Gson();

    // Private constructor - this class is used statically.
    private HubRoutes() { }

    // Wires the hub endpoints onto the Spark server.
    public static void register() {

        // GET /api/hub/:hubId/inventory - inventory rows with live Q(t).
        get("/api/hub/:hubId/inventory", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            if (!isAuthenticated(request, response)) {
                return GSON.toJson(error("Authentication required."));
            }
            try {
                int hubId = parseHubId(request);
                return GSON.toJson(HubService.getInventory(hubId));
            } catch (NumberFormatException e) {
                response.status(HTTP_BAD_REQUEST);
                return GSON.toJson(error("Invalid hubId."));
            } catch (Exception e) {
                System.err.println("GET /api/hub/:hubId/inventory failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not load inventory: " + e.getMessage()));
            }
        });

        // GET /api/hub/:hubId/demand - demand rows for the hub.
        get("/api/hub/:hubId/demand", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            if (!isAuthenticated(request, response)) {
                return GSON.toJson(error("Authentication required."));
            }
            try {
                int hubId = parseHubId(request);
                return GSON.toJson(HubService.getDemand(hubId));
            } catch (NumberFormatException e) {
                response.status(HTTP_BAD_REQUEST);
                return GSON.toJson(error("Invalid hubId."));
            } catch (Exception e) {
                System.err.println("GET /api/hub/:hubId/demand failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not load demand: " + e.getMessage()));
            }
        });

        // GET /api/hub/:hubId/surplus - inventory minus local demand per produce type.
        get("/api/hub/:hubId/surplus", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            if (!isAuthenticated(request, response)) {
                return GSON.toJson(error("Authentication required."));
            }
            try {
                int hubId = parseHubId(request);
                return GSON.toJson(HubService.getSurplus(hubId));
            } catch (NumberFormatException e) {
                response.status(HTTP_BAD_REQUEST);
                return GSON.toJson(error("Invalid hubId."));
            } catch (Exception e) {
                System.err.println("GET /api/hub/:hubId/surplus failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not load surplus: " + e.getMessage()));
            }
        });

        // GET /api/hub/:hubId/vehicles - vehicles parked at the hub.
        get("/api/hub/:hubId/vehicles", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            if (!isAuthenticated(request, response)) {
                return GSON.toJson(error("Authentication required."));
            }
            try {
                int hubId = parseHubId(request);
                return GSON.toJson(HubService.getVehicles(hubId));
            } catch (NumberFormatException e) {
                response.status(HTTP_BAD_REQUEST);
                return GSON.toJson(error("Invalid hubId."));
            } catch (Exception e) {
                System.err.println("GET /api/hub/:hubId/vehicles failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not load vehicles: " + e.getMessage()));
            }
        });

        // GET /api/hub/:hubId/routes - routes that touch this hub.
        get("/api/hub/:hubId/routes", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            if (!isAuthenticated(request, response)) {
                return GSON.toJson(error("Authentication required."));
            }
            try {
                int hubId = parseHubId(request);
                return GSON.toJson(HubService.getRoutes(hubId));
            } catch (NumberFormatException e) {
                response.status(HTTP_BAD_REQUEST);
                return GSON.toJson(error("Invalid hubId."));
            } catch (Exception e) {
                System.err.println("GET /api/hub/:hubId/routes failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not load routes: " + e.getMessage()));
            }
        });
    }

    // Helper: turns the :hubId path parameter into an int.
    private static int parseHubId(Request request) {
        return Integer.parseInt(request.params(":hubId"));
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
