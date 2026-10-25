package com.example.todo.model;

import java.time.LocalDateTime;

public record User(long id, String name, String email, String passwordHash, LocalDateTime createdAt) {
}
