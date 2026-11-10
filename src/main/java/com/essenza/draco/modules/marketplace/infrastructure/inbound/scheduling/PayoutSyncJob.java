package com.essenza.draco.modules.marketplace.infrastructure.inbound.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.essenza.draco.modules.marketplace.application.port.PayoutDispatcher;
import com.essenza.draco.modules.marketplace.application.service.PayoutDispatchService;

/**
 * Consulta periódicamente el resultado de los lotes enviados a dispersión (cubre avisos perdidos del
 * proveedor). Solo lee el estado y asienta lo ya pagado; nunca envía pagos.
 */
@Component
public class PayoutSyncJob {

    private static final Logger log = LoggerFactory.getLogger(PayoutSyncJob.class);

    private final PayoutDispatchService dispatch;
    private final PayoutDispatcher dispatcher;

    public PayoutSyncJob(PayoutDispatchService dispatch, PayoutDispatcher dispatcher) {
        this.dispatch = dispatch;
        this.dispatcher = dispatcher;
    }

    @Scheduled(cron = "${essenza.payouts.sync-cron:0 */10 * * * *}", zone = "${essenza.marketplace.zone:America/Bogota}")
    public void run() {
        if (!dispatcher.configured()) {
            return;
        }
        int done = dispatch.syncAll();
        if (done > 0) {
            log.info("Lotes de pago resueltos por la dispersión: {}", done);
        }
    }
}
