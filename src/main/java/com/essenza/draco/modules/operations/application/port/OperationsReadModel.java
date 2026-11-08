package com.essenza.draco.modules.operations.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.essenza.draco.modules.operations.application.dto.AlertDtos.AlertItemDto;
import com.essenza.draco.modules.operations.domain.AlertCatalog.Thresholds;

/** Lecturas de solo consulta sobre ventas, marketplace, consignación, facturación, postventa e inventario. */
public interface OperationsReadModel {

    int SAMPLE = 5;

    /**
     * Calcula una alerta del catálogo.
     *
     * @throws IllegalArgumentException si la clave no existe
     */
    AlertData compute(String key, long companyId, LocalDate today, Thresholds thresholds);

    record AlertData(long count, BigDecimal amount, List<AlertItemDto> items) {
    }
}
