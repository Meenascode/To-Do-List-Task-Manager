package com.example.todo.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.example.todo.model.HistoryEntry;

@Repository
public class HistoryRepository {

    private static final String COLUMNS = "SELECT id, task_id, task_title, event_type, description, created_at "
            + "FROM task_history ";

    private static final RowMapper<HistoryEntry> MAPPER = (rs, i) -> {
        long taskId = rs.getLong("task_id");
        Long taskIdOrNull = rs.wasNull() ? null : taskId;
        return new HistoryEntry(rs.getLong("id"), taskIdOrNull, rs.getString("task_title"),
                rs.getString("event_type"), rs.getString("description"),
                rs.getObject("created_at", LocalDateTime.class));
    };

    private final JdbcTemplate jdbc;

    public HistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long userId, Long taskId, String taskTitle, String eventType, String description,
                       LocalDateTime now) {
        jdbc.update("INSERT INTO task_history(user_id, task_id, task_title, event_type, description, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", userId, taskId, taskTitle, eventType, description, now);
    }

    public List<HistoryEntry> findRecent(long userId, int limit) {
        return jdbc.query(COLUMNS + "WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?",
                MAPPER, userId, limit);
    }

    public List<HistoryEntry> findByTask(long userId, long taskId) {
        return jdbc.query(COLUMNS + "WHERE user_id = ? AND task_id = ? ORDER BY created_at DESC, id DESC",
                MAPPER, userId, taskId);
    }
}
