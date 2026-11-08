package com.essenza.draco.modules.marketplace.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.DisputeEventDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.DisputeStatsDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.DisputeSummaryDto;
import com.essenza.draco.modules.marketplace.application.dto.DisputeDtos.OrderDisputeDto;

/** Reclamos: datos de la orden reclamada, el reclamo, su historial y consultas. */
public interface DisputeStore {

    /** Orden con su vendedor; vacío si no existe o su vendedor es de otra empresa. */
    Optional<OrderRef> order(long companyId, long orderId);

    boolean hasOpen(long orderId);

    long insert(long companyId, OrderRef order, String reason, String description, BigDecimal claimed, LocalDate respondBy,
                String actorId, String actorName, Instant now);

    void setNumber(long id, String number);

    Optional<DisputeRow> find(long companyId, long id, boolean forUpdate);

    boolean respond(long id, int lockVersion, String response, Instant now);

    boolean resolve(long id, int lockVersion, String status, String resolution, BigDecimal amount, BigDecimal sellerCharge,
                    String note, Long devolutionId, String actorId, Instant now);

    boolean cancel(long id, int lockVersion, String reason, String actorId, Instant now);

    void addEvent(long disputeId, String action, String from, String to, String note, String actorId, String actorName, Instant now);

    List<DisputeEventDto> events(long disputeId);

    Page<DisputeSummaryDto> search(long companyId, String status, Long sellerId, String q, LocalDate today, Pageable pageable);

    DisputeStatsDto stats(long companyId, LocalDate today);

    Optional<OrderDisputeDto> latestForOrder(long companyId, long orderId);

    /** ¿La devolución es de esa orden? */
    boolean devolutionOfOrder(long devolutionId, long orderId);

    record OrderRef(long id, String number, String state, BigDecimal total, long sellerId, String sellerName, String sellerKind,
                    Long purchaseId, String customerName) {
    }

    record DisputeRow(DisputeSummaryDto summary, String description, String sellerResponse, Instant respondedAt,
                      BigDecimal sellerCharge, String resolutionNote, Long devolutionId, Instant resolvedAt, String resolvedBy,
                      BigDecimal orderTotal, String orderState, int lockVersion, String createdByName) {
    }
}
