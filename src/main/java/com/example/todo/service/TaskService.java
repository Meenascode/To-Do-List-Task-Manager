package com.example.todo.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.todo.dto.TagRef;
import com.example.todo.dto.TaskFilter;
import com.example.todo.dto.TaskRequest;
import com.example.todo.dto.TaskView;
import com.example.todo.exception.ApiException;
import com.example.todo.model.Task;
import com.example.todo.model.TaskPriority;
import com.example.todo.model.TaskStatus;
import com.example.todo.repository.CategoryRepository;
import com.example.todo.repository.HistoryRepository;
import com.example.todo.repository.TagRepository;
import com.example.todo.repository.TaskRepository;

/**
 * Business rules for tasks and subtasks. Every method receives the authenticated user's id and every
 * lookup is made "by id AND user" - a task of another user behaves exactly like a missing task (404).
 */
@Service
public class TaskService {

    private static final Set<String> DUE_WORDS = Set.of("today", "overdue", "week", "none");
    private static final Set<String> SORT_KEYS = Set.of("dueDate", "priority", "createdAt", "updatedAt", "title");

    private final TaskRepository tasks;
    private final CategoryRepository categories;
    private final TagRepository tags;
    private final HistoryRepository history;
    private final Clock clock;

    public TaskService(TaskRepository tasks, CategoryRepository categories, TagRepository tags,
                       HistoryRepository history, Clock clock) {
        this.tasks = tasks;
        this.categories = categories;
        this.tags = tags;
        this.history = history;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public List<TaskView> search(long userId, String q, String status, String priority, Long categoryId,
                                 Long tagId, String due, String sort, String dir, boolean includeSubtasks) {
        TaskStatus st = isBlank(status) ? null : TaskStatus.parse(status);
        TaskPriority pr = isBlank(priority) ? null : TaskPriority.parse(priority);
        String sortKey = isBlank(sort) ? "createdAt" : sort.trim();
        if (!SORT_KEYS.contains(sortKey)) {
            throw ApiException.badRequest("Invalid sort. Allowed: dueDate, priority, createdAt, updatedAt, title.");
        }
        String direction = isBlank(dir) ? "desc" : dir.trim().toLowerCase(Locale.ROOT);
        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw ApiException.badRequest("Invalid direction. Use asc or desc.");
        }
        TaskFilter filter = new TaskFilter(q, st, pr, categoryId, tagId, normalizeDue(due), sortKey, direction,
                includeSubtasks);
        return toViews(tasks.search(userId, filter, today()));
    }

    @Transactional(readOnly = true)
    public TaskView get(long userId, long id) {
        Task task = findOwned(userId, id);
        return toViewWithSubtasks(task);
    }

    @Transactional(readOnly = true)
    public List<TaskView> calendar(long userId, int year, int month) {
        if (month < 1 || month > 12 || year < 1970 || year > 2200) {
            throw ApiException.badRequest("Invalid year or month.");
        }
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        return toViews(tasks.findDueBetween(userId, from, to));
    }

    @Transactional(readOnly = true)
    public List<TaskView> upcoming(long userId, int limit) {
        return toViews(tasks.findUpcoming(userId, today(), limit));
    }

    @Transactional(readOnly = true)
    public List<Task> findAllForExport(long userId) {
        return tasks.findAllForExport(userId);
    }

    // ------------------------------------------------------------------ create

    @Transactional
    public TaskView create(long userId, TaskRequest req, Long parentId) {
        TaskStatus status = isBlank(req.status()) ? TaskStatus.TODO : TaskStatus.parse(req.status());
        TaskPriority priority = isBlank(req.priority()) ? TaskPriority.MEDIUM : TaskPriority.parse(req.priority());
        LocalDate due = parseDate(req.dueDate());
        Long categoryId = validateCategory(userId, req.categoryId());
        List<String> tagNames = normalizeTags(req.tags());

        Task parent = null;
        if (parentId != null) {
            parent = tasks.findOwned(parentId, userId)
                    .orElseThrow(() -> ApiException.notFound("Parent task not found."));
            if (parent.parentId() != null) {
                throw ApiException.badRequest("A subtask cannot have its own subtasks.");
            }
        }

        LocalDateTime now = now();
        String title = req.title().trim();
        String description = req.description() == null ? "" : req.description().trim();
        long id = tasks.insert(userId, parentId, title, description, status, priority, due, categoryId, now,
                status == TaskStatus.COMPLETED ? now : null);
        if (tagNames != null && !tagNames.isEmpty()) {
            tags.replaceTaskTags(id, userId, tagNames);
        }
        if (parent != null) {
            log(userId, parent.id(), parent.title(), "SUBTASK_ADDED", "Subtask \"" + shorten(title, 80) + "\" added", now);
        } else {
            log(userId, id, title, "TASK_CREATED", "Task created with " + priority.getLabel() + " priority", now);
        }
        return toViewWithSubtasks(findOwned(userId, id));
    }

