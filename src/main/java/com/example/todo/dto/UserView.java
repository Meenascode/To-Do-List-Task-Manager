package com.example.todo.dto;

/** Safe representation of a user: never contains the password or its hash. */
public record UserView(long id, String name, String email) {
}
