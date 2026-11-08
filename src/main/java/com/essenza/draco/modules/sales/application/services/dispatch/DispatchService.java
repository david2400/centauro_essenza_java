package com.essenza.draco.modules.sales.application.services.dispatch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.sales.application.dto.dispatch.DispatchDtos.BackfillResultDto;
import com.essenza.draco.modules.sales.application.dto.dispatch.DispatchDtos.DispatchRowDto;
import com.essenza.draco.modules.sales.application.dto.dispatch.DispatchDtos.DispatchStatsDto;
import com.essenza.draco.modules.sales.application.dto.dispatch.DispatchDtos.SellerLoadDto;
import com.essenza.draco.modules.sales.application.output.repository.DispatchBoard;
import com.essenza.draco.modules.sales.application.output.repository.DispatchBoard.DispatchRow;
import com.essenza.draco.modules.sales.application.output.repository.DispatchBoard.Filter;
import com.essenza.draco.modules.sales.application.output.repository.DispatchBoard.PromiseInput;
import com.essenza.draco.modules.sales.domain.model.dispatch.DispatchPromise;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Despachos por subpedido. Al pagarse una orden se fija su fecha límite de despacho: los días de
 * alistamiento de la oferta en subpedidos de vendedores externos y
 * {@code essenza.orders.handling-days} en los propios. El tablero muestra lo pendiente por
 * despachar (propio y de cada vendedor), lo vencido, lo que va en camino y el cumplimiento.
 * Empresa y permisos salen del token (VENTAS.VER; calcular fechas pendientes, VENTAS.GESTIONAR).
 */
@Service
@Transactional
public class DispatchService {

    static final ZoneId ZONE = ZoneId.of("America/Bogota");
    static final Set<String> VIEWS = Set.of("TO_SHIP", "OVERDUE", "DUE_TODAY", "IN_TRANSIT", "DELIVERED", "ALL");
    static final int BACKFILL_LIMIT = 500;

    private final DispatchBoard board;
    private final ActorProvider actors;
    private final int ownHandlingDays;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public DispatchService(DispatchBoard board, ActorProvider actors,
                           @Value("${essenza.orders.handling-days:1}") int ownHandlingDays) {
        this(board, actors, ownHandlingDays, Clock.system(ZONE));
    }

    /** Para pruebas: reloj fijo. */
    public DispatchService(DispatchBoard board, ActorProvider actors, int ownHandlingDays, Clock clock) {
        this.board = board;
        this.actors = actors;
        this.ownHandlingDays = ownHandlingDays;
        this.clock = clock;
    }

    /**
     * Fija la fecha límite de una orden recién pagada (lo llama el evento de pago, sin usuario).
     *
     * @return la fecha fijada, o null si la orden no se despacha (caja) o no existe
     */
    // Corre dentro de la transacción del pago: un error aquí no debe marcarla para rollback.
    @Transactional(noRollbackFor = RuntimeException.class)
    public LocalDate promise(long orderId) {
        PromiseInput input = board.promiseInput(orderId).orElse(null);
        if (input == null) {
            return null;
        }
        // Dentro de la transacción del pago, paid_at puede no estar escrito aún: se paga hoy.
        LocalDate paidOn = input.paidAt() == null ? LocalDate.now(clock) : LocalDate.ofInstant(input.paidAt(), ZONE);
        int days = input.thirdParty() && input.offerHandlingDays() != null ? input.offerHandlingDays() : ownHandlingDays;
        LocalDate shipBy = DispatchPromise.shipBy(paidOn, days);
        board.setShipBy(orderId, shipBy);
        return shipBy;
    }

    @Transactional(readOnly = true)
    public Page<DispatchRowDto> search(String view, String seller, String q, Pageable pageable) {
        RequestActor actor = actors.current().requireAny("VENTAS.VER", "VENTAS.GESTIONAR");
        String v = view == null || view.isBlank() ? "TO_SHIP" : view.trim().toUpperCase(Locale.ROOT);
        if (!VIEWS.contains(v)) {
            throw new IllegalArgumentException("Vista no válida: " + view + " (TO_SHIP, OVERDUE, DUE_TODAY, IN_TRANSIT, DELIVERED o ALL).");
        }
        String s = seller == null || seller.isBlank() ? null : seller.trim();
        if (s != null && !"own".equalsIgnoreCase(s) && !s.matches("\\d{1,18}")) {
            throw new IllegalArgumentException("Vendedor no válido: usa \"own\" o el id del vendedor.");
        }
        LocalDate today = LocalDate.now(clock);
        return board.search(actor.companyId(), new Filter(v, s, q), today, pageable).map(r -> dto(r, today));
    }

    @Transactional(readOnly = true)
    public DispatchStatsDto stats() {
        RequestActor actor = actors.current().requireAny("VENTAS.VER", "VENTAS.GESTIONAR");
        DispatchBoard.Stats s = board.stats(actor.companyId(), LocalDate.now(clock));
        BigDecimal rate = s.shipped30() == 0 ? null
                : BigDecimal.valueOf(s.onTime30() * 100L).divide(BigDecimal.valueOf(s.shipped30()), 1, RoundingMode.HALF_UP);
        return new DispatchStatsDto(s.toShip(), s.overdue(), s.dueToday(), s.inTransit(), s.delivered30(), s.shipped30(),
                s.onTime30(), rate, s.withoutShipBy(), s.bySeller().stream()
                        .map(l -> new SellerLoadDto(l.sellerId(), l.sellerName(), l.thirdParty(), l.toShip(), l.overdue()))
                        .toList());
    }

    /** Calcula la fecha límite de las órdenes abiertas pagadas antes de esta función (idempotente). */
    public BackfillResultDto backfill() {
        RequestActor actor = actors.current().require("VENTAS.GESTIONAR");
        int updated = 0;
        for (Long id : board.openWithoutShipBy(actor.companyId(), BACKFILL_LIMIT)) {
            if (promise(id) != null) {
                updated++;
            }
        }
        return new BackfillResultDto(updated);
    }

    private static DispatchRowDto dto(DispatchRow r, LocalDate today) {
        boolean pending = "PAID".equals(r.state()) || "PROCESSING".equals(r.state()) || "READY".equals(r.state());
        DispatchPromise.Urgency urgency = DispatchPromise.urgency(r.shipBy(), today, pending);
        return new DispatchRowDto(r.orderId(), r.orderNumber(), r.state(), r.deliveryMethod(), r.purchaseId(),
                r.purchaseNumber(), r.sellerId(), r.sellerName(), r.thirdParty(), r.customerName(), r.shipCity(), r.total(),
                r.paidAt(), r.shipBy(), urgency.name(), pending ? DispatchPromise.daysLate(r.shipBy(), today) : 0,
                r.shippedAt(), r.deliveredAt(), r.carrierId(), r.carrierName(), r.trackingNumber(), r.consignment());
    }
}
