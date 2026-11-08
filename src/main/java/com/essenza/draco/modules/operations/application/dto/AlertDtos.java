package com.essenza.draco.modules.operations.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Centro de operaciones (JSON en snake_case). */
public final class AlertDtos {

    private AlertDtos() {
    }

    /**
     * Una alerta con su conteo, el monto si aplica y hasta {@code SAMPLE} ejemplos. {@code available}
     * = false si no se pudo calcular (p. ej. el módulo no está instalado en esta base).
     */
    public record AlertDto(String key, String area, String severity, long count, BigDecimal amount, boolean available,
                           List<AlertItemDto> items) {
    }

    /** Ejemplo enlazable: {@code refType} dice a qué ficha lleva (ORDER, SELLER, INVOICE, RETURN, OFFER, WAREHOUSE, COMMISSION, DISPUTE, PURCHASE_ORDER). */
    public record AlertItemDto(String refType, Long refId, String label, String detail, BigDecimal amount, Instant at,
                               Long days) {
    }

    public record AlertsDto(Instant generatedAt, long critical, long warning, long info, int consignmentAgedDays,
                            int returnsStalledDays, List<AlertDto> alerts) {
    }
}
