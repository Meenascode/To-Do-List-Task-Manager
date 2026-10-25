package com.example.todo.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.todo.security.AuthUser;
import com.example.todo.service.CsvExportService;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final CsvExportService exportService;

    public ExportController(CsvExportService exportService) {
        this.exportService = exportService;
    }

    /** Downloads the logged-in user's tasks. A UTF-8 BOM is added so Excel shows non-English text correctly. */
    @GetMapping("/tasks.csv")
    public ResponseEntity<byte[]> exportTasks(@AuthenticationPrincipal AuthUser user) {
        byte[] body = ("\uFEFF" + exportService.buildCsv(user.getId())).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tasks.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
