package com.essenza.draco.modules.marketplace.infrastructure.inbound.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.essenza.draco.modules.marketplace.application.service.CommissionService;

/**
 * Devenga a diario las comisiones cuya fecha llegó (entrega o cierre de la ventana de devolución).
 * Cada comisión en su propia transacción: una que falle no detiene las demás. Solo asienta en el
 * estado de cuenta; no cobra nada.
 */
@Component
public class CommissionAccrualJob {

    private static final Logger log = LoggerFactory.getLogger(CommissionAccrualJob.class);

    private final CommissionService commissions;

    public CommissionAccrualJob(CommissionService commissions) {
        this.commissions = commissions;
    }

    @Scheduled(cron = "${essenza.marketplace.billing.accrual-cron:0 20 2 * * *}",
            zone = "${essenza.marketplace.zone:America/Bogota}")
    public void run() {
        int accrued = 0;
        for (Long id : commissions.dueIds()) {
            try {
                if (commissions.accrueOne(id)) {
                    accrued++;
                }
            } catch (RuntimeException ex) {
                log.warn("No se pudo devengar la comisión {}: {}", id, ex.getMessage());
            }
        }
        if (accrued > 0) {
            log.info("Comisiones devengadas: {}", accrued);
        }
    }
}
