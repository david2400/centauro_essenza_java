package com.essenza.draco.shared.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Id de petición para seguir una operación de punta a punta.
 *
 * <p>Toma {@code X-Request-Id} si el panel lo envía (y es seguro: letras, números, {@code -_.},
 * hasta 64 caracteres) o genera uno. Lo deja en el MDC ({@code requestId}) para que salga en cada
 * línea de log y lo devuelve en la respuesta y en el cuerpo de error ({@code ApiErrorResponse}).
 * Corre antes que la cadena de seguridad para que también quede en los 401/403.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && SAFE.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Id de la petición en curso (o {@code null} fuera de una petición HTTP, p. ej. en un job). */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
