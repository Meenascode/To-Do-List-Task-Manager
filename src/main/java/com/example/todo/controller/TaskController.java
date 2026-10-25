package com.example.todo.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.todo.dto.StatsView;
import com.example.todo.dto.StatusRequest;
import com.example.todo.dto.TaskRequest;
import com.example.todo.dto.TaskView;
import com.example.todo.model.HistoryEntry;
import com.example.todo.security.AuthUser;
import com.example.todo.service.HistoryService;
import com.example.todo.service.StatsService;
import com.example.todo.service.TaskService;

import jakarta.validation.Valid;

/** Tasks, subtasks, Kanban status updates, calendar data, statistics and per-task history. */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final StatsService statsService;
    private final HistoryService historyService;

    public TaskController(TaskService taskService, StatsService statsService, HistoryService historyService) {
        this.taskService = taskService;
        this.statsService = statsService;
        this.historyService = historyService;
    }

    @GetMapping
    public List<TaskView> list(@AuthenticationPrincipal AuthUser user,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String due,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam(defaultValue = "false") boolean includeSubtasks) {
        return taskService.search(user.getId(), q, status, priority, categoryId, tagId, due, sort, dir,
                includeSubtasks);
    }

    @GetMapping("/stats")
    public StatsView stats(@AuthenticationPrincipal AuthUser user) {
        return statsService.compute(user.getId());
    }

    @GetMapping("/calendar")
    public List<TaskView> calendar(@AuthenticationPrincipal AuthUser user,
            @RequestParam int year, @RequestParam int month) {
        return taskService.calendar(user.getId(), year, month);
    }

    @GetMapping("/{id}")
    public TaskView get(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        return taskService.get(user.getId(), id);
    }

    @PostMapping
    public ResponseEntity<TaskView> create(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.create(user.getId(), request, request.parentId()));
    }

    @PutMapping("/{id}")
    public TaskView update(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
            @Valid @RequestBody TaskRequest request) {
        return taskService.update(user.getId(), id, request);
    }

    /** Used by the Kanban board and the complete / reopen buttons. */
    @PatchMapping("/{id}/status")
    public TaskView changeStatus(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
            @Valid @RequestBody StatusRequest request) {
        return taskService.changeStatus(user.getId(), id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        taskService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    // ---- subtasks (a subtask is a task whose parent is another task) ----

    @GetMapping("/{id}/subtasks")
    public List<TaskView> subtasks(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        return taskService.get(user.getId(), id).subtasks();
    }

    @PostMapping("/{id}/subtasks")
    public ResponseEntity<TaskView> addSubtask(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
            @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(user.getId(), request, id));
    }

    @GetMapping("/{id}/history")
    public List<HistoryEntry> history(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        return historyService.forTask(user.getId(), id);
    }
}
