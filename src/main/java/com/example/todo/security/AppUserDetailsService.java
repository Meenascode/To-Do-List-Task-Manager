package com.example.todo.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.todo.repository.UserRepository;

/** Lets Spring Security look users up in our H2 table. Spring itself compares the password hash. */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return users.findByEmail(normalized)
                .map(u -> new AuthUser(u.id(), u.name(), u.email(), u.passwordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
