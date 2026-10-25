package com.example.todo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.todo.dto.TagView;
import com.example.todo.exception.ApiException;
import com.example.todo.repository.TagRepository;

@Service
public class TagService {

    private final TagRepository tags;

    public TagService(TagRepository tags) {
        this.tags = tags;
    }

    @Transactional(readOnly = true)
    public List<TagView> list(long userId) {
        return tags.findAllByUser(userId);
    }

    @Transactional
    public void delete(long userId, long id) {
        if (tags.delete(id, userId) == 0) {
            throw ApiException.notFound("Tag not found.");
        }
    }
}
