package io.github.gravedig.cli;

import io.github.gravedig.server.GravedigServer;
import io.github.gravedig.server.db.DatabaseManager;
import io.github.gravedig.server.security.AuthManager;
import io.github.gravedig.server.ui.NativeAppWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

@Command(
        name = "ui",
        description = "Launch the local gravedig desktop GUI application on 127.0.0.1:7777",
        mixinStandardHelpOptions = true
)
public class UiCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(UiCommand.class);

    @Option(
            names = {"-p", "--port"},
            description = "Port to listen on (default: 7777)",
            defaultValue = "7777"
    )
    private int port;

    @Option(
            names = {"--no-window", "--headless"},
            description = "Run headless server mode without opening the application window"
    )
    private boolean headlessMode;

    @Option(
            names = {"--browser"},
            description = "Open in external system web browser instead of the embedded desktop window"
    )
    private boolean openExternalBrowser;

    @Option(
            names = {"--no-browser"},
            description = "Alias for headless mode (do not open window or browser)"
    )
    private boolean noBrowser;

    @Option(
            names = {"--db"},
            description = "Path to custom SQLite database file"
    )
    private String dbPath;

    @Option(
            names = {"-t", "--token"},
            description = "Custom secret authentication token (default: securely auto-generated)"
    )
    private String customToken;

    @Override
    public Integer call() {
        DatabaseManager dbManager = resolveDatabaseManager();
        AuthManager authManager = (customToken != null && !customToken.isBlank())
                ? new AuthManager(customToken.trim())
                : new AuthManager();

        GravedigServer server = new GravedigServer(port, dbManager, authManager);

        try {
            server.start();

            String fullUrl = server.getAuthenticatedUrl();
            printBanner(server.getPort(), server.getToken(), fullUrl);

            if (openExternalBrowser) {
                openBrowser(fullUrl);
                waitForShutdown(server);
            } else if (!headlessMode && !noBrowser && NativeAppWindow.isSupported()) {
                System.out.println("  Opening Gravedig native desktop application window...\n");
                NativeAppWindow.open(
                        fullUrl,
                        "Gravedig — Dig up the secrets your git history forgot to bury",
                        1320,
                        880,
                        server::stop
                );
            } else {
                if (!headlessMode && !noBrowser) {
                    System.out.println("  Native window not supported in current environment; running headless on " + fullUrl);
                }
                waitForShutdown(server);
            }

            server.stop();
            return 0;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.stop();
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to start gravedig server: " + e.getMessage());
            log.error("Server failure", e);
            server.stop();
            return 1;
        }
    }

    private void waitForShutdown(GravedigServer server) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down gravedig server...");
            server.stop();
            latch.countDown();
        }));
        latch.await();
    }

    private DatabaseManager resolveDatabaseManager() {
        if (dbPath != null && !dbPath.isBlank()) {
            String path = dbPath.trim();
            if (":memory:".equalsIgnoreCase(path)) {
                return DatabaseManager.createInMemory();
            }
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            return new DatabaseManager("jdbc:sqlite:" + file.getAbsolutePath());
        }
        return DatabaseManager.createDefault();
    }

    private void printBanner(int actualPort, String authToken, String fullUrl) {
        System.out.println();
        System.out.println("  \u001B[33m┌─────────────────────────────────────────────────────────────┐\u001B[0m");
        System.out.println("  \u001B[33m│\u001B[0m  \u001B[1mgravedig Desktop Application Active\u001B[0m                       \u001B[33m│\u001B[0m");
        System.out.println("  \u001B[33m│\u001B[0m  \"Dig up the secrets your git history forgot to bury.\"      \u001B[33m│\u001B[0m");
        System.out.println("  \u001B[33m├─────────────────────────────────────────────────────────────┤\u001B[0m");
        System.out.printf("  \u001B[33m│\u001B[0m  Local URL:  \u001B[36m%s\u001B[0m\n", fullUrl);
        System.out.printf("  \u001B[33m│\u001B[0m  Host:       127.0.0.1 (Strict Localhost)\n");
        System.out.printf("  \u001B[33m│\u001B[0m  Port:       %d\n", actualPort);
        System.out.printf("  \u001B[33m│\u001B[0m  Auth Token: \u001B[32m%s\u001B[0m\n", authToken);
        System.out.println("  \u001B[33m├─────────────────────────────────────────────────────────────┤\u001B[0m");
        System.out.println("  \u001B[33m│\u001B[0m  Close desktop window or press \u001B[1mCtrl+C\u001B[0m to exit              \u001B[33m│\u001B[0m");
        System.out.println("  \u001B[33m└─────────────────────────────────────────────────────────────┘\u001B[0m");
        System.out.println();
    }

    private void openBrowser(String url) {
        try {
            if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(new URI(url));
                }
            }
        } catch (Exception e) {
            log.debug("Auto-browser opening unavailable: {}", e.getMessage());
        }
    }
}
