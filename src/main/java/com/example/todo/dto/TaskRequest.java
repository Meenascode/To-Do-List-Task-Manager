package com.example.todo.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for creating or updating a task. On update, a missing status/priority/tags keeps the old value;
 * a missing dueDate or categoryId clears them. parentId is only used on creation.
 */
public record TaskRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must be at most 200 characters.")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters.")
        String description,

        String status,
        String priority,
        String dueDate,
        Long categoryId,
        List<String> tags,
        Long parentId) {
}
