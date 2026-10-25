package com.example.todo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.todo.dto.RegisterRequest;
import com.example.todo.dto.UserView;
import com.example.todo.exception.ApiException;
import com.example.todo.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional
    public UserView register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String name = req.name().trim();
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("An account with this email already exists.");
        }
        try {
            long id = users.insert(name, email, encoder.encode(req.password()),
                    LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
            return new UserView(id, name, email);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("An account with this email already exists.");
        }
    }
}
