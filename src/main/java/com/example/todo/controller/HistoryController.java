package com.example.todo.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.todo.model.HistoryEntry;
import com.example.todo.security.AuthUser;
import com.example.todo.service.HistoryService;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService service;

    public HistoryController(HistoryService service) {
        this.service = service;
    }

    /** Newest events first. */
    @GetMapping
    public List<HistoryEntry> recent(@AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "50") int limit) {
        return service.recent(user.getId(), limit);
    }
}
