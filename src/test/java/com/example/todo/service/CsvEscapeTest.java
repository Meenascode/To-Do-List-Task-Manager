package com.example.todo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pure unit tests (no Spring) for the CSV escaping rules. */
class CsvEscapeTest {

    @Test
    void plainTextIsUnchanged() {
        assertEquals("hello world", CsvExportService.escape("hello world"));
        assertEquals("", CsvExportService.escape(null));
        assertEquals("", CsvExportService.escape(""));
    }

    @Test
    void commasQuotesAndNewlinesAreQuoted() {
        assertEquals("\"a,b\"", CsvExportService.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExportService.escape("say \"hi\""));
        assertEquals("\"line1\nline2\"", CsvExportService.escape("line1\nline2"));
    }

    @Test
    void formulaCharactersGetALeadingApostrophe() {
        assertEquals("'=1+1", CsvExportService.escape("=1+1"));
        assertEquals("'+1", CsvExportService.escape("+1"));
        assertEquals("'-1", CsvExportService.escape("-1"));
        assertEquals("'@cmd", CsvExportService.escape("@cmd"));
        assertEquals("a=b", CsvExportService.escape("a=b"));
    }
}
