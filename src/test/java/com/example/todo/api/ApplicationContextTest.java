package com.example.todo.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.SecurityFilterChain;

import com.example.todo.service.TaskService;

class ApplicationContextTest extends AbstractIntegrationTest {

    @Autowired ApplicationContext context;

    @Test
    void contextStartsAndKeyBeansExist() {
        assertNotNull(context.getBean(TaskService.class));
        assertNotNull(context.getBean(SecurityFilterChain.class));
        assertNotNull(context.getBean(JdbcTemplate.class));
    }

    @Test
    void schemaIsCreatedInTheTestDatabase() {
        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME IN "
                        + "('USERS','TASKS','CATEGORIES','TAGS','TASK_TAGS','TASK_HISTORY')", Integer.class);
        assertTrue(tables != null && tables == 6);
    }
}
