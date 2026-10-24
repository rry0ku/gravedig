package io.github.gravedig.core.formatter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.gravedig.core.model.ScanResult;

import java.io.PrintStream;

public class JsonOutputFormatter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static String format(ScanResult result) {
        try {
            return MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    public static void print(ScanResult result, PrintStream out) {
        out.println(format(result));
    }
}
