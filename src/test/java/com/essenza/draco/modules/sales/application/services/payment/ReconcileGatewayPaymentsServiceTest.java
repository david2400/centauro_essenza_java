package com.essenza.draco.modules.sales.application.services.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.sales.application.dto.payment.PaymentDto;
import com.essenza.draco.modules.sales.application.input.payment.ReconcileGatewayPaymentsUseCase.Result;
import com.essenza.draco.modules.sales.application.input.payment.SyncPaymentUseCase;
import com.essenza.draco.modules.sales.application.output.repository.PaymentRepository;

class ReconcileGatewayPaymentsServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T15:00:00Z");

    private static PaymentDto withStatus(String status) {
        PaymentDto dto = mock(PaymentDto.class);
        when(dto.status()).thenReturn(status);
        return dto;
    }

    @Test
    void consultaLosPendientesDeLaVentanaYCuentaLosQueCambian() {
        PaymentRepository payments = mock(PaymentRepository.class);
        SyncPaymentUseCase sync = mock(SyncPaymentUseCase.class);
        when(payments.findPendingOnline(NOW.minus(Duration.ofDays(3)), NOW.minus(Duration.ofMinutes(5)), 50))
                .thenReturn(List.of(1L, 2L, 3L));
        PaymentDto approved = withStatus("APPROVED");
        PaymentDto pending = withStatus("PENDING");
        when(sync.sync(1L)).thenReturn(approved);
        when(sync.sync(2L)).thenReturn(pending);
        when(sync.sync(3L)).thenThrow(new IllegalStateException("pasarela caída"));

        var service = new ReconcileGatewayPaymentsService(payments, sync, Clock.fixed(NOW, ZoneOffset.UTC));
        Result result = service.reconcile(Duration.ofMinutes(5), Duration.ofDays(3), 50);

        // Un fallo de la pasarela con un pago no detiene los demás.
        assertThat(result).isEqualTo(new Result(3, 1, 1));
        verify(sync).sync(eq(3L));
    }

    @Test
    void sinPendientesNoConsultaLaPasarela() {
        PaymentRepository payments = mock(PaymentRepository.class);
        SyncPaymentUseCase sync = mock(SyncPaymentUseCase.class);
        when(payments.findPendingOnline(NOW.minus(Duration.ofDays(3)), NOW.minus(Duration.ofMinutes(5)), 10)).thenReturn(List.of());

        var service = new ReconcileGatewayPaymentsService(payments, sync, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(service.reconcile(Duration.ofMinutes(5), Duration.ofDays(3), 10)).isEqualTo(new Result(0, 0, 0));
        org.mockito.Mockito.verifyNoInteractions(sync);
    }
}
