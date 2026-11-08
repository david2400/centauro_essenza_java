package com.essenza.draco.shared.common.marketplace;

import java.util.List;
import java.util.Set;

/**
 * Contrato entre postventa y marketplace: las unidades devueltas por clientes de líneas vendidas
 * desde consignación vuelven al stock del vendedor, no al inventario propio. Se ejecuta dentro
 * de la transacción de la inspección. Lo implementa el marketplace.
 */
public interface ConsignmentReturns {

    /**
     * Reingresa a consignación las unidades de las líneas que se vendieron desde ella.
     *
     * @return ids de las líneas de pedido que tomó (las demás van al inventario propio)
     */
    Set<Long> receiveReturn(Long orderId, Long returnCaseId, Long warehouseId, List<ReturnedLine> lines);

    record ReturnedLine(Long orderLineId, Long skuId, int quantity) {
    }

    /** Sin marketplace: nada es de consignación. */
    ConsignmentReturns NONE = (orderId, returnCaseId, warehouseId, lines) -> Set.of();
}
