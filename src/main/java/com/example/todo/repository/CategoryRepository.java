package com.example.todo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.example.todo.dto.CategoryView;
import com.example.todo.model.Category;

@Repository
public class CategoryRepository {

    private static final RowMapper<Category> MAPPER = (rs, i) -> new Category(
            rs.getLong("id"), rs.getLong("user_id"), rs.getString("name"), rs.getString("color"));

    private final JdbcTemplate jdbc;

    public CategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CategoryView> findAllByUser(long userId) {
        return jdbc.query(
                "SELECT c.id, c.name, c.color, "
                        + "(SELECT COUNT(*) FROM tasks t WHERE t.category_id = c.id AND t.parent_id IS NULL) AS task_count "
                        + "FROM categories c WHERE c.user_id = ? ORDER BY LOWER(c.name)",
                (rs, i) -> new CategoryView(rs.getLong("id"), rs.getString("name"), rs.getString("color"),
                        rs.getInt("task_count")),
                userId);
    }

    public Optional<Category> findOwned(long id, long userId) {
        return jdbc.query("SELECT id, user_id, name, color FROM categories WHERE id = ? AND user_id = ?",
                MAPPER, id, userId).stream().findFirst();
    }

    public boolean nameExists(long userId, String name, long excludeId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM categories WHERE user_id = ? AND LOWER(name) = LOWER(?) AND id <> ?",
                Integer.class, userId, name, excludeId);
        return n != null && n > 0;
    }

    public long insert(long userId, String name, String color) {
        return JdbcSupport.insert(jdbc, "INSERT INTO categories(user_id, name, color) VALUES (?, ?, ?)",
                userId, name, color);
    }

    public int update(long id, long userId, String name, String color) {
        return jdbc.update("UPDATE categories SET name = ?, color = ? WHERE id = ? AND user_id = ?",
                name, color, id, userId);
    }

    public int delete(long id, long userId) {
        return jdbc.update("DELETE FROM categories WHERE id = ? AND user_id = ?", id, userId);
    }
}
