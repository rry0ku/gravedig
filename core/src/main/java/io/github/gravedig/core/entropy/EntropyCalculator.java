package io.github.gravedig.core.entropy;

import java.util.HashMap;
import java.util.Map;

public final class EntropyCalculator {

    private EntropyCalculator() {
    }

    /**
     * Calculates Shannon entropy for the given string:
     * H = - sum(p_i * log2(p_i))
     */
    public static double calculate(String input) {
        if (input == null || input.isEmpty()) {
            return 0.0;
        }

        Map<Character, Integer> frequencies = new HashMap<>();
        for (char c : input.toCharArray()) {
            frequencies.merge(c, 1, Integer::sum);
        }

        double length = input.length();
        double entropy = 0.0;
        double log2 = Math.log(2);

        for (int count : frequencies.values()) {
            double p = count / length;
            entropy -= p * (Math.log(p) / log2);
        }

        return entropy;
    }

    /**
     * Checks if input has sufficient character diversity.
     */
    public static boolean hasSufficientDiversity(String input, int minDistinctChars) {
        if (input == null || input.length() < minDistinctChars) {
            return false;
        }
        long distinct = input.chars().distinct().count();
        return distinct >= minDistinctChars;
    }
}
