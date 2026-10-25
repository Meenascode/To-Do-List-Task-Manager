package com.example.todo.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.todo.dto.TagRef;
import com.example.todo.model.Task;
import com.example.todo.repository.TagRepository;

/** Builds an RFC 4180 CSV of the user's tasks, protected against spreadsheet formula injection. */
@Service
public class CsvExportService {

    static final String HEADER = "ID,Parent ID,Title,Description,Status,Priority,Category,Due Date,Tags,Created At,Updated At";

    private final TaskService taskService;
    private final TagRepository tags;

    public CsvExportService(TaskService taskService, TagRepository tags) {
        this.taskService = taskService;
        this.tags = tags;
    }

    @Transactional(readOnly = true)
    public String buildCsv(long userId) {
        List<Task> rows = taskService.findAllForExport(userId);
        Map<Long, List<TagRef>> tagMap = tags.findByTaskIds(rows.stream().map(Task::id).toList());
        StringBuilder out = new StringBuilder(HEADER).append("\r\n");
        for (Task t : rows) {
            String tagText = tagMap.getOrDefault(t.id(), List.of()).stream().map(TagRef::name)
                    .collect(Collectors.joining("; "));
            out.append(String.join(",",
                    String.valueOf(t.id()),
                    t.parentId() == null ? "" : String.valueOf(t.parentId()),
                    escape(t.title()),
                    escape(t.description()),
                    t.status().getLabel(),
                    t.priority().getLabel(),
                    escape(t.categoryName()),
                    date(t.dueDate()),
                    escape(tagText),
                    dateTime(t.createdAt()),
                    dateTime(t.updatedAt()))).append("\r\n");
        }
        return out.toString();
    }

    /**
     * Makes one text value safe for CSV: neutralises leading = + - @ (formula injection) and
     * quotes the value when it contains a comma, quote or line break (quotes are doubled).
     */
    public static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String v = value;
        char first = v.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                || first == '\n') {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String date(LocalDate d) {
        return d == null ? "" : d.toString();
    }

    private static String dateTime(LocalDateTime d) {
        return d == null ? "" : d.toString().replace('T', ' ');
    }
}
