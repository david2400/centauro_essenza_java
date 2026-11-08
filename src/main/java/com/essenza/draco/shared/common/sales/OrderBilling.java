package com.essenza.draco.shared.common.sales;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Contrato de ventas para facturación: la orden con sus líneas, el impuesto congelado
 * en cada una y los tipos de pago aprobados. Lo implementa ventas.
 */
public interface OrderBilling {

    record BillableLine(Long lineId, String skuCode, String description, int quantity, BigDecimal unitPrice,
                        BigDecimal discount, BigDecimal total, String taxCode, String taxKind, BigDecimal taxRate,
                        BigDecimal taxBase, BigDecimal taxAmount) {
    }

    /**
     * @param paidKinds tipos de medio de los pagos aprobados (CASH, CARD_TERMINAL…), para el medio de pago DIAN
     */
    /**
     * @param thirdPartySale subpedido de un vendedor externo del marketplace: lo factura ese vendedor, no la tienda
     */
    record BillableOrder(Long id, String state, BigDecimal total, BigDecimal taxAmount, BigDecimal netAmount,
                         List<BillableLine> lines, List<String> paidKinds, String channel, BillableBuyer buyer,
                         boolean thirdPartySale) {

        public BillableOrder(Long id, String state, BigDecimal total, BigDecimal taxAmount, BigDecimal netAmount,
                             List<BillableLine> lines, List<String> paidKinds, String channel, BillableBuyer buyer) {
            this(id, state, total, taxAmount, netAmount, lines, paidKinds, channel, buyer, false);
        }

        public BillableOrder(Long id, String state, BigDecimal total, BigDecimal taxAmount, BigDecimal netAmount,
                             List<BillableLine> lines, List<String> paidKinds, String channel) {
            this(id, state, total, taxAmount, netAmount, lines, paidKinds, channel, null);
        }

        public BillableOrder(Long id, String state, BigDecimal total, BigDecimal taxAmount, BigDecimal netAmount,
                             List<BillableLine> lines, List<String> paidKinds) {
            this(id, state, total, taxAmount, netAmount, lines, paidKinds, null, null);
        }
    }

    /** Datos de facturación del cliente congelados en la orden (V16); nulo = consumidor final. */
    record BillableBuyer(String docType, String docNumber, String name, String email, String phone, String address,
                         String city) {
    }

    Optional<BillableOrder> find(Long orderId);
}