    // ------------------------------------------------------------------ update

    @Transactional
    public TaskView update(long userId, long id, TaskRequest req) {
        Task old = findOwned(userId, id);
        TaskStatus status = isBlank(req.status()) ? old.status() : TaskStatus.parse(req.status());
        TaskPriority priority = isBlank(req.priority()) ? old.priority() : TaskPriority.parse(req.priority());
        LocalDate due = parseDate(req.dueDate());
        Long categoryId = validateCategory(userId, req.categoryId());
        List<String> newTags = normalizeTags(req.tags());
        String title = req.title().trim();
        String description = req.description() == null ? "" : req.description().trim();

        LocalDateTime now = now();
        LocalDateTime completedAt = null;
        if (status == TaskStatus.COMPLETED) {
            completedAt = old.status() == TaskStatus.COMPLETED && old.completedAt() != null ? old.completedAt() : now;
        }
        tasks.update(id, userId, title, description, status, priority, due, categoryId, now, completedAt);

        if (!title.equals(old.title())) {
            log(userId, id, title, "TASK_UPDATED",
                    "Title changed from \"" + shorten(old.title(), 60) + "\" to \"" + shorten(title, 60) + "\"", now);
        }
        if (!description.equals(old.description())) {
            log(userId, id, title, "TASK_UPDATED", "Description updated", now);
        }
        if (priority != old.priority()) {
            log(userId, id, title, "PRIORITY_CHANGED",
                    "Priority changed from " + old.priority().getLabel() + " to " + priority.getLabel(), now);
        }
        if (!java.util.Objects.equals(due, old.dueDate())) {
            log(userId, id, title, "DUE_DATE_CHANGED",
                    "Due date changed from " + (old.dueDate() == null ? "none" : old.dueDate())
                            + " to " + (due == null ? "none" : due), now);
        }
        if (status != old.status()) {
            logStatus(userId, id, title, old.status(), status, now);
        }
        if (!java.util.Objects.equals(categoryId, old.categoryId())) {
            log(userId, id, title, "TASK_UPDATED", "Category changed", now);
        }
        if (newTags != null) {
            Set<String> before = tags.findByTaskIds(List.of(id)).getOrDefault(id, List.of()).stream()
                    .map(TagRef::name).collect(Collectors.toSet());
            if (!before.equals(new LinkedHashSet<>(newTags))) {
                tags.replaceTaskTags(id, userId, newTags);
                log(userId, id, title, "TASK_UPDATED", "Tags updated", now);
            }
        }
        return toViewWithSubtasks(findOwned(userId, id));
    }

    @Transactional
    public TaskView changeStatus(long userId, long id, String rawStatus) {
        Task old = findOwned(userId, id);
        TaskStatus status = TaskStatus.parse(rawStatus);
        if (status != old.status()) {
            LocalDateTime now = now();
            tasks.updateStatus(id, userId, status, now, status == TaskStatus.COMPLETED ? now : null);
            logStatus(userId, id, old.title(), old.status(), status, now);
        }
        return toViewWithSubtasks(findOwned(userId, id));
    }

    // ------------------------------------------------------------------ delete

    @Transactional
    public void delete(long userId, long id) {
        Task task = findOwned(userId, id);
        int subtasks = tasks.deleteSubtasks(id, userId);   // never leave orphaned subtasks
        tasks.delete(id, userId);
        LocalDateTime now = now();
        if (task.parentId() != null) {
            Task parent = tasks.findOwned(task.parentId(), userId).orElse(null);
            log(userId, task.parentId(), parent == null ? task.title() : parent.title(), "SUBTASK_REMOVED",
                    "Subtask \"" + shorten(task.title(), 80) + "\" removed", now);
        } else {
            log(userId, id, task.title(), "TASK_DELETED",
                    subtasks == 0 ? "Task deleted" : "Task deleted together with " + subtasks + " subtask(s)", now);
        }
    }

