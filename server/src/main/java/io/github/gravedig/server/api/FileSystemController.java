package io.github.gravedig.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gravedig.server.ui.NativeFolderChooser;
import io.javalin.http.Context;

import java.awt.Desktop;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FileSystemController {

    private final ObjectMapper mapper = new ObjectMapper();

    public void chooseFolder(Context ctx) {
        String initialPath = null;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode node = mapper.readTree(ctx.body());
                if (node.has("initialPath")) {
                    initialPath = node.get("initialPath").asText(null);
                }
            }
        } catch (Exception ignored) {}

        if (!NativeFolderChooser.isSupported()) {
            ctx.json(Map.of(
                "success", false,
                "selected", false,
                "message", "Folder chooser dialog is not supported in headless environments."
            ));
            return;
        }

        try {
            Optional<String> chosen = NativeFolderChooser.chooseFolder(initialPath);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            if (chosen.isPresent()) {
                resp.put("selected", true);
                resp.put("path", chosen.get());
            } else {
                resp.put("selected", false);
                resp.put("path", null);
            }
            ctx.json(resp);
        } catch (Exception e) {
            ctx.json(Map.of(
                "success", false,
                "selected", false,
                "message", "Failed to open folder chooser: " + e.getMessage()
            ));
        }
    }

    public void openFile(Context ctx) {
        String path = null;
        try {
            if (ctx.body() != null && !ctx.body().isBlank()) {
                JsonNode node = mapper.readTree(ctx.body());
                if (node.has("path")) {
                    path = node.get("path").asText(null);
                }
            }
        } catch (Exception ignored) {}

        if (path == null || path.isBlank()) {
            ctx.status(400).json(Map.of("success", false, "message", "Path parameter is required"));
            return;
        }

        File file = new File(path);
        if (!file.exists()) {
            ctx.status(404).json(Map.of("success", false, "message", "File does not exist: " + path));
            return;
        }

        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file);
                ctx.json(Map.of("success", true, "message", "Opened file: " + path));
                return;
            }
        } catch (Exception e) {
            ctx.json(Map.of("success", false, "message", "Unable to open file: " + e.getMessage()));
            return;
        }

        ctx.json(Map.of("success", false, "message", "Desktop open action not supported in this environment"));
    }
}
