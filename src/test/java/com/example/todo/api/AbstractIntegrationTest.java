package com.example.todo.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared helpers. The "test" profile uses an in-memory H2 database, so the developer's
 * ./data/tododb file is never touched. Each test registers its own users with unique e-mails.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "Passw0rdTest";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected Clock clock;

    protected LocalDate today() {
        return LocalDate.now(clock);
    }

    protected String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.example";
    }

    protected MockHttpSession newUserSession() throws Exception {
        String email = uniqueEmail();
        register("Test User", email, PASSWORD);
        return login(email, PASSWORD);
    }

    protected void register(String name, String email, String password) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("password", password);
        post("/api/auth/register", null, body, 201);
    }

    protected MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").with(csrf())
                .param("email", email).param("password", password))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    // ---- request helpers: return the parsed JSON body and assert the HTTP status ----

    protected JsonNode send(MockHttpServletRequestBuilder builder, MockHttpSession session, int expected)
            throws Exception {
        if (session != null) {
            builder.session(session);
        }
        MvcResult result = mvc.perform(builder.with(csrf())).andExpect(status().is(expected)).andReturn();
        String text = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return text.isBlank() ? json.createObjectNode() : json.readTree(text);
    }

    protected JsonNode post(String url, MockHttpSession s, Object body, int expected) throws Exception {
        return send(MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), s, expected);
    }

    protected JsonNode put(String url, MockHttpSession s, Object body, int expected) throws Exception {
        return send(MockMvcRequestBuilders.put(url)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), s, expected);
    }

    protected JsonNode patchJson(String url, MockHttpSession s, Object body, int expected) throws Exception {
        return send(MockMvcRequestBuilders.patch(url)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), s, expected);
    }

    protected JsonNode getJson(String url, MockHttpSession s, int expected) throws Exception {
        return send(MockMvcRequestBuilders.get(url), s, expected);
    }

    protected JsonNode deleteJson(String url, MockHttpSession s, int expected) throws Exception {
        return send(MockMvcRequestBuilders.delete(url), s, expected);
    }

    // ---- domain helpers ----

    protected JsonNode createTask(MockHttpSession s, String title) throws Exception {
        return post("/api/tasks", s, Map.of("title", title), 201);
    }

    protected JsonNode createTask(MockHttpSession s, Map<String, Object> body) throws Exception {
        return post("/api/tasks", s, body, 201);
    }
}
