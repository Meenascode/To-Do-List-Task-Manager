package com.example.todo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.fasterxml.jackson.databind.JsonNode;

/** Two users must never see or change each other's data, even when they guess record ids. */
class UserIsolationTest extends AbstractIntegrationTest {

    @Test
    void userCannotReadChangeOrDeleteAnotherUsersTask() throws Exception {
        MockHttpSession alice = newUserSession();
        MockHttpSession bob = newUserSession();
        long aliceTask = createTask(alice, "Alice private task").get("id").asLong();

        getJson("/api/tasks/" + aliceTask, bob, 404);
        put("/api/tasks/" + aliceTask, bob, Map.of("title", "hacked"), 404);
        patchJson("/api/tasks/" + aliceTask + "/status", bob, Map.of("status", "COMPLETED"), 404);
        getJson("/api/tasks/" + aliceTask + "/history", bob, 404);
        deleteJson("/api/tasks/" + aliceTask, bob, 404);
        post("/api/tasks/" + aliceTask + "/subtasks", bob, Map.of("title", "sneaky subtask"), 404);

        // Alice's task is untouched
        JsonNode still = getJson("/api/tasks/" + aliceTask, alice, 200);
        assertEquals("Alice private task", still.get("title").asText());
        assertEquals("TODO", still.get("status").asText());
        assertEquals(0, still.get("subtaskTotal").asInt());
    }

    @Test
    void listsAndStatisticsOnlyContainTheUsersOwnRecords() throws Exception {
        MockHttpSession alice = newUserSession();
        MockHttpSession bob = newUserSession();
        createTask(alice, "A1");
        createTask(alice, "A2");
        createTask(bob, "B1");

        assertEquals(2, getJson("/api/tasks", alice, 200).size());
        assertEquals(1, getJson("/api/tasks", bob, 200).size());
        assertEquals(2, getJson("/api/tasks/stats", alice, 200).get("total").asInt());
        assertEquals(1, getJson("/api/tasks/stats", bob, 200).get("total").asInt());
        assertEquals(1, getJson("/api/history", bob, 200).size());
    }

    @Test
    void userCannotUseAnotherUsersCategory() throws Exception {
        MockHttpSession alice = newUserSession();
        MockHttpSession bob = newUserSession();
        long aliceCategory = post("/api/categories", alice, Map.of("name", "Secret", "color", "#112233"), 201)
                .get("id").asLong();

        JsonNode error = post("/api/tasks", bob, Map.of("title", "x", "categoryId", aliceCategory), 400);
        assertTrue(error.get("message").asText().contains("category"));
        put("/api/categories/" + aliceCategory, bob, Map.of("name", "Mine now"), 404);
        deleteJson("/api/categories/" + aliceCategory, bob, 404);
        assertEquals(0, getJson("/api/categories", bob, 200).size());
    }

    @Test
    void userCannotDeleteAnotherUsersTag() throws Exception {
        MockHttpSession alice = newUserSession();
        MockHttpSession bob = newUserSession();
        createTask(alice, Map.of("title", "tagged", "tags", java.util.List.of("private")));
        long tagId = getJson("/api/tags", alice, 200).get(0).get("id").asLong();

        deleteJson("/api/tags/" + tagId, bob, 404);
        assertEquals(0, getJson("/api/tags", bob, 200).size());
        assertEquals(1, getJson("/api/tags", alice, 200).size());
    }
}
