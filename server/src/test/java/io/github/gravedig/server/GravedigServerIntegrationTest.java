package io.github.gravedig.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gravedig.server.db.DatabaseManager;
import io.github.gravedig.server.security.AuthManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GravedigServerIntegrationTest {

    private static final String TEST_TOKEN = "test-secret-token-1234567890ab";
    private static GravedigServer server;
    private static HttpClient client;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    static Path tempDir;

    @BeforeAll
    static void startServer() throws Exception {
        // Create sample leaked file in tempDir
        Path leakedFile = tempDir.resolve("secret.env");
        Files.writeString(leakedFile, "AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE\n");

        DatabaseManager db = DatabaseManager.createInMemory();
        AuthManager auth = new AuthManager(TEST_TOKEN);
        server = new GravedigServer(0, db, auth);
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("GET /api/health succeeds without authentication")
    void testHealthEndpoint() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/health"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);
        JsonNode json = MAPPER.readTree(resp.body());
        assertThat(json.get("status").asText()).isEqualTo("UP");
    }

    @Test
    @DisplayName("Protected endpoint rejects request without token with 401")
    void testUnauthorizedRequest() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/rules"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Protected endpoint succeeds with X-Auth-Token header")
    void testAuthorizedRequestWithHeader() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/rules"))
                .header("X-Auth-Token", TEST_TOKEN)
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);
        JsonNode rules = MAPPER.readTree(resp.body());
        assertThat(rules.isArray()).isTrue();
        assertThat(rules.size()).isGreaterThanOrEqualTo(15);
    }

    @Test
    @DisplayName("POST /api/rules/test tests custom regex matching")
    void testRuleTestEndpoint() throws Exception {
        String payload = """
            {
                "pattern": "(AKIA[0-9A-Z]{16})",
                "sample": "Found AWS key AKIAIOSFODNN7BK5WP9Z in source"
            }
        """;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/rules/test"))
                .header("X-Auth-Token", TEST_TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);
        JsonNode json = MAPPER.readTree(resp.body());
        assertThat(json.get("matched").asBoolean()).isTrue();
        assertThat(json.get("matches").size()).isEqualTo(1);
        assertThat(json.get("matches").get(0).get("match").asText()).isEqualTo("AKIAIOSFODNN7BK5WP9Z");
    }

    @Test
    @DisplayName("Full scan lifecycle via REST API and exports")
    void testScanLifecycleAndExport() throws Exception {
        // 1. Launch scan
        String scanPayload = String.format("{\"path\": \"%s\", \"includeHistory\": false}", tempDir.toAbsolutePath());
        HttpRequest postScan = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/scans"))
                .header("X-Auth-Token", TEST_TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(scanPayload))
                .build();

        HttpResponse<String> postResp = client.send(postScan, HttpResponse.BodyHandlers.ofString());
        assertThat(postResp.statusCode()).isEqualTo(202);
        JsonNode scanRecord = MAPPER.readTree(postResp.body());
        String scanId = scanRecord.get("id").asText();
        assertThat(scanId).isNotBlank();

        // 2. Poll until scan completes
        boolean completed = false;
        for (int i = 0; i < 50; i++) {
            HttpRequest getScan = HttpRequest.newBuilder()
                    .uri(URI.create(server.getBaseUrl() + "/api/scans/" + scanId))
                    .header("X-Auth-Token", TEST_TOKEN)
                    .GET()
                    .build();
            HttpResponse<String> getResp = client.send(getScan, HttpResponse.BodyHandlers.ofString());
            JsonNode statusNode = MAPPER.readTree(getResp.body());
            if ("COMPLETED".equals(statusNode.get("status").asText())) {
                completed = true;
                break;
            }
            Thread.sleep(100);
        }
        assertThat(completed).isTrue();

        // 3. Query findings
        HttpRequest getFindings = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/scans/" + scanId + "/findings"))
                .header("X-Auth-Token", TEST_TOKEN)
                .GET()
                .build();
        HttpResponse<String> findingsResp = client.send(getFindings, HttpResponse.BodyHandlers.ofString());
        assertThat(findingsResp.statusCode()).isEqualTo(200);
        JsonNode findings = MAPPER.readTree(findingsResp.body());
        assertThat(findings.isArray()).isTrue();

        // 4. Test Export JSON
        HttpRequest exportJson = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/scans/" + scanId + "/export?format=json"))
                .header("X-Auth-Token", TEST_TOKEN)
                .GET()
                .build();
        HttpResponse<String> jsonResp = client.send(exportJson, HttpResponse.BodyHandlers.ofString());
        assertThat(jsonResp.statusCode()).isEqualTo(200);
        assertThat(jsonResp.body()).contains("summary");

        // 5. Test Export SARIF
        HttpRequest exportSarif = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/scans/" + scanId + "/export?format=sarif"))
                .header("X-Auth-Token", TEST_TOKEN)
                .GET()
                .build();
        HttpResponse<String> sarifResp = client.send(exportSarif, HttpResponse.BodyHandlers.ofString());
        assertThat(sarifResp.statusCode()).isEqualTo(200);
        assertThat(sarifResp.body()).contains("https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json");

        // 6. Test Export HTML
        HttpRequest exportHtml = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/scans/" + scanId + "/export?format=html"))
                .header("X-Auth-Token", TEST_TOKEN)
                .GET()
                .build();
        HttpResponse<String> htmlResp = client.send(exportHtml, HttpResponse.BodyHandlers.ofString());
        assertThat(htmlResp.statusCode()).isEqualTo(200);
        assertThat(htmlResp.body()).contains("<!DOCTYPE html>");

        // 7. Test Export CSV
        HttpRequest exportCsv = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/api/scans/" + scanId + "/export?format=csv"))
                .header("X-Auth-Token", TEST_TOKEN)
                .GET()
                .build();
        HttpResponse<String> csvResp = client.send(exportCsv, HttpResponse.BodyHandlers.ofString());
        assertThat(csvResp.statusCode()).isEqualTo(200);
        assertThat(csvResp.body()).contains("id,rule_id,rule_name");
    }

    @Test
    @DisplayName("WebSocket connects with token and receives status update")
    void testWebSocketConnection() throws Exception {
        CompletableFuture<String> messageReceived = new CompletableFuture<>();

        URI wsUri = URI.create("ws://127.0.0.1:" + server.getPort() + "/ws/scan/test-ws-scan?token=" + TEST_TOKEN);
        CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
                .buildAsync(wsUri, new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        messageReceived.complete(data.toString());
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }
                });

        WebSocket ws = wsFuture.get(5, TimeUnit.SECONDS);
        assertThat(ws).isNotNull();

        // Broadcast or initial progress event received
        String message = messageReceived.get(5, TimeUnit.SECONDS);
        assertThat(message).contains("PROGRESS");
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "Done");
    }

    @Test
    @DisplayName("Bundled Svelte GUI serves index.html and SPA routes")
    void testStaticFrontendServesIndexHtml() throws Exception {
        // 1. Root /
        HttpRequest reqRoot = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/"))
                .GET()
                .build();
        HttpResponse<String> respRoot = client.send(reqRoot, HttpResponse.BodyHandlers.ofString());
        assertThat(respRoot.statusCode()).isEqualTo(200);
        assertThat(respRoot.body()).contains("<div id=\"app\"></div>");
        assertThat(respRoot.body()).contains("Gravedig");

        // 2. SPA routing fallback on arbitrary unmapped path
        HttpRequest reqSpa = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + "/dashboard"))
                .GET()
                .build();
        HttpResponse<String> respSpa = client.send(reqSpa, HttpResponse.BodyHandlers.ofString());
        assertThat(respSpa.statusCode()).isEqualTo(200);
        assertThat(respSpa.body()).contains("<div id=\"app\"></div>");

        // 3. Static JS/CSS assets
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("src=\"(/assets/[^\"]+)\"").matcher(respRoot.body());
        assertThat(m.find()).isTrue();
        String assetPath = m.group(1);
        HttpRequest reqAsset = HttpRequest.newBuilder()
                .uri(URI.create(server.getBaseUrl() + assetPath))
                .GET()
                .build();
        HttpResponse<String> respAsset = client.send(reqAsset, HttpResponse.BodyHandlers.ofString());
        assertThat(respAsset.statusCode()).isEqualTo(200);
        assertThat(respAsset.body()).contains("gravedig");
    }
}
