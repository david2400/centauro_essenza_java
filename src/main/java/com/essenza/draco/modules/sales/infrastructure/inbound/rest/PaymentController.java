package com.essenza.draco.modules.sales.infrastructure.inbound.rest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.essenza.draco.modules.sales.application.dto.payment.CheckoutDto;
import com.essenza.draco.modules.sales.application.dto.payment.PaymentActionDto;
import com.essenza.draco.modules.sales.application.dto.payment.PaymentDto;
import com.essenza.draco.modules.sales.application.dto.payment.PaymentEventDto;
import com.essenza.draco.modules.sales.application.dto.payment.PaymentFilter;
import com.essenza.draco.modules.sales.application.dto.payment.PaymentsSummaryDto;
import com.essenza.draco.modules.sales.application.dto.payment.RegisterPaymentDto;
import com.essenza.draco.modules.sales.application.dto.payment.RegisterSplitPaymentDto;
import com.essenza.draco.modules.sales.application.dto.payment.StartCheckoutDto;
import com.essenza.draco.modules.sales.application.input.payment.ConfirmPaymentUseCase;
import com.essenza.draco.modules.sales.application.input.payment.DeclinePaymentUseCase;
import com.essenza.draco.modules.sales.application.input.payment.FindPaymentByIdUseCase;
import com.essenza.draco.modules.sales.application.input.payment.FindPaymentEventsUseCase;
import com.essenza.draco.modules.sales.application.input.payment.RefundPaymentUseCase;
import com.essenza.draco.modules.sales.application.input.payment.RegisterPaymentUseCase;
import com.essenza.draco.modules.sales.application.input.payment.RegisterSplitPaymentUseCase;
import com.essenza.draco.modules.sales.application.input.payment.SearchPaymentsUseCase;
import com.essenza.draco.modules.sales.application.input.payment.StartCheckoutUseCase;
import com.essenza.draco.modules.sales.application.input.payment.SummarizePaymentsUseCase;
import com.essenza.draco.modules.sales.application.input.payment.SyncPaymentUseCase;
import com.essenza.draco.modules.sales.application.input.payment.VoidPaymentUseCase;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateway;
import com.essenza.draco.shared.common.domain.dto.PageResponse;
import com.essenza.draco.shared.common.web.PageableFactory;
import com.essenza.draco.shared.exceptions.ApiErrorResponse;
import com.essenza.draco.shared.exceptions.NotFoundException;

@RestController
@RequestMapping("/sales/payments")
@Tag(name = "Payments", description = "Pagos de órdenes: registro en tienda, pagos en línea, confirmación, anulación y reembolso")
@RequiredArgsConstructor
public class PaymentController {

