package com.example.todo.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.todo.dto.StatsView;
import com.example.todo.dto.StatsView.DayStat;
import com.example.todo.model.TaskPriority;
import com.example.todo.repository.HistoryRepository;
import com.example.todo.repository.StatsRepository;
import com.example.todo.repository.StatsRepository.Counts;

@Service
public class StatsService {

    private final StatsRepository stats;
    private final HistoryRepository history;
    private final TaskService taskService;
    private final Clock clock;

    public StatsService(StatsRepository stats, HistoryRepository history, TaskService taskService, Clock clock) {
        this.stats = stats;
        this.history = history;
        this.taskService = taskService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StatsView compute(long userId) {
        LocalDate today = LocalDate.now(clock);
        Counts c = stats.counts(userId, today);
        int percent = c.total() == 0 ? 0 : Math.round(c.completed() * 100f / c.total());

        Map<String, Integer> priorities = new LinkedHashMap<>();
        Map<String, Integer> fromDb = stats.priorityCounts(userId);
        for (TaskPriority p : TaskPriority.values()) {
            priorities.put(p.name(), fromDb.getOrDefault(p.name(), 0));
        }

        // Last 14 days of data: the most recent 7 feed the chart, the 7 before are used for the trend.
        LocalDate start = today.minusDays(13);
        Map<LocalDate, Integer> completedPerDay = stats.completedPerDay(userId, start);
        Map<LocalDate, Integer> createdPerDay = stats.createdPerDay(userId, start);
        List<DayStat> weekly = new ArrayList<>();
        int thisWeek = 0;
        int lastWeek = 0;
        for (int i = 13; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            int done = completedPerDay.getOrDefault(day, 0);
            if (i <= 6) {
                weekly.add(new DayStat(day.toString(), createdPerDay.getOrDefault(day, 0), done));
                thisWeek += done;
            } else {
                lastWeek += done;
            }
        }

        return new StatsView(today.toString(), c.total(), c.todo(), c.inProgress(), c.completed(), c.overdue(),
                c.dueToday(), percent, priorities, weekly, thisWeek, lastWeek,
                taskService.upcoming(userId, 5), history.findRecent(userId, 8));
    }
}
