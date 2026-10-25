package com.example.todo.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A task row (or subtask when parentId != null) joined with its category and subtask counts. */
public record Task(long id, long userId, Long parentId, String title, String description,
                   TaskStatus status, TaskPriority priority, LocalDate dueDate,
                   Long categoryId, String categoryName, String categoryColor,
                   LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime completedAt,
                   int subtaskTotal, int subtaskDone) {
}
