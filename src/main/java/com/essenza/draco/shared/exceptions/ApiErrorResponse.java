package com.essenza.draco.shared.exceptions;

import java.time.Instant;
import java.util.List;

import com.essenza.draco.shared.observability.RequestIdFilter;

/**
 * Cuerpo de error uniforme. El frontend muestra {@code message} y, en
 * errores de validación, puede marcar cada campo con {@code fieldErrors}.
 * {@code requestId} identifica la petición en los logs (soporte: "ref" del mensaje).
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldError> fieldErrors,
        String requestId) {

    /** Constructor previo: toma el id de la petición en curso. */
    public ApiErrorResponse(Instant timestamp, int status, String error, String code, String message, String path,
            List<FieldError> fieldErrors) {
        this(timestamp, status, error, code, message, path, fieldErrors, RequestIdFilter.current());
    }

    public record FieldError(String field, String message) {
    }
}
