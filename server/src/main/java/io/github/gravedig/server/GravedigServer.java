package io.github.gravedig.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.gravedig.server.api.RuleController;
import io.github.gravedig.server.api.ScanController;
import io.github.gravedig.server.db.DatabaseManager;
import io.github.gravedig.server.db.FindingDao;
import io.github.gravedig.server.db.ScanDao;
import io.github.gravedig.server.security.AuthManager;
import io.github.gravedig.server.service.ScanService;
import io.github.gravedig.server.ws.ScanWebSocketHandler;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Map;

public class GravedigServer implements Closeable {

    public static final String BIND_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 7777;
    private static final Logger log = LoggerFactory.getLogger(GravedigServer.class);

    private final int configuredPort;
    private final DatabaseManager databaseManager;
    private final AuthManager authManager;
    private final ScanService scanService;
    private Javalin app;
    private int actualPort;

    public GravedigServer() {
        this(DEFAULT_PORT, DatabaseManager.createDefault(), new AuthManager());
    }

    public GravedigServer(int port, DatabaseManager db, AuthManager auth) {
        this.configuredPort = port;
        this.databaseManager = db;
        this.authManager = auth;

        ScanDao scanDao = new ScanDao(databaseManager);
        FindingDao findingDao = new FindingDao(databaseManager);
        this.scanService = new ScanService(scanDao, findingDao);
    }

    public synchronized GravedigServer start() {
        if (app != null) {
            return this;
        }

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jsonMapper(new JavalinJackson(mapper, false));

            // Static assets for frontend when bundled on classpath
            if (getClass().getResource("/public/index.html") != null) {
                config.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = "/public";
                    staticFiles.location = Location.CLASSPATH;
                });
                config.spaRoot.addFile("/", "/public/index.html", Location.CLASSPATH);
            }
        });

        // Security check for /api endpoints
        app.before("/api/*", authManager.createAuthHandler());

        // Controllers
        ScanController scanController = new ScanController(scanService);
        RuleController ruleController = new RuleController(scanService);
        ScanWebSocketHandler wsHandler = new ScanWebSocketHandler(scanService, authManager);

        // REST API routes
        app.get("/api/health", ctx -> ctx.json(Map.of("status", "UP", "version", "0.1.0")));
        app.get("/api/auth/validate", ctx -> ctx.json(Map.of("authenticated", true)));
        app.post("/api/scans", scanController::startScan);
        app.get("/api/scans", scanController::listScans);
        app.get("/api/scans/{id}", scanController::getScan);
        app.get("/api/scans/{id}/findings", scanController::getFindings);
        app.patch("/api/findings/{id}", scanController::patchFinding);
        app.get("/api/scans/{id}/export", scanController::exportScan);

        app.get("/api/rules", ruleController::listRules);
        app.post("/api/rules/test", ruleController::testRule);

        // WebSocket route
        app.ws("/ws/scan/{id}", wsHandler::configure);

        // Strictly bind to 127.0.0.1
        app.start(BIND_HOST, configuredPort);
        this.actualPort = app.port();

        log.info("gravedig server started on http://{}:{}", BIND_HOST, actualPort);
        return this;
    }

    public synchronized void stop() {
        if (app != null) {
            app.stop();
            app = null;
        }
        if (scanService != null) {
            scanService.close();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public int getPort() {
        return actualPort > 0 ? actualPort : configuredPort;
    }

    public String getHost() {
        return BIND_HOST;
    }

    public String getToken() {
        return authManager.getToken();
    }

    public String getBaseUrl() {
        return "http://" + BIND_HOST + ":" + getPort();
    }

    public String getAuthenticatedUrl() {
        return getBaseUrl() + "/?token=" + getToken();
    }

    public ScanService getScanService() {
        return scanService;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    @Override
    public void close() {
        stop();
    }
}
