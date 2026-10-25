package com.example.todo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.fasterxml.jackson.databind.JsonNode;

class HistoryTest extends AbstractIntegrationTest {

    @Test
    void importantEventsAreRecordedNewestFirst() throws Exception {
        MockHttpSession s = newUserSession();
        long id = createTask(s, Map.of("title", "History task", "priority", "LOW")).get("id").asLong();

        put("/api/tasks/" + id, s, Map.of("title", "History task v2", "description", "now with text",
                "priority", "URGENT", "dueDate", today().plusDays(2).toString()), 200);
        patchJson("/api/tasks/" + id + "/status", s, Map.of("status", "IN_PROGRESS"), 200);
        patchJson("/api/tasks/" + id + "/status", s, Map.of("status", "COMPLETED"), 200);
        patchJson("/api/tasks/" + id + "/status", s, Map.of("status", "TODO"), 200);

        JsonNode history = getJson("/api/tasks/" + id + "/history", s, 200);
        List<String> types = types(history);
        assertTrue(types.contains("TASK_CREATED"));
        assertTrue(types.contains("TASK_UPDATED"));
        assertTrue(types.contains("PRIORITY_CHANGED"));
        assertTrue(types.contains("DUE_DATE_CHANGED"));
        assertTrue(types.contains("STATUS_CHANGED"));
        assertTrue(types.contains("TASK_COMPLETED"));
        assertTrue(types.contains("TASK_REOPENED"));
        // newest first: the last action (reopen) comes before the creation
        assertEquals("TASK_REOPENED", types.get(0));
        assertEquals("TASK_CREATED", types.get(types.size() - 1));
        assertFalse(history.get(0).get("description").asText().isBlank());
        assertFalse(history.get(0).toString().contains("userId"));
    }

    @Test
    void subtaskEventsAreRecordedOnTheParent() throws Exception {
        MockHttpSession s = newUserSession();
        long parent = createTask(s, "Parent").get("id").asLong();
        long child = post("/api/tasks/" + parent + "/subtasks", s, Map.of("title", "Child"), 201)
                .get("id").asLong();
        deleteJson("/api/tasks/" + child, s, 204);

        List<String> types = types(getJson("/api/tasks/" + parent + "/history", s, 200));
        assertTrue(types.contains("SUBTASK_ADDED"));
        assertTrue(types.contains("SUBTASK_REMOVED"));
    }

    @Test
    void historySurvivesTaskDeletionAndStaysPrivate() throws Exception {
        MockHttpSession owner = newUserSession();
        MockHttpSession other = newUserSession();
        long id = createTask(owner, "Short lived").get("id").asLong();
        deleteJson("/api/tasks/" + id, owner, 204);

        List<String> recent = types(getJson("/api/history", owner, 200));
        assertEquals("TASK_DELETED", recent.get(0));
        assertTrue(recent.contains("TASK_CREATED"));
        assertEquals(2, getJson("/api/tasks/" + id + "/history", owner, 200).size());

        getJson("/api/tasks/" + id + "/history", other, 404);
        assertEquals(0, getJson("/api/history", other, 200).size());
    }

    private static List<String> types(JsonNode array) {
        List<String> result = new ArrayList<>();
        array.forEach(n -> result.add(n.get("eventType").asText()));
        return result;
    }
}
