package com.example.todo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.fasterxml.jackson.databind.JsonNode;

class TaskApiTest extends AbstractIntegrationTest {

    @Test
    void createReadUpdateDeleteLifecycle() throws Exception {
        MockHttpSession s = newUserSession();

        JsonNode created = createTask(s, Map.of("title", "  Write report  ", "description", "Chapter 1",
                "priority", "HIGH", "status", "IN_PROGRESS", "dueDate", today().plusDays(3).toString()));
        long id = created.get("id").asLong();
        assertEquals("Write report", created.get("title").asText());
        assertEquals("HIGH", created.get("priority").asText());
        assertEquals("IN_PROGRESS", created.get("status").asText());
        assertNotNull(created.get("createdAt"));

        JsonNode fetched = getJson("/api/tasks/" + id, s, 200);
        assertEquals("Chapter 1", fetched.get("description").asText());

        JsonNode updated = put("/api/tasks/" + id, s, Map.of("title", "Write final report", "description", "",
                "priority", "URGENT", "status", "TODO"), 200);
        assertEquals("Write final report", updated.get("title").asText());
        assertEquals("URGENT", updated.get("priority").asText());
        assertTrue(updated.get("dueDate").isNull(), "missing dueDate clears the date");

        deleteJson("/api/tasks/" + id, s, 204);
        getJson("/api/tasks/" + id, s, 404);
    }

    @Test
    void defaultsAreTodoAndMedium() throws Exception {
        MockHttpSession s = newUserSession();
        JsonNode t = createTask(s, "Defaults");
        assertEquals("TODO", t.get("status").asText());
        assertEquals("MEDIUM", t.get("priority").asText());
    }

    @Test
    void invalidInputIsRejectedWithClearMessages() throws Exception {
        MockHttpSession s = newUserSession();
        JsonNode blank = post("/api/tasks", s, Map.of("title", "   "), 400);
        assertTrue(blank.get("fieldErrors").has("title"));

        JsonNode badStatus = post("/api/tasks", s, Map.of("title", "x", "status", "DONE-ish"), 400);
        assertTrue(badStatus.get("message").asText().contains("Invalid status"));

        JsonNode badPriority = post("/api/tasks", s, Map.of("title", "x", "priority", "SUPER"), 400);
        assertTrue(badPriority.get("message").asText().contains("Invalid priority"));

        JsonNode badDate = post("/api/tasks", s, Map.of("title", "x", "dueDate", "31/12/2026"), 400);
        assertTrue(badDate.get("message").asText().contains("Invalid due date"));

        JsonNode impossible = post("/api/tasks", s, Map.of("title", "x", "dueDate", "2026-02-30"), 400);
        assertTrue(impossible.get("message").asText().contains("Invalid due date"));

        JsonNode tooLong = post("/api/tasks", s, Map.of("title", "a".repeat(201)), 400);
        assertTrue(tooLong.get("fieldErrors").has("title"));

        post("/api/tasks", s, "{not json", 400);
    }

    @Test
    void errorResponsesNeverContainStackTraces() throws Exception {
        MockHttpSession s = newUserSession();
        JsonNode error = getJson("/api/tasks/999999", s, 404);
        assertFalse(error.has("trace"));
        assertFalse(error.toString().contains("Exception"));
    }

    @Test
    void searchFindsTitleAndDescriptionCaseInsensitively() throws Exception {
        MockHttpSession s = newUserSession();
        createTask(s, Map.of("title", "Buy Milk", "description", "from the corner shop"));
        createTask(s, Map.of("title", "Call plumber", "description", "about the MILK-white sink"));
        createTask(s, "Unrelated");

        JsonNode hits = getJson("/api/tasks?q=milk", s, 200);
        assertEquals(2, hits.size());
        assertEquals(1, getJson("/api/tasks?q=corner", s, 200).size());
        assertEquals(0, getJson("/api/tasks?q=nothing-matches", s, 200).size());
        // LIKE wildcards typed by the user are matched literally
        assertEquals(0, getJson("/api/tasks?q=%25", s, 200).size());
    }

