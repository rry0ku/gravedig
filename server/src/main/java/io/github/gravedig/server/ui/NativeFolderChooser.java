package io.github.gravedig.server.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class NativeFolderChooser {

    private static final Logger log = LoggerFactory.getLogger(NativeFolderChooser.class);

    public static boolean isSupported() {
        if (Boolean.getBoolean("gravedig.headless") || Boolean.getBoolean("gravedig.test.skipDialog")) {
            return false;
        }
        return !GraphicsEnvironment.isHeadless();
    }

    public static Optional<String> chooseFolder(String initialPath) {
        if (!isSupported()) {
            log.warn("Cannot show folder chooser dialog: environment is headless");
            return Optional.empty();
        }

        // If GTK native window is active, run modal GTK file chooser via g_idle_add
        if (NativeAppWindow.getMainWindow() != null && NativeAppWindow.getGtk() != null && NativeAppWindow.getGLib() != null) {
            try {
                return chooseFolderGtk(initialPath);
            } catch (Exception e) {
                log.warn("GTK folder chooser failed, falling back to Swing", e);
            }
        }

        // Otherwise fallback to Swing JFileChooser (for browser mode or non-GTK environments)
        return chooseFolderSwing(initialPath);
    }

    private static Optional<String> chooseFolderGtk(String initialPath) throws Exception {
        CompletableFuture<Optional<String>> future = new CompletableFuture<>();
        NativeAppWindow.GSourceFunc func = data -> {
            try {
                var gtk = NativeAppWindow.getGtk();
                var parent = NativeAppWindow.getMainWindow();
                // GTK_FILE_CHOOSER_ACTION_SELECT_FOLDER = 2
                var dialog = gtk.gtk_file_chooser_dialog_new("Select Target Folder to Scan", parent, 2, null);
                gtk.gtk_dialog_add_button(dialog, "_Cancel", -6); // GTK_RESPONSE_CANCEL = -6
                gtk.gtk_dialog_add_button(dialog, "_Select Folder", -3); // GTK_RESPONSE_ACCEPT = -3
                gtk.gtk_window_set_keep_above(dialog, true);

                if (initialPath != null && !initialPath.isBlank()) {
                    File f = new File(initialPath);
                    if (f.exists()) {
                        gtk.gtk_file_chooser_set_current_folder(dialog, f.isDirectory() ? f.getAbsolutePath() : f.getParent());
                    }
                }

                int res = gtk.gtk_dialog_run(dialog);
                String chosen = null;
                if (res == -3) { // GTK_RESPONSE_ACCEPT
                    chosen = gtk.gtk_file_chooser_get_filename(dialog);
                }
                gtk.gtk_widget_destroy(dialog);
                future.complete(Optional.ofNullable(chosen));
            } catch (Throwable t) {
                log.error("Error displaying GTK file chooser dialog", t);
                future.completeExceptionally(t);
            }
            return false; // remove source from GLib main loop
        };

        NativeAppWindow.getGLib().g_idle_add(func, com.sun.jna.Pointer.NULL);
        return future.get(5, TimeUnit.MINUTES);
    }

    private static Optional<String> chooseFolderSwing(String initialPath) {
        CompletableFuture<Optional<String>> future = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Throwable ignored) {}

                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Select Target Repository or Folder to Scan");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);

                if (initialPath != null && !initialPath.isBlank()) {
                    File f = new File(initialPath);
                    if (f.exists()) {
                        chooser.setCurrentDirectory(f.isDirectory() ? f : f.getParentFile());
                    }
                }

                int result = chooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                    future.complete(Optional.of(chooser.getSelectedFile().getAbsolutePath()));
                } else {
                    future.complete(Optional.empty());
                }
            } catch (Throwable t) {
                log.error("Error displaying Swing file chooser dialog", t);
                future.complete(Optional.empty());
            }
        });

        try {
            return future.get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Swing folder chooser timed out or interrupted: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
