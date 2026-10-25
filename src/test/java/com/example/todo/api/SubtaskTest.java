package com.example.todo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.fasterxml.jackson.databind.JsonNode;

class SubtaskTest extends AbstractIntegrationTest {

    @Test
    void subtasksBelongToTheirParentAndShowProgress() throws Exception {
        MockHttpSession s = newUserSession();
        long parent = createTask(s, "Parent").get("id").asLong();

        JsonNode sub1 = post("/api/tasks/" + parent + "/subtasks", s, Map.of("title", "Step 1"), 201);
        JsonNode sub2 = post("/api/tasks/" + parent + "/subtasks", s, Map.of("title", "Step 2"), 201);
        assertEquals(parent, sub1.get("parentId").asLong());
        patchJson("/api/tasks/" + sub1.get("id").asLong() + "/status", s, Map.of("status", "COMPLETED"), 200);

        JsonNode detail = getJson("/api/tasks/" + parent, s, 200);
        assertEquals(2, detail.get("subtaskTotal").asInt());
        assertEquals(1, detail.get("subtaskDone").asInt());
        assertEquals(2, detail.get("subtasks").size());
        assertEquals(2, getJson("/api/tasks/" + parent + "/subtasks", s, 200).size());

        // renaming a subtask works through the normal update endpoint
        JsonNode renamed = put("/api/tasks/" + sub2.get("id").asLong(), s, Map.of("title", "Step 2 (renamed)"), 200);
        assertEquals("Step 2 (renamed)", renamed.get("title").asText());
    }

    @Test
    void subtasksAreHiddenFromTheMainListButAvailableOnRequest() throws Exception {
        MockHttpSession s = newUserSession();
        long parent = createTask(s, "Parent").get("id").asLong();
        post("/api/tasks/" + parent + "/subtasks", s, Map.of("title", "Child"), 201);

        assertEquals(1, getJson("/api/tasks", s, 200).size());
        assertEquals(2, getJson("/api/tasks?includeSubtasks=true", s, 200).size());
        assertEquals(1, getJson("/api/tasks/stats", s, 200).get("total").asInt());
    }

    @Test
    void invalidNestingIsRejected() throws Exception {
        MockHttpSession s = newUserSession();
        long parent = createTask(s, "Parent").get("id").asLong();
        long child = post("/api/tasks/" + parent + "/subtasks", s, Map.of("title", "Child"), 201)
                .get("id").asLong();

        JsonNode error = post("/api/tasks/" + child + "/subtasks", s, Map.of("title", "Grandchild"), 400);
        assertTrue(error.get("message").asText().contains("subtask"));
        post("/api/tasks/987654/subtasks", s, Map.of("title", "orphan"), 404);
        post("/api/tasks", s, Map.of("title", "via body", "parentId", child), 400);
    }

    @Test
    void parentIdCannotBeChangedByUpdate() throws Exception {
        MockHttpSession s = newUserSession();
        long a = createTask(s, "A").get("id").asLong();
        long b = createTask(s, "B").get("id").asLong();
        long child = post("/api/tasks/" + a + "/subtasks", s, Map.of("title", "Child"), 201).get("id").asLong();

        JsonNode afterUpdate = put("/api/tasks/" + child, s, Map.of("title", "Child", "parentId", b), 200);
        assertEquals(a, afterUpdate.get("parentId").asLong());
        assertEquals(0, getJson("/api/tasks/" + b, s, 200).get("subtaskTotal").asInt());
    }

    @Test
    void deletingAParentRemovesItsSubtasksSoNothingIsOrphaned() throws Exception {
        MockHttpSession s = newUserSession();
        long parent = createTask(s, "Parent").get("id").asLong();
        long child = post("/api/tasks/" + parent + "/subtasks", s, Map.of("title", "Child"), 201)
                .get("id").asLong();

        deleteJson("/api/tasks/" + parent, s, 204);
        getJson("/api/tasks/" + child, s, 404);
        Integer orphans = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tasks WHERE parent_id IS NOT NULL AND parent_id NOT IN (SELECT id FROM tasks)",
                Integer.class);
        assertEquals(0, orphans);
    }

    @Test
    void deletingASubtaskLeavesTheParentIntact() throws Exception {
        MockHttpSession s = newUserSession();
        long parent = createTask(s, "Parent").get("id").asLong();
        long child = post("/api/tasks/" + parent + "/subtasks", s, Map.of("title", "Child"), 201)
                .get("id").asLong();
        deleteJson("/api/tasks/" + child, s, 204);
        assertEquals(0, getJson("/api/tasks/" + parent, s, 200).get("subtaskTotal").asInt());
    }
}
