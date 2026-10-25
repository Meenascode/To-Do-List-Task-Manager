package com.example.todo.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.example.todo.model.User;

@Repository
public class UserRepository {

    private static final RowMapper<User> MAPPER = (rs, i) -> new User(
            rs.getLong("id"), rs.getString("name"), rs.getString("email"),
            rs.getString("password_hash"), rs.getObject("created_at", LocalDateTime.class));

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findByEmail(String email) {
        List<User> rows = jdbc.query(
                "SELECT id, name, email, password_hash, created_at FROM users WHERE email = ?", MAPPER, email);
        return rows.stream().findFirst();
    }

    public boolean existsByEmail(String email) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        return n != null && n > 0;
    }

    public long insert(String name, String email, String passwordHash, LocalDateTime now) {
        return JdbcSupport.insert(jdbc,
                "INSERT INTO users(name, email, password_hash, created_at) VALUES (?, ?, ?, ?)",
                name, email, passwordHash, now);
    }
}
