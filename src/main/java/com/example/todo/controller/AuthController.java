package com.example.todo.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.todo.dto.RegisterRequest;
import com.example.todo.dto.UserView;
import com.example.todo.security.AuthUser;
import com.example.todo.service.AuthService;

import jakarta.validation.Valid;

/** Registration and session info. Login and logout themselves are handled by Spring Security. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** The browser calls this to obtain the CSRF token it must send with every POST/PUT/PATCH/DELETE. */
    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(Map.of("headerName", token.getHeaderName(),
                        "parameterName", token.getParameterName(),
                        "token", token.getToken()));
    }

    @PostMapping("/register")
    public ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AuthUser user) {
        return user.toView();
    }
}
