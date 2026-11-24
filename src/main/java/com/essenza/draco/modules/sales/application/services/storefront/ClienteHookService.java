package com.essenza.draco.modules.sales.application.services.storefront;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.essenza.draco.modules.sales.application.input.storefront.ClienteHooksUseCase;
import com.essenza.draco.modules.sales.application.output.storefront.ClienteHookOutbox;
import com.essenza.draco.modules.sales.application.output.storefront.ClienteHookSender;

/**
 * Outbox hacia centauro_cliente: el aviso se guarda junto con el pago o el cambio de estado (si la transacción se
 * revierte, no hay aviso) y
 * se envía después, con reintentos espaciados (1, 2, 4… minutos, hasta 6 h; 12 intentos ≈ 1 día). centauro_cliente
 * es idempotente: un aviso repetido no duplica correos.
 */
@Service
public class ClienteHookService implements ClienteHooksUseCase {

    private static final Logger log = LoggerFactory.getLogger(ClienteHookService.class);
    static final int MAX_ATTEMPTS = 12;
    static final Duration MAX_BACKOFF = Duration.ofHours(6);
    /** Tiempo que un envío reserva el aviso (más que el timeout HTTP) para que otra instancia no lo repita. */
    static final Duration LEASE = Duration.ofMinutes(2);

    private final ClienteHookOutbox outbox;
    private final ClienteHookSender sender;
    private final Clock clock;

    @Autowired
    public ClienteHookService(ClienteHookOutbox outbox, ClienteHookSender sender) {
        this(outbox, sender, Clock.systemUTC());
    }

    public ClienteHookService(ClienteHookOutbox outbox, ClienteHookSender sender, Clock clock) {
        this.outbox = outbox;
        this.sender = sender;
        this.clock = clock;
    }

    @Override
    public void orderPaid(long orderId) {
        enqueue(orderId, ClienteHookOutbox.ORDER_PAID);
    }

    @Override
    public void orderFulfillment(long orderId, String status) {
        String event = switch (status == null ? "" : status) {
            case "SHIPPED" -> ClienteHookOutbox.ORDER_SHIPPED;
            case "READY" -> ClienteHookOutbox.ORDER_READY_FOR_PICKUP;
            case "DELIVERED" -> ClienteHookOutbox.ORDER_DELIVERED;
            default -> null;
        };
        if (event != null) {
            enqueue(orderId, event);
        }
    }

    private void enqueue(long orderId, String event) {
        if (!sender.enabled()) {
            return;
        }
        try {
            outbox.enqueue(orderId, event, clock.instant());
        } catch (RuntimeException ex) {
            log.warn("No se pudo encolar el aviso {} de la orden {} para centauro_cliente: {}", event, orderId, ex.getMessage());
        }
    }

    @Override
    public Result dispatch(int batch) {
        if (!sender.enabled()) {
            return new Result(0, 0);
        }
        int sent = 0;
        int failed = 0;
        Instant now = clock.instant();
        for (ClienteHookOutbox.Hook hook : outbox.due(now, batch)) {
            if (!outbox.claim(hook, now.plus(LEASE))) {
                continue;
            }
            try {
                sender.send(hook);
                outbox.markSent(hook.id(), clock.instant());
                sent++;
            } catch (RuntimeException ex) {
                failed++;
                int attempts = hook.attempts() + 1;
                Instant next = attempts >= MAX_ATTEMPTS ? null : clock.instant().plus(backoff(attempts));
                outbox.markFailed(hook.id(), message(ex), next);
                if (next == null) {
                    log.error("Aviso {} a centauro_cliente descartado tras {} intentos: {}", hook.eventKey(), attempts, message(ex));
                }
            }
        }
        return new Result(sent, failed);
    }

    static Duration backoff(int attempts) {
        Duration delay = Duration.ofMinutes(1L << Math.min(attempts - 1, 20));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private static String message(RuntimeException ex) {
        String text = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return text.length() > 300 ? text.substring(0, 300) : text;
    }
}
