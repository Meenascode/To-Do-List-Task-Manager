package com.example.todo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

class CsvExportApiTest extends AbstractIntegrationTest {

    private String exportCsv(MockHttpSession s) throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/api/export/tasks.csv").session(s))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment; filename=\"tasks.csv\"")))
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith("text/csv"))
                .andReturn();
        String text = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    @Test
    void exportHasHeaderRowAndEscapesSpecialCharacters() throws Exception {
        MockHttpSession s = newUserSession();
        long cat = post("/api/categories", s, Map.of("name", "Work, Office"), 201).get("id").asLong();
        createTask(s, Map.of("title", "Plain task", "description", "simple", "priority", "HIGH",
                "dueDate", "2031-05-06", "categoryId", cat, "tags", List.of("one", "two")));
        createTask(s, Map.of("title", "Comma, \"quote\" and\nnewline", "description", "x"));

        String csv = exportCsv(s);
        assertTrue(csv.startsWith("ID,Parent ID,Title,Description,Status,Priority,Category,Due Date,Tags,Created At,Updated At\r\n"));
        assertTrue(csv.contains(",Plain task,simple,To Do,High,\"Work, Office\",2031-05-06,one; two,"));
        assertTrue(csv.contains("\"Comma, \"\"quote\"\" and\nnewline\""));
    }

    @Test
    void exportNeutralisesSpreadsheetFormulas() throws Exception {
        MockHttpSession s = newUserSession();
        createTask(s, Map.of("title", "=HYPERLINK(\"http://evil\")", "description", "+1+1"));
        createTask(s, Map.of("title", "@SUM(A1)", "description", "-2+3"));

        String csv = exportCsv(s);
        assertFalse(csv.contains(",=HYPERLINK"), "formula must not start a cell");
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"http://evil\"\")\""));
        assertTrue(csv.contains(",'+1+1,"));
        assertTrue(csv.contains(",'@SUM(A1),'-2+3,"));
    }

    @Test
    void exportOnlyContainsTheLoggedInUsersTasks() throws Exception {
        MockHttpSession alice = newUserSession();
        MockHttpSession bob = newUserSession();
        createTask(alice, "Alice only task");
        createTask(bob, "Bob only task");

        String aliceCsv = exportCsv(alice);
        String bobCsv = exportCsv(bob);
        assertTrue(aliceCsv.contains("Alice only task"));
        assertFalse(aliceCsv.contains("Bob only task"));
        assertTrue(bobCsv.contains("Bob only task"));
        assertFalse(bobCsv.contains("Alice only task"));
        assertEquals(2, bobCsv.split("\r\n").length, "header + one task");
    }

    @Test
    void exportRequiresLogin() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/export/tasks.csv"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }
}
