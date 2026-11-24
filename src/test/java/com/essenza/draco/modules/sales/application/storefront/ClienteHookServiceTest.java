package com.essenza.draco.modules.sales.application.storefront;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.sales.application.input.storefront.ClienteHooksUseCase;
import com.essenza.draco.modules.sales.application.output.storefront.ClienteHookOutbox;
import com.essenza.draco.modules.sales.application.output.storefront.ClienteHookSender;
import com.essenza.draco.modules.sales.application.services.storefront.ClienteHookService;

class ClienteHookServiceTest {

    static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

    /** Outbox en memoria con el mismo contrato que el JDBC. */
    static class MemoryOutbox implements ClienteHookOutbox {
        final Map<Long, Object[]> rows = new HashMap<>(); // id → {event, key, purchase, attempts, next, status}
        final List<Long> enqueued = new ArrayList<>();
        boolean failEnqueue;

        void add(long id, String purchase, int attempts, Instant next) {
            rows.put(id, new Object[] {"ORDER_PAID", "ORDER_PAID:" + id, purchase, attempts, next, "PENDING"});
        }

        final List<String> events = new ArrayList<>();

        @Override
        public void enqueue(long orderId, String event, Instant now) {
            if (failEnqueue) {
                throw new IllegalStateException("tabla bloqueada");
            }
            enqueued.add(orderId);
            events.add(event);
        }

        @Override
        public List<Hook> due(Instant now, int limit) {
            return rows.entrySet().stream()
                    .filter(e -> "PENDING".equals(e.getValue()[5]) && !((Instant) e.getValue()[4]).isAfter(now))
                    .map(e -> new Hook(e.getKey(), (String) e.getValue()[0], (String) e.getValue()[1], (String) e.getValue()[2],
                            "ORD-" + e.getKey(), null, null, (int) e.getValue()[3], (Instant) e.getValue()[4]))
                    .limit(limit).toList();
        }

        @Override
        public boolean claim(Hook hook, Instant leaseUntil) {
            Object[] row = rows.get(hook.id());
            if (!row[4].equals(hook.nextAttemptAt())) {
                return false;
            }
            row[4] = leaseUntil;
            return true;
        }

        @Override
        public void markSent(long id, Instant now) {
            rows.get(id)[5] = "SENT";
        }

        @Override
        public void markFailed(long id, String error, Instant next) {
            Object[] row = rows.get(id);
            row[3] = (int) row[3] + 1;
            if (next == null) {
                row[5] = "FAILED";
            } else {
                row[4] = next;
            }
        }
    }

    static class FakeSender implements ClienteHookSender {
        boolean enabled = true;
        final List<String> sent = new ArrayList<>();
        String failFor;

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public void send(ClienteHookOutbox.Hook hook) {
            if (hook.purchaseNumber().equals(failFor)) {
                throw new IllegalStateException("centauro_cliente respondió 503");
            }
            sent.add(hook.purchaseNumber());
        }
    }

    final MemoryOutbox outbox = new MemoryOutbox();
    final FakeSender sender = new FakeSender();
    final ClienteHookService service = new ClienteHookService(outbox, sender, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void encolaSoloSiEstaConfiguradoYNuncaTumbaElPago() {
        service.orderPaid(7);
        assertThat(outbox.enqueued).containsExactly(7L);

        outbox.failEnqueue = true;
        service.orderPaid(8); // no lanza

        sender.enabled = false;
        outbox.failEnqueue = false;
        service.orderPaid(9);
        assertThat(outbox.enqueued).containsExactly(7L);
    }

    @Test
    void soloSeAvisanDespachoRetiroYEntrega() {
        service.orderFulfillment(1, "SHIPPED");
        service.orderFulfillment(2, "READY");
        service.orderFulfillment(3, "DELIVERED");
        service.orderFulfillment(4, "PROCESSING");
        service.orderFulfillment(5, null);
        assertThat(outbox.enqueued).containsExactly(1L, 2L, 3L);
        assertThat(outbox.events).containsExactly("ORDER_SHIPPED", "ORDER_READY_FOR_PICKUP", "ORDER_DELIVERED");
    }

    @Test
    void enviaLosVencidosYReprogramaLosFallidosConEsperaCreciente() {
        outbox.add(1, "CMP-1", 0, NOW.minusSeconds(5));
        outbox.add(2, "CMP-2", 0, NOW.minusSeconds(5));
        outbox.add(3, "CMP-3", 0, NOW.plusSeconds(60)); // aún no vence
        sender.failFor = "CMP-2";

        ClienteHooksUseCase.Result result = service.dispatch(10);
        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(sender.sent).containsExactly("CMP-1");
        assertThat(outbox.rows.get(1L)[5]).isEqualTo("SENT");
        assertThat(outbox.rows.get(2L)[3]).isEqualTo(1);
        assertThat(outbox.rows.get(2L)[4]).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(outbox.rows.get(3L)[5]).isEqualTo("PENDING");
    }

    @Test
    void trasDoceIntentosQuedaFallidoSinReintento() {
        outbox.add(1, "CMP-1", 11, NOW.minusSeconds(1));
        sender.failFor = "CMP-1";
        service.dispatch(10);
        assertThat(outbox.rows.get(1L)[5]).isEqualTo("FAILED");
        assertThat(outbox.rows.get(1L)[3]).isEqualTo(12);
    }

    @Test
    void otraInstanciaYaLoTomo() {
        outbox.add(1, "CMP-1", 0, NOW.minusSeconds(1));
        ClienteHookOutbox.Hook seen = outbox.due(NOW, 10).get(0);
        outbox.claim(seen, NOW.plusSeconds(120)); // la otra instancia
        assertThat(outbox.claim(seen, NOW.plusSeconds(120))).isFalse();
    }

    @Test
    void apagadoNoEnviaNada() {
        outbox.add(1, "CMP-1", 0, NOW.minusSeconds(1));
        sender.enabled = false;
        assertThat(service.dispatch(10).sent()).isZero();
        assertThat(sender.sent).isEmpty();
    }
}
