package com.essenza.draco.modules.sales.application.dto.order;

import java.math.BigDecimal;
import java.util.Map;

/**
 * KPIs del listado (con los mismos filtros): ventas sin canceladas, ticket promedio y colas
 * de trabajo (por cobrar, por preparar, listas, despachadas y vencidas).
 * <p>
 * {@code grossSales} es el total vendido (incluye subpedidos de vendedores externos);
 * {@code ownSales} lo vendido por la tienda y {@code marketplaceSales} lo de terceros, que no es
 * ingreso propio (lo cobra el vendedor; la tienda gana la comisión).
 */
public record OrderSummaryDto(long count, BigDecimal grossSales, BigDecimal averageTicket, long pendingPayment,
                              long toPrepare, long ready, long shipped, long delivered, long cancelled, long overdue,
                              Map<String, Long> byState, BigDecimal ownSales, BigDecimal marketplaceSales) {
}
