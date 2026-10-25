package com.example.todo.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Turns every exception into the same JSON error shape and never leaks internal details. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String timestamp, int status, String error, String message,
                            Map<String, String> fieldErrors) {
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException ex) {
        return build(ex.getStatus(), ex.getMessage(), null);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Object> handleDuplicate(DuplicateKeyException ex) {
        return build(HttpStatus.CONFLICT, "A record with the same unique value already exists.", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getClass().getSimpleName());
        return build(HttpStatus.CONFLICT, "The data conflicts with existing records.", null);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Object> handleDatabase(DataAccessException ex) {
        log.error("Database error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "A database error occurred. Please try again.", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAny(Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.", null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        String message = fieldErrors.isEmpty() ? "Validation failed." : fieldErrors.values().iterator().next();
        return build(HttpStatus.BAD_REQUEST, message, fieldErrors);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        return build(HttpStatus.valueOf(statusCode.value()), safeMessage(statusCode.value()), null);
    }

    private static String safeMessage(int status) {
        return switch (status) {
            case 400 -> "The request could not be understood or contains invalid data.";
            case 404 -> "Resource not found.";
            case 405 -> "This method is not allowed here.";
            case 406, 415 -> "Unsupported media type.";
            default -> "The request failed.";
        };
    }

    private static ResponseEntity<Object> build(HttpStatus status, String message, Map<String, String> fields) {
        ErrorBody body = new ErrorBody(Instant.now().toString(), status.value(), status.getReasonPhrase(),
                message, fields);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
