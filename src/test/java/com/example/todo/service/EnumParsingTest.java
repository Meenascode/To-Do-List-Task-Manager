package com.example.todo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.todo.exception.ApiException;
import com.example.todo.model.TaskPriority;
import com.example.todo.model.TaskStatus;

class EnumParsingTest {

    @Test
    void statusAcceptsCommonSpellings() {
        assertEquals(TaskStatus.IN_PROGRESS, TaskStatus.parse("in progress"));
        assertEquals(TaskStatus.IN_PROGRESS, TaskStatus.parse("IN_PROGRESS"));
        assertEquals(TaskStatus.TODO, TaskStatus.parse(" todo "));
    }

    @Test
    void invalidValuesThrowBadRequest() {
        assertThrows(ApiException.class, () -> TaskStatus.parse("finished"));
        assertThrows(ApiException.class, () -> TaskStatus.parse(null));
        assertThrows(ApiException.class, () -> TaskPriority.parse("critical"));
        assertEquals(TaskPriority.URGENT, TaskPriority.parse("urgent"));
    }

    @Test
    void tagNormalisationLowercasesAndDeduplicates() {
        assertEquals(List.of("a", "b"), TaskService.normalizeTags(List.of("A", "#b", " a ", "")));
    }
}