    /** Fechas del filtro (from/to) en hora de Colombia. */
    static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final Set<String> SORTABLE = Set.of("id", "amount", "status", "createdAt", "approvedAt", "orderId");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("id"));

    private final SearchPaymentsUseCase searchPayments;
    private final SummarizePaymentsUseCase summarizePayments;
    private final FindPaymentByIdUseCase findPayment;
    private final FindPaymentEventsUseCase findEvents;
    private final RegisterPaymentUseCase registerPayment;
    private final RegisterSplitPaymentUseCase registerSplitPayment;
    private final StartCheckoutUseCase startCheckout;
    private final ConfirmPaymentUseCase confirmPayment;
    private final DeclinePaymentUseCase declinePayment;
    private final VoidPaymentUseCase voidPayment;
    private final RefundPaymentUseCase refundPayment;
    private final SyncPaymentUseCase syncPayment;

    @Operation(summary = "Search", description = "Pagos paginados. Filtros: q (referencia, orden, correo), status (uno o varios separados por coma), "
            + "methodId, channel, provider, orderId, from/to (yyyy-MM-dd, hora de Colombia); sort=campo,asc|desc")
    @GetMapping
    public PageResponse<PaymentDto> search(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "methodId", required = false) Long methodId,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "provider", required = false) String provider,
            @RequestParam(value = "orderId", required = false) Long orderId,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "sort", required = false) String sort) {
        return searchPayments.search(filter(q, status, methodId, channel, provider, orderId, from, to),
                PageableFactory.of(page, size, sort, SORTABLE, DEFAULT_SORT));
    }

    @Operation(summary = "Summary", description = "Totales por medio y estado con los mismos filtros del listado.")
    @GetMapping("/summary")
    public PaymentsSummaryDto summary(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "methodId", required = false) Long methodId,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "provider", required = false) String provider,
            @RequestParam(value = "orderId", required = false) Long orderId,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to) {
        return summarizePayments.summarize(filter(q, status, methodId, channel, provider, orderId, from, to));
    }

    @Operation(summary = "Get by id")
    @GetMapping("/{id}")
    public PaymentDto get(@PathVariable Long id) {
        return findPayment.findById(id).orElseThrow(() -> new NotFoundException("Pago no encontrado: " + id));
    }

    @Operation(summary = "Events", description = "Bitácora del pago (registro, confirmaciones, webhooks, reembolsos).")
    @GetMapping("/{id}/events")
    public List<PaymentEventDto> events(@PathVariable Long id) {
        return findEvents.events(id);
    }

    @Operation(summary = "Register", description = "Pago cobrado en tienda o confirmado a mano (efectivo con cambio, datáfono, transferencia…).")
    @PostMapping
    public ResponseEntity<PaymentDto> register(@Valid @RequestBody RegisterPaymentDto input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registerPayment.register(input));
    }

    @Operation(summary = "Register split", description = "Cobro de una orden con varios medios (efectivo + datáfono…), todo o nada: "
            + "si una parte no cumple las reglas no se registra ninguna. La suma no puede superar el saldo por cobrar.")
    @PostMapping("/split")
    public ResponseEntity<List<PaymentDto>> registerSplit(@Valid @RequestBody RegisterSplitPaymentDto input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registerSplitPayment.register(input));
    }

    @Operation(summary = "Checkout", description = "Inicia un pago en línea con la pasarela del medio y devuelve el link de pago.")
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutDto> checkout(@Valid @RequestBody StartCheckoutDto input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(startCheckout.start(input));
    }

    @Operation(summary = "Confirm", description = "Aprueba a mano un pago pendiente.")
    @PostMapping("/{id}/confirm")
    public PaymentDto confirm(@PathVariable Long id, @Valid @RequestBody(required = false) PaymentActionDto input) {
        return confirmPayment.confirm(id, input);
    }

    @Operation(summary = "Decline", description = "Rechaza un pago pendiente.")
    @PostMapping("/{id}/decline")
    public PaymentDto decline(@PathVariable Long id, @Valid @RequestBody(required = false) PaymentActionDto input) {
        return declinePayment.decline(id, input);
    }

    @Operation(summary = "Void", description = "Anula un pago (reason obligatorio).")
    @PostMapping("/{id}/void")
    public PaymentDto voidPayment(@PathVariable Long id, @Valid @RequestBody PaymentActionDto input) {
        return voidPayment.voidPayment(id, input);
    }

    @Operation(summary = "Refund", description = "Reembolso parcial (amount) o total. Mercado Pago lo ejecuta por API; los demás se registran.")
    @PostMapping("/{id}/refund")
    public PaymentDto refund(@PathVariable Long id, @Valid @RequestBody(required = false) PaymentActionDto input) {
        return refundPayment.refund(id, input);
    }

    @Operation(summary = "Sync", description = "Consulta el estado en la pasarela (si no llegó el webhook).")
    @PostMapping("/{id}/sync")
    public PaymentDto sync(@PathVariable Long id) {
        return syncPayment.sync(id);
    }

    @ExceptionHandler(PaymentGateway.GatewayException.class)
    public ResponseEntity<ApiErrorResponse> gatewayError(PaymentGateway.GatewayException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiErrorResponse(Instant.now(), 502, "Bad Gateway",
                "GATEWAY", ex.getMessage(), request.getRequestURI(), List.of()));
    }

    static PaymentFilter filter(String q, String status, Long methodId, String channel, String provider, Long orderId,
                                LocalDate from, LocalDate to) {
        return new PaymentFilter(q, status, methodId, channel, provider, orderId,
                from == null ? null : from.atStartOfDay(ZONE).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(ZONE).toInstant());
    }
}
