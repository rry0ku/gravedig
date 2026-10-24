package io.github.gravedig.server.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.gravedig.server.model.ScanEvent;
import io.github.gravedig.server.security.AuthManager;
import io.github.gravedig.server.service.ScanService;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsConnectContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ScanWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ScanWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final ScanService scanService;
    private final AuthManager authManager;
    private final Map<String, Consumer<ScanEvent>> sessionListeners = new ConcurrentHashMap<>();

    public ScanWebSocketHandler(ScanService scanService, AuthManager authManager) {
        this.scanService = scanService;
        this.authManager = authManager;
    }

    public void configure(WsConfig ws) {
        ws.onConnect(ctx -> {
            String token = ctx.queryParam("token");
            if (token == null) {
                token = ctx.header("X-Auth-Token");
            }
            if (token == null) {
                String authHeader = ctx.header("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            if (!authManager.isValid(token)) {
                log.warn("Unauthorized WebSocket handshake attempt for scan {}", ctx.pathParam("id"));
                ctx.closeSession(4001, "Unauthorized: missing or invalid token");
                return;
            }

            String scanId = ctx.pathParam("id");
            String sessionId = ctx.sessionId();
            log.info("WebSocket client connected to scan {} (session={})", scanId, sessionId);

            Consumer<ScanEvent> listener = event -> {
                try {
                    String json = MAPPER.writeValueAsString(event);
                    ctx.send(json);
                } catch (Exception e) {
                    log.warn("Failed sending WebSocket message to session {}", sessionId, e);
                }
            };

            sessionListeners.put(sessionId, listener);
            scanService.addListener(scanId, listener);

            // Send current status or initial progress
            scanService.getScan(scanId).ifPresentOrElse(record -> {
                if ("COMPLETED".equals(record.getStatus())) {
                    listener.accept(ScanEvent.completed(scanId, record.getSummary()));
                } else if ("FAILED".equals(record.getStatus())) {
                    listener.accept(ScanEvent.error(scanId, record.getErrorMessage()));
                } else {
                    listener.accept(ScanEvent.progress(scanId, record.getTotalFiles(), record.getTotalCommits(), record.getTotalFindings()));
                }
            }, () -> {
                listener.accept(ScanEvent.progress(scanId, 0, 0, 0));
            });
        });

        ws.onClose(ctx -> {
            String scanId = ctx.pathParam("id");
            String sessionId = ctx.sessionId();
            Consumer<ScanEvent> listener = sessionListeners.remove(sessionId);
            if (listener != null) {
                scanService.removeListener(scanId, listener);
            }
            log.info("WebSocket client disconnected from scan {} (session={})", scanId, sessionId);
        });

        ws.onError(ctx -> {
            String scanId = ctx.pathParam("id");
            String sessionId = ctx.sessionId();
            log.warn("WebSocket error on scan {} (session={})", scanId, sessionId, ctx.error());
            Consumer<ScanEvent> listener = sessionListeners.remove(sessionId);
            if (listener != null) {
                scanService.removeListener(scanId, listener);
            }
        });
    }
}
