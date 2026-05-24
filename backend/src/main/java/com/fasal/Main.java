package com.fasal;

import com.fasal.api.AuthRoutes;
import com.fasal.api.FarmerRoutes;
import com.fasal.api.HubRoutes;
import com.fasal.api.RoutingRoutes;
import com.fasal.api.SeedRoutes;
import com.fasal.api.SuperAdminRoutes;
import com.fasal.db.DatabaseConnection;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.SQLException;

import static spark.Spark.before;
import static spark.Spark.get;
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

    // Absolute path to the frontend-web folder, resolved once at startup.
    // Null if we could not find the folder (API still works in that case).
    private static File frontendRoot = null;

    // Boots the whole backend. Called by the JVM on `java -jar fasal-backend.jar`.
    public static void main(String[] args) {
        verifyDatabaseOrExit();
        resolveFrontendRoot();
        configureServer();
        registerRoutes();
        registerStaticFileHandler();
        System.out.println("FASAL backend running on http://localhost:" + SERVER_PORT);
        if (frontendRoot != null) {
            System.out.println("Open in your browser:");
            System.out.println("  http://localhost:" + SERVER_PORT + "/farmer.html");
            System.out.println("  http://localhost:" + SERVER_PORT + "/hub-admin.html");
            System.out.println("  http://localhost:" + SERVER_PORT + "/super-admin.html");
        }
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

    // Finds the frontend-web folder near the JVM's working directory and saves
    // its absolute path. The JVM may run from the project root OR from backend/
    // (which is what start.sh does), so we check a few candidates.
    private static void resolveFrontendRoot() {
        String[] candidates = { "../frontend-web", "frontend-web", "./frontend-web" };
        for (String c : candidates) {
            File dir = new File(c);
            if (dir.exists() && dir.isDirectory()) {
                try {
                    frontendRoot = dir.getCanonicalFile();
                } catch (IOException e) {
                    frontendRoot = dir.getAbsoluteFile();
                }
                System.out.println("Frontend root resolved to: " + frontendRoot.getAbsolutePath());
                return;
            }
        }
        System.err.println("WARN: frontend-web folder not found near "
                         + new File(".").getAbsolutePath()
                         + " - only the JSON API will be available.");
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
    // This MUST run before registerStaticFileHandler() so /api/* matches first.
    private static void registerRoutes() {
        AuthRoutes.register();
        FarmerRoutes.register();
        HubRoutes.register();
        RoutingRoutes.register();
        SuperAdminRoutes.register();
        SeedRoutes.register();
    }

    // Registers a single catch-all GET route that reads files from frontendRoot
    // and streams them back. This sidesteps Spark's built-in static file plumbing
    // entirely - so it works the same on every Spark/Jetty/OS combination.
    private static void registerStaticFileHandler() {
        if (frontendRoot == null) {
            return;
        }

        // GET / -> redirect to the farmer portal so the user lands somewhere useful.
        get("/", (request, response) -> {
            response.redirect("/farmer.html");
            return "";
        });

        // GET anything else -> try to serve a file from frontendRoot.
        get("/*", (request, response) -> {
            String path = request.pathInfo();
            if (path == null || path.equals("/") || path.startsWith("/api/")) {
                response.status(404);
                return "Not found";
            }

            File requested = new File(frontendRoot, path);

            // Security: prevent path traversal so /../../etc/passwd cannot escape.
            String requestedCanonical;
            String rootCanonical;
            try {
                requestedCanonical = requested.getCanonicalPath();
                rootCanonical = frontendRoot.getCanonicalPath();
            } catch (IOException e) {
                response.status(404);
                return "Not found";
            }
            if (!requestedCanonical.startsWith(rootCanonical)) {
                response.status(403);
                return "Forbidden";
            }

            if (!requested.exists() || requested.isDirectory()) {
                response.status(404);
                return "Not found";
            }

            response.type(guessMimeType(path));
            response.status(200);

            // Stream the file straight to the response so big assets work too.
            try (InputStream in = new FileInputStream(requested);
                 OutputStream out = response.raw().getOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                out.flush();
            }
            // Returning null tells Spark we wrote to the raw response ourselves.
            return null;
        });
    }

    // Returns a best-guess MIME type from the file extension.
    private static String guessMimeType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm"))
            return "text/html; charset=UTF-8";
        if (lower.endsWith(".css"))
            return "text/css; charset=UTF-8";
        if (lower.endsWith(".js"))
            return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".json"))
            return "application/json; charset=UTF-8";
        if (lower.endsWith(".svg"))
            return "image/svg+xml";
        if (lower.endsWith(".png"))
            return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return "image/jpeg";
        if (lower.endsWith(".ico"))
            return "image/x-icon";
        if (lower.endsWith(".woff"))
            return "font/woff";
        if (lower.endsWith(".woff2"))
            return "font/woff2";
        return "application/octet-stream";
    }
}