    @Test
    void filtersByStatusPriorityAndDue() throws Exception {
        MockHttpSession s = newUserSession();
        createTask(s, Map.of("title", "A", "status", "TODO", "priority", "LOW"));
        createTask(s, Map.of("title", "B", "status", "IN_PROGRESS", "priority", "HIGH",
                "dueDate", today().toString()));
        createTask(s, Map.of("title", "C", "status", "COMPLETED", "priority", "HIGH",
                "dueDate", today().minusDays(3).toString()));
        createTask(s, Map.of("title", "D", "status", "TODO", "priority", "URGENT",
                "dueDate", today().minusDays(1).toString()));

        assertEquals(2, getJson("/api/tasks?status=TODO", s, 200).size());
        assertEquals(1, getJson("/api/tasks?status=IN_PROGRESS", s, 200).size());
        assertEquals(2, getJson("/api/tasks?priority=HIGH", s, 200).size());
        assertEquals(1, getJson("/api/tasks?due=today", s, 200).size());
        assertEquals(1, getJson("/api/tasks?due=none", s, 200).size());
        JsonNode overdue = getJson("/api/tasks?due=overdue", s, 200);
        assertEquals(1, overdue.size());
        assertEquals("D", overdue.get(0).get("title").asText());
        assertEquals(1, getJson("/api/tasks?due=" + today(), s, 200).size());
        getJson("/api/tasks?status=NOPE", s, 400);
        getJson("/api/tasks?due=whenever", s, 400);
        getJson("/api/tasks?sort=password", s, 400);
    }

    @Test
    void sortsByTitlePriorityAndDueDate() throws Exception {
        MockHttpSession s = newUserSession();
        createTask(s, Map.of("title", "Bravo", "priority", "LOW", "dueDate", today().plusDays(5).toString()));
        createTask(s, Map.of("title", "Alpha", "priority", "URGENT", "dueDate", today().plusDays(9).toString()));
        createTask(s, Map.of("title", "Charlie", "priority", "MEDIUM"));

        assertEquals(List.of("Alpha", "Bravo", "Charlie"), titles(getJson("/api/tasks?sort=title&dir=asc", s, 200)));
        assertEquals(List.of("Alpha", "Charlie", "Bravo"),
                titles(getJson("/api/tasks?sort=priority&dir=desc", s, 200)));
        // tasks without a due date always come last
        assertEquals(List.of("Bravo", "Alpha", "Charlie"), titles(getJson("/api/tasks?sort=dueDate&dir=asc", s, 200)));
    }

    @Test
    void statusChangeCompletesAndReopensTasks() throws Exception {
        MockHttpSession s = newUserSession();
        long id = createTask(s, "Finish me").get("id").asLong();

        JsonNode done = patchJson("/api/tasks/" + id + "/status", s, Map.of("status", "COMPLETED"), 200);
        assertEquals("COMPLETED", done.get("status").asText());
        assertNotNull(done.get("completedAt").asText());

        JsonNode reopened = patchJson("/api/tasks/" + id + "/status", s, Map.of("status", "IN_PROGRESS"), 200);
        assertEquals("IN_PROGRESS", reopened.get("status").asText());
        assertTrue(reopened.get("completedAt").isNull());

        patchJson("/api/tasks/" + id + "/status", s, Map.of("status", "WRONG"), 400);
    }

    @Test
    void overdueRuleMatchesTheSpecification() throws Exception {
        MockHttpSession s = newUserSession();
        JsonNode pastOpen = createTask(s, Map.of("title", "past open", "dueDate", today().minusDays(1).toString()));
        JsonNode pastDone = createTask(s, Map.of("title", "past done", "status", "COMPLETED",
                "dueDate", today().minusDays(1).toString()));
        JsonNode dueToday = createTask(s, Map.of("title", "today", "dueDate", today().toString()));
        JsonNode future = createTask(s, Map.of("title", "future", "dueDate", today().plusDays(1).toString()));
        JsonNode noDate = createTask(s, "no date");

        assertTrue(pastOpen.get("overdue").asBoolean());
        assertFalse(pastDone.get("overdue").asBoolean());
        assertFalse(dueToday.get("overdue").asBoolean());
        assertFalse(future.get("overdue").asBoolean());
        assertFalse(noDate.get("overdue").asBoolean());
        assertNull(noDate.get("dueDate").textValue());
    }

    @Test
    void calendarReturnsOnlyTasksDueInTheRequestedMonth() throws Exception {
        MockHttpSession s = newUserSession();
        createTask(s, Map.of("title", "in march", "dueDate", "2031-03-15"));
        createTask(s, Map.of("title", "in april", "dueDate", "2031-04-02"));
        createTask(s, "no date");

        JsonNode march = getJson("/api/tasks/calendar?year=2031&month=3", s, 200);
        assertEquals(1, march.size());
        assertEquals("in march", march.get(0).get("title").asText());
        getJson("/api/tasks/calendar?year=2031&month=13", s, 400);
    }

    private static List<String> titles(JsonNode array) {
        List<String> result = new java.util.ArrayList<>();
        array.forEach(n -> result.add(n.get("title").asText()));
        return result;
    }
}
