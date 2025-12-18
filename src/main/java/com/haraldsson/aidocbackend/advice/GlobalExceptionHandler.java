package com.haraldsson.aidocbackend.advice;

import com.haraldsson.aidocbackend.advice.dto.SimpleErrorResponse;
import com.haraldsson.aidocbackend.advice.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<SimpleErrorResponse> handleUnauthorizedException(
            UnauthorizedException ex) {

        log.warn("Unauthorized access attempt: {}", ex.getMessage());

        SimpleErrorResponse errorResponse = new SimpleErrorResponse(
                ex.getErrorCode(),
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorResponse);
    }

    @ExceptionHandler(FileProcessingException.class)
    public ResponseEntity<SimpleErrorResponse> handleFileProcessingException(
            FileProcessingException ex, ServerWebExchange exchange) {

        log.error("File processing error: {}", ex.getMessage(), ex);

        SimpleErrorResponse errorResponse = new SimpleErrorResponse(
                ex.getErrorCode(),
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<SimpleErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, ServerWebExchange exchange) {

        log.warn("Resource not found: {}", ex.getMessage());

        SimpleErrorResponse errorResponse = new SimpleErrorResponse(
                ex.getErrorCode(),
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<SimpleErrorResponse> handleValidationException(
            ValidationException ex, ServerWebExchange exchange) {

        log.warn("Validation error: {}", ex.getMessage());

        SimpleErrorResponse errorResponse = new SimpleErrorResponse(
                ex.getErrorCode(),
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<SimpleErrorResponse> handleWebExchangeBindException(
            WebExchangeBindException ex, ServerWebExchange exchange) {

        if (log.isDebugEnabled()) {
            String errorDetails = ex.getFieldErrors().stream()
                    .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            log.debug("Request validation failed: {}", errorDetails);
        } else {
            log.warn("Request validation failed for {} {}",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath());
        }

        String errorDetails = ex.getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));

        SimpleErrorResponse errorResponse = new SimpleErrorResponse(
                "REQUEST_VALIDATION_ERROR",
                "Invalid request parameters: " + errorDetails
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<SimpleErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, ServerWebExchange exchange) {

        log.warn("Access denied: {}", ex.getMessage());

        SimpleErrorResponse errorResponse = new SimpleErrorResponse(
                "ACCESS_DENIED",
                "You don't have permission to access this resource"
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorResponse);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<SimpleErrorResponse> handleBusinessException(
            BusinessException ex, ServerWebExchange exchange) {

        log.error("Business error: {}", ex.getMessage(), ex);

        SimpleErrorResponse errorResponse = new SimpleErrorResponse(
                ex.getErrorCode(),
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<SimpleErrorResponse> handleGenericException(
            Exception ex, ServerWebExchange exchange) {

        log.error("Unhandled exception occurred: {}", ex.getMessage(), ex);

        SimpleErrorResponse errorResponse = new SimpleErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<SimpleErrorResponse> handleServerWebInputException(
            ServerWebInputException ex, ServerWebExchange exchange) {

        log.warn("Invalid request input: {}", ex.getMessage());

        if (ex.getMessage() != null && ex.getMessage().contains("No request body")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new SimpleErrorResponse(
                            "MISSING_REQUEST_BODY",
                            "Request body is required"
                    ));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new SimpleErrorResponse(
                        "INVALID_REQUEST",
                        "Invalid request: " + ex.getReason()
                ));
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public Mono<ResponseEntity<String>> handleHttpMessageNotWritableException(
            HttpMessageNotWritableException ex, ServerWebExchange exchange) {

        log.error("HTTP message not writable: {}", ex.getMessage());

        return Mono.just(ResponseEntity.status(500)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Internal server error"));
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public Mono<ResponseEntity<SimpleErrorResponse>> handleDatabaseConnectionError(
            DataAccessResourceFailureException ex, ServerWebExchange exchange) {

        log.error("Database connection failed: {}", ex.getMessage());


        if (exchange.getResponse().isCommitted()) {
            log.debug("Response already committed, skipping database error response");
            return Mono.empty();
        }

        SimpleErrorResponse errorResponse = new SimpleErrorResponse(
                "DATABASE_CONNECTION_ERROR",
                "Database connection lost. Please try again.");

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse));
    }



    @ExceptionHandler(org.springframework.r2dbc.UncategorizedR2dbcException.class)
    public Mono<ResponseEntity<SimpleErrorResponse>> handleR2dbcException(
            org.springframework.r2dbc.UncategorizedR2dbcException ex,
            ServerWebExchange exchange) {

        log.error("R2DBC error occurred: {}", ex.getMessage());

        if (exchange.getResponse().isCommitted()) {
            log.debug("Response already committed, skipping R2DBC error response");
            return Mono.empty();
        }

        SimpleErrorResponse errorResponse = new SimpleErrorResponse(
                "DATABASE_ERROR",
                "Database operation failed. Please try again later."
        );

        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorResponse));
    }
}