package io.github.gravedig.server.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NativeAppWindowTest {

    @Test
    @DisplayName("isSupported does not throw and returns consistent boolean")
    void testIsSupported() {
        assertThatCode(() -> {
            boolean supported = NativeAppWindow.isSupported();
            assertThat(NativeAppWindow.isSupported()).isEqualTo(supported);
        }).doesNotThrowAnyException();
    }
}
