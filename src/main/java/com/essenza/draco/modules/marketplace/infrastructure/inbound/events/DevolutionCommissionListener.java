package com.essenza.draco.modules.marketplace.infrastructure.inbound.events;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.essenza.draco.modules.marketplace.application.service.CommissionService;
import com.essenza.draco.shared.common.devolution.DevolutionRefundedEvent;

/**
 * Tras confirmarse el reembolso de una devolución, reversa la comisión de la venta según la
 * política del plan. Idempotente por devolución; un fallo no afecta la devolución (se registra).
 */
@Component
public class DevolutionCommissionListener {

    private static final Logger log = LoggerFactory.getLogger(DevolutionCommissionListener.class);

    private final CommissionService commissions;

    public DevolutionCommissionListener(CommissionService commissions) {
        this.commissions = commissions;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRefunded(DevolutionRefundedEvent event) {
        try {
            BigDecimal refunded = event.lines() == null ? BigDecimal.ZERO : event.lines().stream()
                    .map(line -> line.amount() == null ? BigDecimal.ZERO : line.amount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            commissions.refundedAfterCommit(event.devolutionId(), event.orderId(), refunded);
        } catch (RuntimeException ex) {
            log.warn("No se pudo reversar la comisión de la devolución {} (orden {}): {}", event.devolutionId(),
                    event.orderId(), ex.getMessage());
        }
    }
}
