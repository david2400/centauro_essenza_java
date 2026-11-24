package com.essenza.draco.modules.sales.infrastructure.inbound.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.essenza.draco.modules.sales.application.input.storefront.ClienteHooksUseCase;

/**
 * Cada {@code essenza.cliente-hooks.interval} (30 s) envía a centauro_cliente los avisos pendientes. Se apaga con
 * {@code essenza.cliente-hooks.enabled=false}; sin URL o secreto no hace nada.
 */
@Component
@ConditionalOnProperty(prefix = "essenza.cliente-hooks", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ClienteHookJob {

    private static final Logger log = LoggerFactory.getLogger(ClienteHookJob.class);

    private final ClienteHooksUseCase hooks;
    private final int batch;

    public ClienteHookJob(ClienteHooksUseCase hooks, @Value("${essenza.cliente-hooks.batch:50}") int batch) {
        this.hooks = hooks;
        this.batch = batch;
    }

    @Scheduled(fixedDelayString = "${essenza.cliente-hooks.interval:PT30S}", initialDelayString = "${essenza.cliente-hooks.initial-delay:PT1M}")
    public void run() {
        ClienteHooksUseCase.Result result = hooks.dispatch(batch);
        if (result.sent() > 0 || result.failed() > 0) {
            log.info("Avisos a centauro_cliente: {} enviado(s), {} con error", result.sent(), result.failed());
        }
    }
}
