package com.example.todo.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.example.todo.dto.TagRef;
import com.example.todo.dto.TagView;

@Repository
public class TagRepository {

    private final JdbcTemplate jdbc;

    public TagRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TagView> findAllByUser(long userId) {
        return jdbc.query(
                "SELECT g.id, g.name, (SELECT COUNT(*) FROM task_tags tt JOIN tasks t ON t.id = tt.task_id "
                        + "WHERE tt.tag_id = g.id AND t.parent_id IS NULL) AS task_count "
                        + "FROM tags g WHERE g.user_id = ? ORDER BY g.name",
                (rs, i) -> new TagView(rs.getLong("id"), rs.getString("name"), rs.getInt("task_count")),
                userId);
    }

    public Map<Long, List<TagRef>> findByTaskIds(Collection<Long> taskIds) {
        Map<Long, List<TagRef>> result = new HashMap<>();
        if (taskIds.isEmpty()) {
            return result;
        }
        String sql = "SELECT tt.task_id, g.id, g.name FROM task_tags tt JOIN tags g ON g.id = tt.tag_id "
                + "WHERE tt.task_id IN (" + JdbcSupport.placeholders(taskIds.size()) + ") ORDER BY g.name";
        jdbc.query(sql, rs -> {
            result.computeIfAbsent(rs.getLong("task_id"), k -> new ArrayList<>())
                    .add(new TagRef(rs.getLong("id"), rs.getString("name")));
        }, taskIds.toArray());
        return result;
    }

    public boolean existsOwned(long id, long userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM tags WHERE id = ? AND user_id = ?",
                Integer.class, id, userId);
        return n != null && n > 0;
    }

    public long findOrCreate(long userId, String name) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM tags WHERE user_id = ? AND name = ?",
                Long.class, userId, name);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        try {
            return JdbcSupport.insert(jdbc, "INSERT INTO tags(user_id, name) VALUES (?, ?)", userId, name);
        } catch (DuplicateKeyException e) {
            return jdbc.queryForObject("SELECT id FROM tags WHERE user_id = ? AND name = ?",
                    Long.class, userId, name);
        }
    }

    /** Replaces all tags of a task. Call only after the task's ownership has been verified. */
    public void replaceTaskTags(long taskId, long userId, List<String> names) {
        jdbc.update("DELETE FROM task_tags WHERE task_id = ?", taskId);
        for (String name : names) {
            long tagId = findOrCreate(userId, name);
            jdbc.update("INSERT INTO task_tags(task_id, tag_id) VALUES (?, ?)", taskId, tagId);
        }
    }

    public int delete(long id, long userId) {
        return jdbc.update("DELETE FROM tags WHERE id = ? AND user_id = ?", id, userId);
    }
}
