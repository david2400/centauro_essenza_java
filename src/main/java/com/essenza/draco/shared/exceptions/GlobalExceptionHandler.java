package com.essenza.draco.shared.exceptions;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;


/**
 * Manejo de errores uniforme para todos los módulos del API.
 *
 * Devuelve {@link ApiErrorResponse} con un {@code message} legible (el
 * frontend lo muestra tal cual) y {@code field_errors} en validaciones, con
 * los nombres de campo en snake_case, igual que el JSON del API.
 * Antes, la respuesta por defecto de Spring no incluía el mensaje y las
 * pantallas solo mostraban "API request failed with status 500".
 */
@RestControllerAdvice(basePackages = "com.essenza.draco.modules")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> notFound(NotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> conflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> unauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> forbidden(ForbiddenException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> integrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        // Normalmente un índice único (nombre/slug repetido, incluso en un
        // registro eliminado lógicamente) o una FK en uso.
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY",
                "El registro entra en conflicto con datos existentes (valor duplicado o en uso).", request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> invalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiErrorResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldError(FieldPaths.toSnakeCase(error.getField()), error.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION", summary(fields), request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> constraint(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiErrorResponse.FieldError> fields = ex.getConstraintViolations().stream()
                .map(violation -> new ApiErrorResponse.FieldError(
                        FieldPaths.toSnakeCase(violation.getPropertyPath().toString()), violation.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION", summary(fields), request, fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> badRequest(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Solicitud inválida: " + ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> status(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatusCode code = ex.getStatusCode();
        HttpStatus status = HttpStatus.resolve(code.value());
        String message = ex.getReason() != null ? ex.getReason() : (status != null ? status.getReasonPhrase() : "Error");
        return build(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR, "STATUS", message, request, List.of());
    }

    /**
     * Último recurso: se registra el detalle y se responde un mensaje
     * genérico (no se exponen trazas ni SQL al cliente).
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> unexpected(RuntimeException ex, HttpServletRequest request) {
        // Varios adaptadores heredados lanzan RuntimeException("X not found"): es un 404.
        String message = ex.getMessage();
        if (message != null && message.matches("(?is).*(not found|no encontrad).*")) {
            return build(HttpStatus.NOT_FOUND, "NOT_FOUND", message, request, List.of());
        }
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ocurrió un error interno al procesar la solicitud.", request, List.of());
    }

    private static String summary(List<ApiErrorResponse.FieldError> fields) {
        if (fields.isEmpty()) {
            return "Datos inválidos.";
        }
        return "Datos inválidos: " + fields.stream()
                .map(field -> field.field() + " " + field.message())
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }

    private static ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message,
                                                          HttpServletRequest request,
                                                          List<ApiErrorResponse.FieldError> fields) {
        ApiErrorResponse body = new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(),
                code, message, request.getRequestURI(), fields);
        return ResponseEntity.status(status).body(body);
    }
}
