package com.fasal.api;

import com.fasal.services.AuthService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

import static spark.Spark.post;

// AuthRoutes exposes the two HTTP endpoints used for signing in:
// POST /api/auth/register and POST /api/auth/login.
public class AuthRoutes {

    // HTTP status code returned when the client sent something we cannot accept.
    private static final int HTTP_BAD_REQUEST   = 400;
    // HTTP status code returned when the server itself blew up.
    private static final int HTTP_SERVER_ERROR  = 500;
    // Response content type used by every endpoint.
    private static final String CONTENT_TYPE_JSON = "application/json";
    // Shared Gson instance for converting Java objects into JSON strings.
    private static final Gson GSON = new Gson();

    // Private constructor - this class is used statically.
    private AuthRoutes() { }

    // Wires the auth endpoints onto the Spark server.
    public static void register() {
        post("/api/auth/register", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            try {
                JsonObject body = JsonParser.parseString(request.body()).getAsJsonObject();
                String name     = readString(body, "name");
                String phone    = readString(body, "phone");
                String password = readString(body, "password");
                String role     = body.has("role") && !body.get("role").isJsonNull()
                                    ? body.get("role").getAsString()
                                    : AuthService.ROLE_FARMER;
                Integer spokeId = readOptionalInt(body, "spoke_id");
                Integer hubId   = readOptionalInt(body, "hub_id");

                if (name == null || phone == null || password == null) {
                    response.status(HTTP_BAD_REQUEST);
                    return GSON.toJson(error("name, phone and password are required."));
                }

                Map<String, Object> result = AuthService.register(name, phone, password,
                                                                   role, spokeId, hubId);
                return GSON.toJson(result);
            } catch (IllegalArgumentException e) {
                response.status(HTTP_BAD_REQUEST);
                return GSON.toJson(error(e.getMessage()));
            } catch (Exception e) {
                System.err.println("/api/auth/register failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not register: " + e.getMessage()));
            }
        });

        post("/api/auth/login", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            try {
                JsonObject body = JsonParser.parseString(request.body()).getAsJsonObject();
                String phone    = readString(body, "phone");
                String password = readString(body, "password");
                if (phone == null || password == null) {
                    response.status(HTTP_BAD_REQUEST);
                    return GSON.toJson(error("phone and password are required."));
                }
                Map<String, Object> result = AuthService.login(phone, password);
                if (result == null) {
                    response.status(HTTP_BAD_REQUEST);
                    return GSON.toJson(error("Invalid phone or password."));
                }
                return GSON.toJson(result);
            } catch (Exception e) {
                System.err.println("/api/auth/login failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not log in: " + e.getMessage()));
            }
        });
    }

    // Helper: returns a JSON-style error envelope as a Java Map.
    private static Map<String, Object> error(String message) {
        Map<String, Object> e = new HashMap<>();
        e.put("error", message);
        return e;
    }

    // Helper: reads a string field from the JSON body, or null when missing/blank.
    private static String readString(JsonObject body, String field) {
        if (!body.has(field) || body.get(field).isJsonNull()) {
            return null;
        }
        String s = body.get(field).getAsString();
        return s == null || s.isEmpty() ? null : s;
    }

    // Helper: reads an optional integer field, returning null when missing.
    private static Integer readOptionalInt(JsonObject body, String field) {
        if (!body.has(field) || body.get(field).isJsonNull()) {
            return null;
        }
        try {
            return body.get(field).getAsInt();
        } catch (Exception e) {
            return null;
        }
    }
}
