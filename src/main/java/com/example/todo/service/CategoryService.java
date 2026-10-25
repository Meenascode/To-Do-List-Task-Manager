package com.example.todo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.todo.dto.CategoryRequest;
import com.example.todo.dto.CategoryView;
import com.example.todo.exception.ApiException;
import com.example.todo.repository.CategoryRepository;

@Service
public class CategoryService {

    private static final String DEFAULT_COLOR = "#6B7280";

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> list(long userId) {
        return categories.findAllByUser(userId);
    }

    @Transactional
    public CategoryView create(long userId, CategoryRequest req) {
        String name = req.name().trim();
        if (categories.nameExists(userId, name, 0)) {
            throw ApiException.conflict("You already have a category with this name.");
        }
        long id = categories.insert(userId, name, color(req));
        return new CategoryView(id, name, color(req), 0);
    }

    @Transactional
    public CategoryView update(long userId, long id, CategoryRequest req) {
        categories.findOwned(id, userId).orElseThrow(() -> ApiException.notFound("Category not found."));
        String name = req.name().trim();
        if (categories.nameExists(userId, name, id)) {
            throw ApiException.conflict("You already have a category with this name.");
        }
        categories.update(id, userId, name, color(req));
        return list(userId).stream().filter(c -> c.id() == id).findFirst()
                .orElseThrow(() -> ApiException.notFound("Category not found."));
    }

    /** Tasks in the deleted category are kept; their category is cleared (ON DELETE SET NULL). */
    @Transactional
    public void delete(long userId, long id) {
        if (categories.delete(id, userId) == 0) {
            throw ApiException.notFound("Category not found.");
        }
    }

    private static String color(CategoryRequest req) {
        return req.color() == null || req.color().isBlank() ? DEFAULT_COLOR : req.color().toUpperCase();
    }
}
