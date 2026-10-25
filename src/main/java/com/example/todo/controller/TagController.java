package com.example.todo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.todo.dto.TagView;
import com.example.todo.security.AuthUser;
import com.example.todo.service.TagService;

/** Tags are created by typing them on a task; this controller lists and removes them. */
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService service;

    public TagController(TagService service) {
        this.service = service;
    }

    @GetMapping
    public List<TagView> list(@AuthenticationPrincipal AuthUser user) {
        return service.list(user.getId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        service.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
