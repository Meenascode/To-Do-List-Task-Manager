package com.example.todo.dto;

import com.example.todo.model.TaskPriority;
import com.example.todo.model.TaskStatus;

/** Already-validated search options. due is one of: today, overdue, week, none or an ISO date. */
public record TaskFilter(String q, TaskStatus status, TaskPriority priority, Long categoryId, Long tagId,
                         String due, String sort, String dir, boolean includeSubtasks) {
}
