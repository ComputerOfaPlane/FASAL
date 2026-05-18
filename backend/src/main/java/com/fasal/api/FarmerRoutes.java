package com.fasal.api;

import com.fasal.services.AuthService;
import com.fasal.services.FarmerService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import spark.Request;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;

// FarmerRoutes exposes the endpoints a farmer uses: create a listing, see
// their listings, look up produce types and spokes.
public class FarmerRoutes {

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
    private FarmerRoutes() { }

    // Wires the farmer endpoints onto the Spark server.
    public static void register() {

        // POST /api/farmer/listings - create a new produce listing.
        post("/api/farmer/listings", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            Map<String, Object> session = requireSession(request, response);
            if (session == null) {
                return GSON.toJson(error("Authentication required."));
            }
            try {
                int farmerId = ((Number) session.get("user_id")).intValue();
                JsonObject body = JsonParser.parseString(request.body()).getAsJsonObject();
                int produceTypeId = body.get("produce_type_id").getAsInt();
                double quantityKg = body.get("quantity_kg").getAsDouble();
                LocalDate harvestDate = LocalDate.parse(body.get("harvest_date").getAsString());

                int newId = FarmerService.createListing(farmerId, produceTypeId,
                                                        quantityKg, harvestDate);
                Map<String, Object> out = new HashMap<>();
                out.put("listing_id", newId);
                out.put("message",    "Listing created.");
                return GSON.toJson(out);
            } catch (IllegalArgumentException e) {
                response.status(HTTP_BAD_REQUEST);
                return GSON.toJson(error(e.getMessage()));
            } catch (Exception e) {
                System.err.println("POST /api/farmer/listings failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not create listing: " + e.getMessage()));
            }
        });

        // GET /api/farmer/listings - listings owned by the logged-in farmer.
        get("/api/farmer/listings", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            Map<String, Object> session = requireSession(request, response);
            if (session == null) {
                return GSON.toJson(error("Authentication required."));
            }
            try {
                int farmerId = ((Number) session.get("user_id")).intValue();
                return GSON.toJson(FarmerService.getListingsByFarmer(farmerId));
            } catch (Exception e) {
                System.err.println("GET /api/farmer/listings failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not load listings: " + e.getMessage()));
            }
        });

        // GET /api/farmer/produce-types - all produce types for the dropdown.
        get("/api/farmer/produce-types", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            try {
                return GSON.toJson(FarmerService.getAllProduceTypes());
            } catch (Exception e) {
                System.err.println("GET /api/farmer/produce-types failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not load produce types: " + e.getMessage()));
            }
        });

        // GET /api/spokes - every spoke in the system for the registration form.
        get("/api/spokes", (request, response) -> {
            response.type(CONTENT_TYPE_JSON);
            try {
                return GSON.toJson(FarmerService.getAllSpokes());
            } catch (Exception e) {
                System.err.println("GET /api/spokes failed: " + e.getMessage());
                response.status(HTTP_SERVER_ERROR);
                return GSON.toJson(error("Could not load spokes: " + e.getMessage()));
            }
        });
    }

    // Helper: extracts and validates the auth token. Returns the session map
    // or null (and sets a 401 status) when the token is missing or invalid.
    private static Map<String, Object> requireSession(Request request, spark.Response response) {
        String header = request.headers("Authorization");
        if (header == null) {
            response.status(HTTP_UNAUTHORIZED);
            return null;
        }
        String token = header.startsWith(BEARER_PREFIX)
                         ? header.substring(BEARER_PREFIX.length())
                         : header;
        Map<String, Object> session = AuthService.validateToken(token);
        if (session == null) {
            response.status(HTTP_UNAUTHORIZED);
        }
        return session;
    }

    // Helper: returns a JSON-style error envelope as a Java Map.
    private static Map<String, Object> error(String message) {
        Map<String, Object> e = new HashMap<>();
        e.put("error", message);
        return e;
    }
}
