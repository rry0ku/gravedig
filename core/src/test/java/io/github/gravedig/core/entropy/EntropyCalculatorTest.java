package io.github.gravedig.core.entropy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntropyCalculatorTest {

    @Test
    @DisplayName("Should return 0 entropy for empty or single char string")
    void testZeroEntropy() {
        assertThat(EntropyCalculator.calculate("")).isEqualTo(0.0);
        assertThat(EntropyCalculator.calculate("aaaaaaa")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should calculate high entropy for random base64 secret")
    void testHighEntropy() {
        // High randomness token
        String highEntropySecret = "dGhpc0lzQVNlY3JldEtleVdpdGhIaWdoRW50cm9weTEyMzQ1Njc4OTo7PD4=";
        double entropy = EntropyCalculator.calculate(highEntropySecret);
        assertThat(entropy).isGreaterThan(4.5);
    }

    @Test
    @DisplayName("Should calculate moderate entropy for repetitive patterns")
    void testLowEntropy() {
        String repetitive = "abcabcabcabcabc";
        double entropy = EntropyCalculator.calculate(repetitive);
        assertThat(entropy).isLessThan(2.0);
    }

    @Test
    @DisplayName("Should verify character diversity")
    void testDiversity() {
        assertThat(EntropyCalculator.hasSufficientDiversity("abcdefghij", 6)).isTrue();
        assertThat(EntropyCalculator.hasSufficientDiversity("aaaabbbb", 6)).isFalse();
    }
}
