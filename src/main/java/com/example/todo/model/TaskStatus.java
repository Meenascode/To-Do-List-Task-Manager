package com.example.todo.model;

import java.util.Locale;

import com.example.todo.exception.ApiException;

public enum TaskStatus {
    TODO("To Do"), IN_PROGRESS("In Progress"), COMPLETED("Completed");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Accepts TODO, in_progress, "In Progress", etc. Anything else is a 400 error. */
    public static TaskStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Status is required.");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (TaskStatus s : values()) {
            if (s.name().equals(normalized)) {
                return s;
            }
        }
        throw ApiException.badRequest("Invalid status. Allowed values: TODO, IN_PROGRESS, COMPLETED.");
    }
}
