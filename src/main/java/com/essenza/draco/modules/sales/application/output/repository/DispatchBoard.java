package com.essenza.draco.modules.sales.application.output.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Despachos por subpedido: fecha límite y tablero (órdenes propias y de vendedores externos). */
public interface DispatchBoard {

    /** Datos para calcular la fecha límite; vacío si la orden no existe o es de caja (POS). */
    Optional<PromiseInput> promiseInput(long orderId);

    void setShipBy(long orderId, LocalDate shipBy);

    /** Órdenes abiertas (pagadas, en preparación o listas) sin fecha límite, con su fecha de pago. */
    List<Long> openWithoutShipBy(long companyId, int limit);

    Page<DispatchRow> search(long companyId, Filter filter, LocalDate today, Pageable pageable);

    Stats stats(long companyId, LocalDate today);

    /**
     * @param offerHandlingDays mayor alistamiento de las ofertas de sus líneas (null si no tiene ofertas)
     * @param thirdParty        subpedido de un vendedor externo
     */
    record PromiseInput(long orderId, Instant paidAt, Integer offerHandlingDays, boolean thirdParty) {
    }

    /**
     * @param view   TO_SHIP, OVERDUE, DUE_TODAY, IN_TRANSIT, DELIVERED o ALL
     * @param seller "own" (tienda propia), id de vendedor o null (todos)
     */
    record Filter(String view, String seller, String q) {
    }

    record DispatchRow(long orderId, String orderNumber, String state, String deliveryMethod, Long purchaseId,
                       String purchaseNumber, Long sellerId, String sellerName, boolean thirdParty, String customerName,
                       String shipCity, BigDecimal total, Instant paidAt, LocalDate shipBy, Instant shippedAt,
                       Instant deliveredAt, Long carrierId, String carrierName, String trackingNumber, boolean consignment) {
    }

    /**
     * @param shipped30 despachados en los últimos 30 días con fecha límite
     * @param onTime30  de esos, los despachados a más tardar en su fecha límite
     */
    record Stats(long toShip, long overdue, long dueToday, long inTransit, long delivered30, long shipped30, long onTime30,
                 long withoutShipBy, List<SellerLoad> bySeller) {
    }

    record SellerLoad(Long sellerId, String sellerName, boolean thirdParty, long toShip, long overdue) {
    }
}
