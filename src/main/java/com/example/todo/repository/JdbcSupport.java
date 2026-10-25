package com.example.todo.repository;

import java.sql.PreparedStatement;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/** Small helper shared by repositories: INSERT with parameters and return the generated id. */
final class JdbcSupport {

    private JdbcSupport() {
    }

    static long insert(JdbcTemplate jdbc, String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[] {"ID"});
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("The database did not return a generated id.");
        }
        for (Map.Entry<String, Object> e : keys.entrySet()) {
            if (e.getKey().equalsIgnoreCase("ID")) {
                return ((Number) e.getValue()).longValue();
            }
        }
        return ((Number) keys.values().iterator().next()).longValue();
    }

    /** Escapes % and _ so user text is matched literally in LIKE ... ESCAPE '\'. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Builds "?,?,?" for an IN (...) clause. */
    static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }
}
