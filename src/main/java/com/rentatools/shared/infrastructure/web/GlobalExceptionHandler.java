package com.rentatools.shared.infrastructure.web;

import com.rentatools.shared.application.exception.ConflictException;
import com.rentatools.shared.application.exception.ForbiddenException;
import com.rentatools.shared.application.exception.NotFoundException;
import com.rentatools.shared.domain.BusinessRuleViolation;
import com.rentatools.shared.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Manejador global de excepciones que traduce errores de dominio, aplicación y validación
 * a respuestas RFC 9457 {@link ProblemDetail}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.NOT_FOUND, "Not Found", ex.code(), ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.FORBIDDEN, "Forbidden", ex.code(), ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.CONFLICT, "Conflict", ex.code(), ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleViolation.class)
    public ProblemDetail handleBusinessRuleViolation(BusinessRuleViolation ex, HttpServletRequest request) {
        return buildProblem(HttpStatus.CONFLICT, "Business Rule Violation", ex.code(), ex.getMessage(), request);
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainException(DomainException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.code()) {
            case "INVALID_CREDENTIALS", "INVALID_REFRESH_TOKEN" -> HttpStatus.UNAUTHORIZED;
            case "USER_SUSPENDED", "NOT_TOOL_OWNER", "NOT_RESERVATION_PARTICIPANT" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.CONFLICT;
        };
        return buildProblem(status, status.getReasonPhrase(), ex.code(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                "VALIDATION_ERROR",
                "Request validation failed",
                request
        );
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                "VALIDATION_ERROR",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                "MALFORMED_REQUEST",
                "Malformed JSON request body",
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                "BAD_REQUEST",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "FORBIDDEN",
                ex.getMessage(),
                request
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return buildProblem(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "UNAUTHORIZED",
                ex.getMessage(),
                request
        );
    }

    private static ProblemDetail buildProblem(
            HttpStatus status,
            String title,
            String code,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://rentatools.com/problems/" + code.toLowerCase().replace('_', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }
}
