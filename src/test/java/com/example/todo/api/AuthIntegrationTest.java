package com.example.todo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.databind.JsonNode;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired PasswordEncoder encoder;

    @Test
    void registrationSucceedsAndNeverReturnsThePassword() throws Exception {
        String email = uniqueEmail();
        JsonNode body = post("/api/auth/register", null,
                Map.of("name", "Asha", "email", email, "password", PASSWORD), 201);
        assertEquals(email, body.get("email").asText());
        assertFalse(body.has("password"));
        assertFalse(body.has("passwordHash"));
    }

    @Test
    void registrationValidatesInput() throws Exception {
        JsonNode badEmail = post("/api/auth/register", null,
                Map.of("name", "A", "email", "not-an-email", "password", PASSWORD), 400);
        assertTrue(badEmail.get("fieldErrors").has("email"));

        JsonNode shortPassword = post("/api/auth/register", null,
                Map.of("name", "A", "email", uniqueEmail(), "password", "abc1"), 400);
        assertTrue(shortPassword.get("fieldErrors").has("password"));

        JsonNode noDigit = post("/api/auth/register", null,
                Map.of("name", "A", "email", uniqueEmail(), "password", "onlyletters"), 400);
        assertTrue(noDigit.get("fieldErrors").has("password"));

        JsonNode blankName = post("/api/auth/register", null,
                Map.of("name", " ", "email", uniqueEmail(), "password", PASSWORD), 400);
        assertTrue(blankName.get("fieldErrors").has("name"));
    }

    @Test
    void duplicateEmailIsRejectedEvenWithDifferentCase() throws Exception {
        String email = uniqueEmail();
        register("First", email, PASSWORD);
        JsonNode error = post("/api/auth/register", null,
                Map.of("name", "Second", "email", email.toUpperCase(), "password", PASSWORD), 409);
        assertTrue(error.get("message").asText().contains("already exists"));
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        String email = uniqueEmail();
        register("Hash Test", email, PASSWORD);
        String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertNotEquals(PASSWORD, stored);
        assertTrue(stored.startsWith("$2"), "expected a BCrypt hash");
        assertTrue(encoder.matches(PASSWORD, stored));
        assertFalse(encoder.matches("WrongPassword1", stored));
    }

    @Test
    void loginWorksWithCorrectCredentialsAndMeReturnsTheUser() throws Exception {
        String email = uniqueEmail();
        register("Login Test", email, PASSWORD);
        MockHttpSession session = login(email, PASSWORD);
        JsonNode me = getJson("/api/auth/me", session, 200);
        assertEquals(email, me.get("email").asText());
        assertEquals("Login Test", me.get("name").asText());
    }

    @Test
    void loginFailsWithWrongPasswordOrUnknownUser() throws Exception {
        String email = uniqueEmail();
        register("Bad Login", email, PASSWORD);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .param("email", email).param("password", "WrongPassword1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .param("email", "nobody@test.example").param("password", PASSWORD))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = newUserSession();
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/logout"),
                session, 204);
        assertTrue(session.isInvalid(), "session should be invalidated by logout");
    }

    @Test
    void privateApisRequireAuthentication() throws Exception {
        getJson("/api/tasks", null, 401);
        getJson("/api/tasks/stats", null, 401);
        getJson("/api/tasks/1", null, 401);
        getJson("/api/categories", null, 401);
        getJson("/api/tags", null, 401);
        getJson("/api/history", null, 401);
        getJson("/api/export/tasks.csv", null, 401);
        getJson("/api/auth/me", null, 401);
    }

    @Test
    void privatePagesRedirectToLoginAndLoginPageIsPublic() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/index.html"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/login.html"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    @Test
    void requestsWithoutCsrfTokenAreRejected() throws Exception {
        MockHttpSession session = newUserSession();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/tasks")
                .session(session).contentType("application/json").content("{\"title\":\"x\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void csrfEndpointIsPublicAndReturnsAToken() throws Exception {
        JsonNode token = getJson("/api/auth/csrf", null, 200);
        assertFalse(token.get("token").asText().isBlank());
        assertTrue(token.has("headerName"));
    }
}
