package com.example.todo.security;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import com.example.todo.dto.UserView;

/** The logged-in user stored in the session. Carries the database id used for ownership checks. */
public class AuthUser extends User {

    private static final long serialVersionUID = 1L;

    private final long id;
    private final String displayName;

    public AuthUser(long id, String displayName, String email, String passwordHash) {
        super(email, passwordHash, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.id = id;
        this.displayName = displayName;
    }

    public long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserView toView() {
        return new UserView(id, displayName, getUsername());
    }
}
