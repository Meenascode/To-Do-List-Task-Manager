package com.example.todo.dto;

import java.util.List;
import java.util.Map;

import com.example.todo.model.HistoryEntry;

public record StatsView(String today, int total, int todo, int inProgress, int completed,
                        int overdue, int dueToday, int completionPercent,
                        Map<String, Integer> priorityDistribution, List<DayStat> weekly,
                        int completedThisWeek, int completedLastWeek,
                        List<TaskView> upcoming, List<HistoryEntry> recentActivity) {

    public record DayStat(String date, int created, int completed) {
    }
}
