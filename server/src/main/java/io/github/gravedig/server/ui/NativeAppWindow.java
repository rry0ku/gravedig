package io.github.gravedig.server.ui;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.GraphicsEnvironment;

public class NativeAppWindow {

    private static final Logger log = LoggerFactory.getLogger(NativeAppWindow.class);

    public interface GtkLib extends Library {
        void gtk_init(int argc, String[] argv);
        Pointer gtk_window_new(int type);
        void gtk_window_set_title(Pointer window, String title);
        void gtk_window_set_default_size(Pointer window, int width, int height);
        void gtk_window_set_position(Pointer window, int position);
        void gtk_container_add(Pointer container, Pointer widget);
        void gtk_widget_show_all(Pointer widget);
        void gtk_main();
        void gtk_main_quit();
    }

    public interface GObjectLib extends Library {
        long g_signal_connect_data(Pointer instance, String detailedSignal, Callback handler, Pointer data, Pointer destroyData, int connectFlags);
    }

    public interface WebKitLib extends Library {
        Pointer webkit_web_view_new();
        void webkit_web_view_load_uri(Pointer webView, String uri);
        Pointer webkit_web_view_get_settings(Pointer webView);
        void webkit_settings_set_enable_developer_extras(Pointer settings, boolean enabled);
        void webkit_settings_set_enable_javascript(Pointer settings, boolean enabled);
    }

    public interface DestroyCallback extends Callback {
        void invoke(Pointer widget, Pointer data);
    }

    private static GtkLib gtk;
    private static GObjectLib gobject;
    private static WebKitLib webkit;
    private static Boolean supported;

    public static synchronized boolean isSupported() {
        if (supported != null) {
            return supported;
        }

        if (GraphicsEnvironment.isHeadless()) {
            log.debug("Headless environment detected; embedded native window disabled");
            supported = false;
            return false;
        }

        try {
            gtk = Native.load("gtk-3", GtkLib.class);
            gobject = Native.load("gobject-2.0", GObjectLib.class);

            // Try WebKitGTK 4.1, fallback to 4.0 or 6.0
            try {
                webkit = Native.load("webkit2gtk-4.1", WebKitLib.class);
            } catch (Throwable t1) {
                try {
                    webkit = Native.load("webkit2gtk-4.0", WebKitLib.class);
                } catch (Throwable t2) {
                    webkit = Native.load("webkitgtk-6.0", WebKitLib.class);
                }
            }

            supported = true;
            log.info("Native desktop WebKitGTK window supported and initialized");
            return true;
        } catch (Throwable t) {
            log.debug("Native WebKitGTK window not available: {}", t.getMessage());
            supported = false;
            return false;
        }
    }

    public static void open(String url, String title, int width, int height, Runnable onClose) {
        if (!isSupported()) {
            throw new UnsupportedOperationException("Native desktop window is not supported on this platform/environment");
        }

        try {
            gtk.gtk_init(0, null);

            // GTK_WINDOW_TOPLEVEL = 0
            Pointer window = gtk.gtk_window_new(0);
            gtk.gtk_window_set_title(window, title);
            gtk.gtk_window_set_default_size(window, width > 0 ? width : 1280, height > 0 ? height : 850);
            // GTK_WIN_POS_CENTER = 1
            gtk.gtk_window_set_position(window, 1);

            // Create WebKit Web View
            Pointer webView = webkit.webkit_web_view_new();
            Pointer settings = webkit.webkit_web_view_get_settings(webView);
            if (settings != null) {
                webkit.webkit_settings_set_enable_javascript(settings, true);
                webkit.webkit_settings_set_enable_developer_extras(settings, true);
            }

            // Load application URL
            webkit.webkit_web_view_load_uri(webView, url);

            // Handle window close
            DestroyCallback destroyCallback = (w, d) -> {
                log.info("Desktop application window closed by user");
                gtk.gtk_main_quit();
                if (onClose != null) {
                    onClose.run();
                }
            };
            gobject.g_signal_connect_data(window, "destroy", destroyCallback, null, null, 0);

            gtk.gtk_container_add(window, webView);
            gtk.gtk_widget_show_all(window);

            log.info("Entered native desktop window loop for: {}", url);
            gtk.gtk_main();

        } catch (Exception e) {
            log.error("Error running native application window: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to run native desktop window", e);
        }
    }
}
