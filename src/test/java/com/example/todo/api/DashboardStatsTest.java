package com.example.todo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.fasterxml.jackson.databind.JsonNode;

class DashboardStatsTest extends AbstractIntegrationTest {

    @Test
    void emptyAccountHasZeroStatistics() throws Exception {
        MockHttpSession s = newUserSession();
        JsonNode stats = getJson("/api/tasks/stats", s, 200);
        assertEquals(0, stats.get("total").asInt());
        assertEquals(0, stats.get("completionPercent").asInt());
        assertEquals(0, stats.get("overdue").asInt());
        assertEquals(7, stats.get("weekly").size());
        assertEquals(0, stats.get("upcoming").size());
        assertEquals(today().toString(), stats.get("today").asText());
    }

    @Test
    void statisticsAreCalculatedFromTheUsersRecordsAndUpdateWhenTasksChange() throws Exception {
        MockHttpSession s = newUserSession();
        createTask(s, Map.of("title", "todo overdue", "priority", "URGENT",
                "dueDate", today().minusDays(2).toString()));
        createTask(s, Map.of("title", "in progress today", "status", "IN_PROGRESS", "priority", "HIGH",
                "dueDate", today().toString()));
        long doneId = createTask(s, Map.of("title", "done", "status", "COMPLETED", "priority", "LOW",
                "dueDate", today().minusDays(5).toString())).get("id").asLong();
        createTask(s, Map.of("title", "future", "priority", "MEDIUM", "dueDate", today().plusDays(3).toString()));

        JsonNode stats = getJson("/api/tasks/stats", s, 200);
        assertEquals(4, stats.get("total").asInt());
        assertEquals(2, stats.get("todo").asInt());
        assertEquals(1, stats.get("inProgress").asInt());
        assertEquals(1, stats.get("completed").asInt());
        assertEquals(1, stats.get("overdue").asInt(), "completed tasks are never overdue");
        assertEquals(1, stats.get("dueToday").asInt());
        assertEquals(25, stats.get("completionPercent").asInt());
        assertEquals(1, stats.get("priorityDistribution").get("URGENT").asInt());
        assertEquals(1, stats.get("priorityDistribution").get("LOW").asInt());
        assertEquals(1, stats.get("completedThisWeek").asInt());
        assertEquals(2, stats.get("upcoming").size());
        assertTrue(stats.get("recentActivity").size() >= 4);

        // reopening the completed task changes the numbers (it is now overdue)
        patchJson("/api/tasks/" + doneId + "/status", s, Map.of("status", "TODO"), 200);
        JsonNode after = getJson("/api/tasks/stats", s, 200);
        assertEquals(0, after.get("completed").asInt());
        assertEquals(2, after.get("overdue").asInt());
        assertEquals(0, after.get("completionPercent").asInt());
    }

    @Test
    void weeklyChartCountsTasksCompletedAndCreatedToday() throws Exception {
        MockHttpSession s = newUserSession();
        createTask(s, Map.of("title", "done now", "status", "COMPLETED"));
        createTask(s, "open");
        JsonNode weekly = getJson("/api/tasks/stats", s, 200).get("weekly");
        JsonNode last = weekly.get(weekly.size() - 1);
        assertEquals(today().toString(), last.get("date").asText());
        assertEquals(2, last.get("created").asInt());
        assertEquals(1, last.get("completed").asInt());
    }
}
