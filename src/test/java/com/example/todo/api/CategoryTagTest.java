package com.example.todo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.fasterxml.jackson.databind.JsonNode;

class CategoryTagTest extends AbstractIntegrationTest {

    @Test
    void categoryCrudAndValidation() throws Exception {
        MockHttpSession s = newUserSession();
        JsonNode created = post("/api/categories", s, Map.of("name", "Work", "color", "#2563EB"), 201);
        long id = created.get("id").asLong();
        assertEquals("#2563EB", created.get("color").asText());

        post("/api/categories", s, Map.of("name", "work"), 409);               // duplicate (case-insensitive)
        post("/api/categories", s, Map.of("name", "Bad", "color", "blue"), 400); // invalid color
        post("/api/categories", s, Map.of("name", " "), 400);

        JsonNode updated = put("/api/categories/" + id, s, Map.of("name", "Office", "color", "#06B6D4"), 200);
        assertEquals("Office", updated.get("name").asText());
        assertEquals(1, getJson("/api/categories", s, 200).size());

        deleteJson("/api/categories/" + id, s, 204);
        assertEquals(0, getJson("/api/categories", s, 200).size());
        deleteJson("/api/categories/" + id, s, 404);
    }

    @Test
    void tasksCanBeAssignedAndFilteredByCategory() throws Exception {
        MockHttpSession s = newUserSession();
        long work = post("/api/categories", s, Map.of("name", "Work"), 201).get("id").asLong();
        long home = post("/api/categories", s, Map.of("name", "Home"), 201).get("id").asLong();
        JsonNode t1 = createTask(s, Map.of("title", "w1", "categoryId", work));
        createTask(s, Map.of("title", "w2", "categoryId", work));
        createTask(s, Map.of("title", "h1", "categoryId", home));
        createTask(s, "none");

        assertEquals("Work", t1.get("categoryName").asText());
        assertEquals(2, getJson("/api/tasks?categoryId=" + work, s, 200).size());
        assertEquals(1, getJson("/api/tasks?categoryId=" + home, s, 200).size());
        JsonNode cats = getJson("/api/categories", s, 200);   // sorted by name: Home, Work
        assertEquals("Home", cats.get(0).get("name").asText());
        assertEquals(1, cats.get(0).get("taskCount").asInt());
        assertEquals(2, cats.get(1).get("taskCount").asInt());
    }

    @Test
    void deletingACategoryKeepsItsTasks() throws Exception {
        MockHttpSession s = newUserSession();
        long cat = post("/api/categories", s, Map.of("name", "Temp"), 201).get("id").asLong();
        long task = createTask(s, Map.of("title", "keep me", "categoryId", cat)).get("id").asLong();

        deleteJson("/api/categories/" + cat, s, 204);
        JsonNode after = getJson("/api/tasks/" + task, s, 200);
        assertTrue(after.get("categoryId").isNull());
        assertTrue(after.get("categoryName").isNull());
    }

    @Test
    void tagsAreReusedNormalisedAndFilterable() throws Exception {
        MockHttpSession s = newUserSession();
        JsonNode first = createTask(s, Map.of("title", "t1", "tags", List.of("Urgent", "#College", "  college ")));
        createTask(s, Map.of("title", "t2", "tags", List.of("urgent")));
        createTask(s, "t3");

        assertEquals(2, first.get("tags").size(), "duplicates inside one task are merged");
        JsonNode tags = getJson("/api/tags", s, 200);
        assertEquals(2, tags.size(), "the tag 'urgent' is created once and reused");

        long urgentId = -1;
        for (JsonNode tag : tags) {
            if (tag.get("name").asText().equals("urgent")) {
                urgentId = tag.get("id").asLong();
                assertEquals(2, tag.get("taskCount").asInt());
            }
        }
        assertEquals(2, getJson("/api/tasks?tagId=" + urgentId, s, 200).size());
    }

    @Test
    void updatingTagsReplacesThemAndOmittingKeepsThem() throws Exception {
        MockHttpSession s = newUserSession();
        long id = createTask(s, Map.of("title", "x", "tags", List.of("a", "b"))).get("id").asLong();

        JsonNode kept = put("/api/tasks/" + id, s, Map.of("title", "x renamed"), 200);
        assertEquals(2, kept.get("tags").size());

        JsonNode replaced = put("/api/tasks/" + id, s, Map.of("title", "x", "tags", List.of("c")), 200);
        assertEquals(1, replaced.get("tags").size());
        assertEquals("c", replaced.get("tags").get(0).get("name").asText());

        JsonNode cleared = put("/api/tasks/" + id, s, Map.of("title", "x", "tags", List.of()), 200);
        assertEquals(0, cleared.get("tags").size());
    }

    @Test
    void invalidTagsAreRejected() throws Exception {
        MockHttpSession s = newUserSession();
        post("/api/tasks", s, Map.of("title", "x", "tags", List.of("a".repeat(31))), 400);
        post("/api/tasks", s, Map.of("title", "x", "tags", List.of("<script>")), 400);
    }
}
