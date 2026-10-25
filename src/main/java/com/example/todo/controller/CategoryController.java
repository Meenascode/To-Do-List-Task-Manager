package com.example.todo.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.todo.dto.CategoryRequest;
import com.example.todo.dto.CategoryView;
import com.example.todo.security.AuthUser;
import com.example.todo.service.CategoryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoryView> list(@AuthenticationPrincipal AuthUser user) {
        return service.list(user.getId());
    }

    @PostMapping
    public ResponseEntity<CategoryView> create(@AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.getId(), request));
    }

    @PutMapping("/{id}")
    public CategoryView update(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
            @Valid @RequestBody CategoryRequest request) {
        return service.update(user.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        service.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
