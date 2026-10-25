package com.example.todo.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Task as sent to the browser. subtasks is only filled for the single-task (detail) endpoint. */
public record TaskView(long id, Long parentId, String title, String description,
                       String status, String priority, LocalDate dueDate,
                       Long categoryId, String categoryName, String categoryColor,
                       List<TagRef> tags, LocalDateTime createdAt, LocalDateTime updatedAt,
                       LocalDateTime completedAt, boolean overdue,
                       int subtaskTotal, int subtaskDone, List<TaskView> subtasks) {
}
