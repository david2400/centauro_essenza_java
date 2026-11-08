package com.essenza.draco.modules.marketplace.application.port;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.DocumentDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.MovementDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.MovementFilter;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.OptionDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.StockFilter;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.StockRowDto;
import com.essenza.draco.modules.marketplace.application.dto.ConsignmentDtos.StockStatsDto;
import com.essenza.draco.modules.marketplace.domain.consignment.ConsignmentRules.Bin;

/** Consignación: saldos por vendedor, SKU y bodega, documentos y kardex (misma transacción que ventas). */
public interface ConsignmentStore {

    // ── Referencias ───────────────────────────────────────────────────────

    Optional<SellerRef> seller(long companyId, long sellerId);

    /** Bodega activa y no borrada. */
    Optional<String> activeWarehouse(long warehouseId);

    /** SKU existentes (no borrados) de los ids dados, con su código. */
    Map<Long, String> skuCodes(Collection<Long> skuIds);

    /** Oferta con su vendedor y despacho (para saber si una venta sale de consignación). */
    Optional<OfferRef> offer(long offerId);

    /** Oferta de la línea de pedido, si la tiene. */
    Optional<Long> lineOffer(long orderLineId);

    /** Oferta abierta (no cerrada) del vendedor para el SKU con despacho de la plataforma, bloqueada. */
    Optional<OfferRef> lockOpenPlatformOffer(long companyId, long sellerId, long skuId);

    /** Suma {@code delta} al disponible de la oferta (nunca por debajo de 0). @return false si no alcanza */
    boolean adjustOfferAvailable(long offerId, int delta, Instant now);

    /** Unidades reservadas en ventas pendientes de ofertas de la plataforma del vendedor y SKU. */
    int activeReserved(long companyId, long sellerId, long skuId);

    // ── Saldos ────────────────────────────────────────────────────────────

    /** Saldo del vendedor y SKU en la bodega, creado en 0 si no existe; bloqueado. */
    Bin lockOrCreate(long companyId, long sellerId, long skuId, long warehouseId, Instant now);

    /** Saldos del vendedor y SKU en todas las bodegas, bloqueados en orden de id. */
    List<Bin> lockBins(long companyId, long sellerId, long skuId);

    /** Suma {@code delta} al saldo. @return el saldo resultante */
    int addToStock(long stockId, int delta, Instant now);

    // ── Documentos y kardex ───────────────────────────────────────────────

    long insertDocument(long companyId, String kind, long sellerId, long warehouseId, String reference, String notes, int units,
                        String actorId, String actorName, Instant now);

    void setDocumentNumber(long documentId, String number);

    void insertMovement(NewMovement movement);

    /** Línea vendida con lo que pagó el comprador y si la tienda cobra el subpedido (todo de consignación). */
    Optional<LineSale> lineSale(long orderLineId);

    /** ¿Ya existe ese asiento en el estado de cuenta? */
    boolean ledgerKeyExists(long companyId, String idempotencyKey);

    /** Unidades del vendedor en todas las bodegas antes de {@code before} (suma de su kardex). */
    int unitsBefore(long companyId, long sellerId, Instant before);

    /** Movimientos del vendedor en [from, to), con su fecha y cantidad con signo. */
    List<Delta> deltas(long companyId, long sellerId, Instant from, Instant to);

    record Delta(Instant at, int quantity) {
    }

    /** Unidades netas vendidas por bodega para la línea (ventas menos cancelaciones). */
    Map<Long, Integer> soldByStock(long orderLineId);

    // ── Consultas ─────────────────────────────────────────────────────────

    Page<StockRowDto> searchStock(long companyId, StockFilter filter, Instant now, Pageable pageable);

    StockStatsDto stats(long companyId);

    Page<MovementDto> movements(long companyId, MovementFilter filter, Pageable pageable);

    Page<DocumentDto> documents(long companyId, Long sellerId, String kind, Pageable pageable);

    Optional<DocumentDto> document(long companyId, long documentId);

    List<MovementDto> documentLines(long companyId, long documentId);

    /** Vendedores externos con saldo o aprobados (para filtros y formularios). */
    List<OptionDto> sellerOptions(long companyId);

    List<OptionDto> warehouseOptions();

    record SellerRef(long id, String name, String kind, String status) {
    }

    record LineSale(long orderId, String orderNumber, java.math.BigDecimal total, int quantity, boolean storeCollects) {
    }

    record OfferRef(long id, long companyId, long sellerId, long skuId, String fulfillment, String status, int available) {
    }

    record NewMovement(long companyId, long stockId, long sellerId, long skuId, long warehouseId, String kind, int quantity,
                       int balanceAfter, Long documentId, Long orderId, Long orderLineId, Long returnCaseId, String note,
                       String actorId, Instant now) {
    }
}
