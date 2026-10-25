package com.example.todo.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.todo.dto.CategoryRequest;
import com.example.todo.dto.CategoryView;
import com.example.todo.dto.RegisterRequest;
import com.example.todo.dto.TaskRequest;
import com.example.todo.dto.TaskView;
import com.example.todo.dto.UserView;
import com.example.todo.repository.UserRepository;

/**
 * Optional demo account. Disabled by default and it never has a built-in password: the person starting
 * the app must supply one, e.g.  --app.demo.enabled=true --app.demo.password=YourOwn1Password
 */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_EMAIL = "demo@example.com";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AuthService auth;
    private final UserRepository users;
    private final CategoryService categories;
    private final TaskService tasks;
    private final Clock clock;
    private final String password;

    public DemoDataSeeder(AuthService auth, UserRepository users, CategoryService categories, TaskService tasks,
                          Clock clock, @Value("${app.demo.password:}") String password) {
        this.auth = auth;
        this.users = users;
        this.categories = categories;
        this.tasks = tasks;
        this.clock = clock;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            log.warn("app.demo.enabled=true but app.demo.password is empty - demo account NOT created.");
            return;
        }
        if (users.existsByEmail(DEMO_EMAIL)) {
            log.info("Demo account already exists - nothing to do.");
            return;
        }
        UserView user = auth.register(new RegisterRequest("Demo User", DEMO_EMAIL, password));
        CategoryView work = categories.create(user.id(), new CategoryRequest("Work", "#2563EB"));
        CategoryView study = categories.create(user.id(), new CategoryRequest("Study", "#06B6D4"));
        CategoryView home = categories.create(user.id(), new CategoryRequest("Home", "#F59E0B"));
        LocalDate today = LocalDate.now(clock);

        TaskView report = tasks.create(user.id(), new TaskRequest("Finish project report",
                "Complete the final chapter and proofread.", "IN_PROGRESS", "HIGH",
                today.plusDays(2).toString(), study.id(), List.of("report", "college"), null), null);
        tasks.create(user.id(), new TaskRequest("Collect references", "", "COMPLETED", "MEDIUM", null, null, null, null),
                report.id());
        tasks.create(user.id(), new TaskRequest("Draw circuit diagrams", "", "TODO", "MEDIUM", null, null, null, null),
                report.id());
        tasks.create(user.id(), new TaskRequest("Prepare viva slides", "", "TODO", "URGENT",
                today.plusDays(1).toString(), study.id(), List.of("college"), null), null);
        tasks.create(user.id(), new TaskRequest("Submit timesheet", "Weekly timesheet is overdue.", "TODO", "HIGH",
                today.minusDays(2).toString(), work.id(), List.of("admin"), null), null);
        tasks.create(user.id(), new TaskRequest("Team stand-up", "", "TODO", "LOW", today.toString(), work.id(),
                null, null), null);
        tasks.create(user.id(), new TaskRequest("Pay electricity bill", "", "COMPLETED", "MEDIUM",
                today.minusDays(1).toString(), home.id(), List.of("admin"), null), null);
        tasks.create(user.id(), new TaskRequest("Buy groceries", "", "TODO", "LOW", today.plusDays(4).toString(),
                home.id(), null, null), null);
        log.info("Demo account created: {} (password supplied via app.demo.password).", DEMO_EMAIL);
    }
}
