package com.example.todo.repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Aggregate queries for the dashboard. Only top-level tasks are counted; subtasks show as progress. */
@Repository
public class StatsRepository {

    public record Counts(int total, int todo, int inProgress, int completed, int overdue, int dueToday) {
    }

    private final JdbcTemplate jdbc;

    public StatsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Counts counts(long userId, LocalDate today) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) AS total, "
                        + "COALESCE(SUM(CASE WHEN status = 'TODO' THEN 1 ELSE 0 END), 0) AS todo, "
                        + "COALESCE(SUM(CASE WHEN status = 'IN_PROGRESS' THEN 1 ELSE 0 END), 0) AS in_progress, "
                        + "COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed, "
                        + "COALESCE(SUM(CASE WHEN status <> 'COMPLETED' AND due_date < ? THEN 1 ELSE 0 END), 0) AS overdue, "
                        + "COALESCE(SUM(CASE WHEN status <> 'COMPLETED' AND due_date = ? THEN 1 ELSE 0 END), 0) AS due_today "
                        + "FROM tasks WHERE user_id = ? AND parent_id IS NULL",
                (rs, i) -> new Counts(rs.getInt("total"), rs.getInt("todo"), rs.getInt("in_progress"),
                        rs.getInt("completed"), rs.getInt("overdue"), rs.getInt("due_today")),
                today, today, userId);
    }

    public Map<String, Integer> priorityCounts(long userId) {
        Map<String, Integer> result = new HashMap<>();
        jdbc.query("SELECT priority, COUNT(*) AS c FROM tasks WHERE user_id = ? AND parent_id IS NULL "
                + "GROUP BY priority", rs -> {
                    result.put(rs.getString("priority"), rs.getInt("c"));
                }, userId);
        return result;
    }

    public Map<LocalDate, Integer> completedPerDay(long userId, LocalDate from) {
        return perDay("completed_at", userId, from);
    }

    public Map<LocalDate, Integer> createdPerDay(long userId, LocalDate from) {
        return perDay("created_at", userId, from);
    }

    /** column is one of two constants above, never user input. */
    private Map<LocalDate, Integer> perDay(String column, long userId, LocalDate from) {
        Map<LocalDate, Integer> result = new HashMap<>();
        jdbc.query("SELECT CAST(" + column + " AS DATE) AS d, COUNT(*) AS c FROM tasks "
                + "WHERE user_id = ? AND parent_id IS NULL AND " + column + " >= ? "
                + "GROUP BY CAST(" + column + " AS DATE)", rs -> {
                    result.put(rs.getObject("d", LocalDate.class), rs.getInt("c"));
                }, userId, from.atStartOfDay());
        return result;
    }
}
