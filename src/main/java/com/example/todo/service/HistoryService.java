package com.example.todo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.todo.exception.ApiException;
import com.example.todo.model.HistoryEntry;
import com.example.todo.repository.HistoryRepository;
import com.example.todo.repository.TaskRepository;

@Service
public class HistoryService {

    private final HistoryRepository history;
    private final TaskRepository tasks;

    public HistoryService(HistoryRepository history, TaskRepository tasks) {
        this.history = history;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> recent(long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return history.findRecent(userId, safeLimit);
    }

    /** History of one task. Still works after the task was deleted, but only for the owner. */
    @Transactional(readOnly = true)
    public List<HistoryEntry> forTask(long userId, long taskId) {
        List<HistoryEntry> entries = history.findByTask(userId, taskId);
        if (entries.isEmpty() && tasks.findOwned(taskId, userId).isEmpty()) {
            throw ApiException.notFound("Task not found.");
        }
        return entries;
    }
}