    // ------------------------------------------------------------------ helpers

    private Task findOwned(long userId, long id) {
        return tasks.findOwned(id, userId).orElseThrow(() -> ApiException.notFound("Task not found."));
    }

    private void logStatus(long userId, long taskId, String title, TaskStatus from, TaskStatus to, LocalDateTime now) {
        if (to == TaskStatus.COMPLETED) {
            log(userId, taskId, title, "TASK_COMPLETED", "Task marked as completed", now);
        } else if (from == TaskStatus.COMPLETED) {
            log(userId, taskId, title, "TASK_REOPENED", "Task reopened and set to " + to.getLabel(), now);
        } else {
            log(userId, taskId, title, "STATUS_CHANGED",
                    "Status changed from " + from.getLabel() + " to " + to.getLabel(), now);
        }
    }

    private void log(long userId, Long taskId, String title, String type, String description, LocalDateTime now) {
        history.insert(userId, taskId, shorten(title, 200), type, shorten(description, 500), now);
    }

    private Long validateCategory(long userId, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        categories.findOwned(categoryId, userId)
                .orElseThrow(() -> ApiException.badRequest("The selected category does not exist."));
        return categoryId;
    }

    /** Trims, lower-cases and de-duplicates tags. Returns null when the caller did not send any tag list. */
    static List<String> normalizeTags(List<String> raw) {
        if (raw == null) {
            return null;
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String tag : raw) {
            if (tag == null) {
                continue;
            }
            String name = tag.trim().replaceFirst("^#+", "").trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > 30) {
                throw ApiException.badRequest("Each tag must be at most 30 characters.");
            }
            if (!name.matches("[\\p{L}\\p{N} _.\\-]+")) {
                throw ApiException.badRequest("Tags may contain letters, numbers, spaces, '-', '_' and '.' only.");
            }
            result.add(name);
        }
        if (result.size() > 10) {
            throw ApiException.badRequest("A task can have at most 10 tags.");
        }
        return new ArrayList<>(result);
    }

    static LocalDate parseDate(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(raw.trim());
            if (date.getYear() < 1970 || date.getYear() > 2200) {
                throw ApiException.badRequest("Due date must be between the years 1970 and 2200.");
            }
            return date;
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Invalid due date. Use the format YYYY-MM-DD.");
        }
    }

    private static String normalizeDue(String due) {
        if (isBlank(due)) {
            return null;
        }
        String d = due.trim().toLowerCase(Locale.ROOT);
        if (DUE_WORDS.contains(d)) {
            return d;
        }
        try {
            return LocalDate.parse(due.trim()).toString();
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Invalid due filter. Use today, overdue, week, none or YYYY-MM-DD.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    static String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "\u2026";
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private List<TaskView> toViews(List<Task> rows) {
        LocalDate today = today();
        Map<Long, List<TagRef>> tagMap = tags.findByTaskIds(rows.stream().map(Task::id).toList());
        return rows.stream().map(t -> toView(t, today, tagMap.getOrDefault(t.id(), List.of()), null)).toList();
    }

    private TaskView toViewWithSubtasks(Task task) {
        List<TaskView> subs = toViews(tasks.findSubtasks(task.id(), task.userId()));
        Map<Long, List<TagRef>> tagMap = tags.findByTaskIds(List.of(task.id()));
        return toView(task, today(), tagMap.getOrDefault(task.id(), List.of()), subs);
    }

    private static TaskView toView(Task t, LocalDate today, List<TagRef> tagRefs, List<TaskView> subtasks) {
        boolean overdue = t.dueDate() != null && t.dueDate().isBefore(today) && t.status() != TaskStatus.COMPLETED;
        return new TaskView(t.id(), t.parentId(), t.title(), t.description(), t.status().name(),
                t.priority().name(), t.dueDate(), t.categoryId(), t.categoryName(), t.categoryColor(),
                tagRefs, t.createdAt(), t.updatedAt(), t.completedAt(), overdue,
                t.subtaskTotal(), t.subtaskDone(), subtasks);
    }
}
