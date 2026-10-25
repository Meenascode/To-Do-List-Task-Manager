package com.example.todo.model;

import java.util.Locale;

import com.example.todo.exception.ApiException;

public enum TaskPriority {
    LOW("Low"), MEDIUM("Medium"), HIGH("High"), URGENT("Urgent");

    private final String label;

    TaskPriority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static TaskPriority parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Priority is required.");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (TaskPriority p : values()) {
            if (p.name().equals(normalized)) {
                return p;
            }
        }
        throw ApiException.badRequest("Invalid priority. Allowed values: LOW, MEDIUM, HIGH, URGENT.");
    }
}
