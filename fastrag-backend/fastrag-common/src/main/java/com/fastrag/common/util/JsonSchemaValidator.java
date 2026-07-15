package com.fastrag.common.util;

import java.util.*;
import java.util.stream.Collectors;

public class JsonSchemaValidator {

    public static ValidationResult validate(Map<String, Object> data, Map<String, Object> schema) {
        List<String> errors = new ArrayList<>();
        if (schema == null) return new ValidationResult(true, List.of());
        if (data == null) data = Map.of();

        // Check required fields
        List<String> required = (List<String>) schema.getOrDefault("required", List.of());
        for (String field : required) {
            if (!data.containsKey(field) || data.get(field) == null) {
                errors.add("Missing required field: " + field);
            }
        }

        // Check each provided field against schema
        Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();
            Map<String, Object> propSchema = (Map<String, Object>) properties.get(fieldName);
            if (propSchema != null) {
                validateField(fieldName, value, propSchema, errors);
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    @SuppressWarnings("unchecked")
    private static void validateField(String name, Object value, Map<String, Object> schema, List<String> errors) {
        String expectedType = (String) schema.getOrDefault("type", "string");
        // Type check
        if (value != null) {
            if ("string".equals(expectedType) && !(value instanceof String)) {
                errors.add(name + ": expected string, got " + value.getClass().getSimpleName());
            } else if ("number".equals(expectedType) && !(value instanceof Number)) {
                errors.add(name + ": expected number, got " + value.getClass().getSimpleName());
            } else if ("integer".equals(expectedType) && !(value instanceof Integer) && !(value instanceof Long)) {
                errors.add(name + ": expected integer, got " + value.getClass().getSimpleName());
            } else if ("boolean".equals(expectedType) && !(value instanceof Boolean)) {
                errors.add(name + ": expected boolean, got " + value.getClass().getSimpleName());
            } else if ("array".equals(expectedType) && !(value instanceof List)) {
                errors.add(name + ": expected array, got " + value.getClass().getSimpleName());
            }
        }
        // Enum check
        List<String> enumValues = (List<String>) schema.get("enum");
        if (enumValues != null && value != null) {
            if (!enumValues.contains(value.toString())) {
                errors.add(name + ": value '" + value + "' not in enum " + enumValues);
            }
        }
        // Minimum check
        if (schema.containsKey("minimum") && value instanceof Number) {
            double min = ((Number) schema.get("minimum")).doubleValue();
            if (((Number) value).doubleValue() < min) {
                errors.add(name + ": value " + value + " is less than minimum " + min);
            }
        }
        // Maximum check
        if (schema.containsKey("maximum") && value instanceof Number) {
            double max = ((Number) schema.get("maximum")).doubleValue();
            if (((Number) value).doubleValue() > max) {
                errors.add(name + ": value " + value + " exceeds maximum " + max);
            }
        }
    }

    public record ValidationResult(boolean valid, List<String> errors) {}
}
