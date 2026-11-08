package com.essenza.draco.modules.marketplace.infrastructure.outbound.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.essenza.draco.shared.exceptions.ConflictException;

/** Utilidades de los adaptadores JDBC del marketplace. Fechas y horas siempre en UTC. */
final class JdbcSupport {

    private JdbcSupport() {
    }

    static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** Se lee la hora "de pared" guardada (UTC) sin conversiones de zona del driver ni de la JVM. */
    static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    static java.time.LocalDate date(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, java.time.LocalDate.class);
    }

    /** {@code %texto%} escapando comodines del usuario. */
    static String like(String q) {
        String value = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** ORDER BY a partir del Pageable, solo con columnas de la lista blanca. */
    static String orderBy(Pageable pageable, Map<String, String> columns, String fallback) {
        StringJoiner joiner = new StringJoiner(", ");
        for (Sort.Order order : pageable.getSort()) {
            String column = columns.get(order.getProperty());
            if (column != null) {
                joiner.add(column + (order.isAscending() ? " ASC" : " DESC"));
            }
        }
        String clause = joiner.length() == 0 ? fallback : joiner + ", " + fallback;
        return " ORDER BY " + clause;
    }

    /**
     * Traduce una llave única violada a un 409 con mensaje para el usuario. Se busca el nombre
     * del índice en el mensaje (según la versión, Spring lo traduce a DuplicateKeyException o a
     * DataIntegrityViolationException). Otras violaciones se propagan tal cual.
     */
    static RuntimeException duplicate(DataIntegrityViolationException ex, Map<String, String> messages) {
        Throwable cause = ex.getMostSpecificCause();
        String text = String.valueOf(cause == null ? ex.getMessage() : cause.getMessage());
        for (Map.Entry<String, String> entry : messages.entrySet()) {
            if (text.contains(entry.getKey())) {
                return new ConflictException(entry.getValue());
            }
        }
        return ex;
    }
}
