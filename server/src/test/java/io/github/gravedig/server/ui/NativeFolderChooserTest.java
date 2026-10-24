package io.github.gravedig.server.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class NativeFolderChooserTest {

    @Test
    public void testIsSupportedDoesNotThrow() {
        assertDoesNotThrow(() -> {
            boolean supported = NativeFolderChooser.isSupported();
            System.out.println("NativeFolderChooser.isSupported() = " + supported);
        });
    }
}
