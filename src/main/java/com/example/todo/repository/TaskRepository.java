package com.example.todo.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.example.todo.dto.TaskFilter;
import com.example.todo.model.Task;
import com.example.todo.model.TaskPriority;
import com.example.todo.model.TaskStatus;

/** All SQL for tasks and subtasks. Every query is parameterised and filtered by user_id. */
@Repository
public class TaskRepository {

    private static final String SELECT = "SELECT t.id, t.user_id, t.parent_id, t.title, t.description, "
            + "t.status, t.priority, t.due_date, t.category_id, c.name AS category_name, c.color AS category_color, "
            + "t.created_at, t.updated_at, t.completed_at, "
            + "(SELECT COUNT(*) FROM tasks s WHERE s.parent_id = t.id) AS sub_total, "
            + "(SELECT COUNT(*) FROM tasks s WHERE s.parent_id = t.id AND s.status = 'COMPLETED') AS sub_done "
            + "FROM tasks t LEFT JOIN categories c ON c.id = t.category_id ";

    private static final String PRIORITY_RANK = "CASE t.priority WHEN 'URGENT' THEN 4 WHEN 'HIGH' THEN 3 "
            + "WHEN 'MEDIUM' THEN 2 ELSE 1 END";

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static final RowMapper<Task> MAPPER = (rs, i) -> new Task(
            rs.getLong("id"), rs.getLong("user_id"), nullableLong(rs, "parent_id"),
            rs.getString("title"), rs.getString("description"),
            TaskStatus.valueOf(rs.getString("status")), TaskPriority.valueOf(rs.getString("priority")),
            rs.getObject("due_date", LocalDate.class), nullableLong(rs, "category_id"),
            rs.getString("category_name"), rs.getString("category_color"),
            rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class),
            rs.getObject("completed_at", LocalDateTime.class), rs.getInt("sub_total"), rs.getInt("sub_done"));

    private final JdbcTemplate jdbc;

    public TaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Task> findOwned(long id, long userId) {
        return jdbc.query(SELECT + "WHERE t.id = ? AND t.user_id = ?", MAPPER, id, userId).stream().findFirst();
    }

    public List<Task> findSubtasks(long parentId, long userId) {
        return jdbc.query(SELECT + "WHERE t.parent_id = ? AND t.user_id = ? ORDER BY t.id", MAPPER,
                parentId, userId);
    }

    public List<Task> findAllForExport(long userId) {
        return jdbc.query(SELECT + "WHERE t.user_id = ? ORDER BY t.id", MAPPER, userId);
    }

    public List<Task> findDueBetween(long userId, LocalDate from, LocalDate to) {
        return jdbc.query(SELECT + "WHERE t.user_id = ? AND t.parent_id IS NULL AND t.due_date BETWEEN ? AND ? "
                + "ORDER BY t.due_date, " + PRIORITY_RANK + " DESC, t.id", MAPPER, userId, from, to);
    }

    public List<Task> findUpcoming(long userId, LocalDate today, int limit) {
        return jdbc.query(SELECT + "WHERE t.user_id = ? AND t.parent_id IS NULL AND t.status <> 'COMPLETED' "
                + "AND t.due_date >= ? ORDER BY t.due_date, " + PRIORITY_RANK + " DESC, t.id LIMIT ?",
                MAPPER, userId, today, limit);
    }

    public List<Task> search(long userId, TaskFilter f, LocalDate today) {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE t.user_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(userId);

        if (!f.includeSubtasks()) {
            sql.append("AND t.parent_id IS NULL ");
        }
        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + JdbcSupport.escapeLike(f.q().trim().toLowerCase(Locale.ROOT)) + "%";
            sql.append("AND (LOWER(t.title) LIKE ? ESCAPE '\\' OR LOWER(t.description) LIKE ? ESCAPE '\\') ");
            args.add(like);
            args.add(like);
        }
        if (f.status() != null) {
            sql.append("AND t.status = ? ");
            args.add(f.status().name());
        }
        if (f.priority() != null) {
            sql.append("AND t.priority = ? ");
            args.add(f.priority().name());
        }
        if (f.categoryId() != null) {
            sql.append("AND t.category_id = ? ");
            args.add(f.categoryId());
        }
        if (f.tagId() != null) {
            sql.append("AND EXISTS (SELECT 1 FROM task_tags tt WHERE tt.task_id = t.id AND tt.tag_id = ?) ");
            args.add(f.tagId());
        }
        if (f.due() != null) {
            switch (f.due()) {
                case "today" -> {
                    sql.append("AND t.due_date = ? ");
                    args.add(today);
                }
                case "overdue" -> {
                    sql.append("AND t.due_date < ? AND t.status <> 'COMPLETED' ");
                    args.add(today);
                }
                case "week" -> {
                    sql.append("AND t.due_date BETWEEN ? AND ? ");
                    args.add(today);
                    args.add(today.plusDays(7));
                }
                case "none" -> sql.append("AND t.due_date IS NULL ");
                default -> {
                    sql.append("AND t.due_date = ? ");
                    args.add(LocalDate.parse(f.due()));
                }
            }
        }
        sql.append("ORDER BY ").append(orderBy(f.sort(), "desc".equalsIgnoreCase(f.dir())));
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    /** Sort column comes from a fixed whitelist, never from user text. */
    private static String orderBy(String sort, boolean desc) {
        String dir = desc ? "DESC" : "ASC";
        String key = sort == null ? "createdAt" : sort;
        String main = switch (key) {
            case "dueDate" -> "CASE WHEN t.due_date IS NULL THEN 1 ELSE 0 END, t.due_date " + dir;
            case "priority" -> PRIORITY_RANK + " " + dir;
            case "title" -> "LOWER(t.title) " + dir;
            case "updatedAt" -> "t.updated_at " + dir;
            default -> "t.created_at " + dir;
        };
        return main + ", t.id DESC";
    }

    public long insert(long userId, Long parentId, String title, String description, TaskStatus status,
                       TaskPriority priority, LocalDate dueDate, Long categoryId, LocalDateTime now,
                       LocalDateTime completedAt) {
        return JdbcSupport.insert(jdbc,
                "INSERT INTO tasks(user_id, parent_id, title, description, status, priority, due_date, "
                        + "category_id, created_at, updated_at, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId, parentId, title, description, status.name(), priority.name(), dueDate, categoryId,
                now, now, completedAt);
    }

    public int update(long id, long userId, String title, String description, TaskStatus status,
                      TaskPriority priority, LocalDate dueDate, Long categoryId, LocalDateTime now,
                      LocalDateTime completedAt) {
        return jdbc.update("UPDATE tasks SET title = ?, description = ?, status = ?, priority = ?, due_date = ?, "
                + "category_id = ?, updated_at = ?, completed_at = ? WHERE id = ? AND user_id = ?",
                title, description, status.name(), priority.name(), dueDate, categoryId, now, completedAt,
                id, userId);
    }

    public int updateStatus(long id, long userId, TaskStatus status, LocalDateTime now, LocalDateTime completedAt) {
        return jdbc.update("UPDATE tasks SET status = ?, updated_at = ?, completed_at = ? "
                + "WHERE id = ? AND user_id = ?", status.name(), now, completedAt, id, userId);
    }

    public int deleteSubtasks(long parentId, long userId) {
        return jdbc.update("DELETE FROM tasks WHERE parent_id = ? AND user_id = ?", parentId, userId);
    }

    public int delete(long id, long userId) {
        return jdbc.update("DELETE FROM tasks WHERE id = ? AND user_id = ?", id, userId);
    }
}
