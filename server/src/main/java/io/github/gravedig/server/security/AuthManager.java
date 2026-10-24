package io.github.gravedig.server.security;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Map;

public class AuthManager {

    private static final Logger log = LoggerFactory.getLogger(AuthManager.class);
    private final String token;

    public AuthManager() {
        this(generateSecureToken());
    }

    public AuthManager(String token) {
        if (token == null || token.isBlank()) {
            this.token = generateSecureToken();
        } else {
            this.token = token.trim();
        }
    }

    public static String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public String getToken() {
        return token;
    }

    public boolean isValid(String candidate) {
        if (candidate == null) {
            return false;
        }
        return token.equals(candidate.trim());
    }

    public boolean authenticate(Context ctx) {
        // 1. Header X-Auth-Token
        String headerToken = ctx.header("X-Auth-Token");
        if (isValid(headerToken)) {
            return true;
        }

        // 2. Header Authorization: Bearer <token>
        String authHeader = ctx.header("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String bearer = authHeader.substring(7);
            if (isValid(bearer)) {
                return true;
            }
        }

        // 3. Query param token
        String queryToken = ctx.queryParam("token");
        return isValid(queryToken);
    }

    public Handler createAuthHandler() {
        return ctx -> {
            String path = ctx.path();
            // Protect all /api/ endpoints except /api/health
            if (path.startsWith("/api")) {
                if ("/api/health".equals(path)) {
                    return;
                }
                if (!authenticate(ctx)) {
                    log.warn("Unauthorized request from {} to {}", ctx.ip(), path);
                    ctx.status(HttpStatus.UNAUTHORIZED)
                       .json(Map.of("error", "Unauthorized: missing or invalid authentication token"));
                }
            }
        };
    }
}
