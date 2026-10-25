package com.example.todo.model;

import java.time.LocalDateTime;

/** One line of the activity timeline. taskId has no foreign key so entries survive task deletion. */
public record HistoryEntry(long id, Long taskId, String taskTitle, String eventType,
                           String description, LocalDateTime createdAt) {
}
