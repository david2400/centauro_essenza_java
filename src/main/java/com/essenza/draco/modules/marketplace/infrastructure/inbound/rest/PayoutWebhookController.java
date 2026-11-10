package com.essenza.draco.modules.marketplace.infrastructure.inbound.rest;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.essenza.draco.modules.marketplace.application.service.PayoutDispatchService;
import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Aviso de Wompi Pagos a terceros ({@code transaction.updated} / {@code payout.updated}). Sin sesión
 * (agregar {@code /marketplace/payouts/webhooks/**} a las rutas públicas). No se confía en el contenido:
 * solo se toma el id del lote para consultar su estado real con las llaves de la tienda. Responde 200
 * siempre para que el proveedor no reintente sin fin; el sincronizador programado cubre lo que se pierda.
 */
@RestController
@RequestMapping("/marketplace/payouts/webhooks")
@Tag(name = "Payouts", description = "Liquidaciones a vendedores")
public class PayoutWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PayoutWebhookController.class);

    private final PayoutDispatchService dispatch;

    public PayoutWebhookController(PayoutDispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @Operation(summary = "Wompi payouts webhook", description = "Dispara la consulta del lote en Wompi (no confía en el cuerpo).")
    @PostMapping("/wompi")
    public ResponseEntity<Void> wompi(@RequestBody(required = false) JsonNode body) {
        try {
            dispatch.onProviderEvent(candidates(body));
        } catch (RuntimeException ex) {
            log.warn("Aviso de Wompi Pagos a terceros sin procesar: {}", ex.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    static List<String> candidates(JsonNode body) {
        List<String> ids = new ArrayList<>();
        if (body == null) {
            return ids;
        }
        JsonNode data = body.path("data");
        for (JsonNode node : List.of(data, data.path("payout"), data.path("transaction"), body)) {
            for (String field : List.of("payoutId", "payout_id")) {
                JsonNode v = node.path(field);
                if (v.isValueNode() && !v.asText().isBlank()) {
                    ids.add(v.asText());
                }
            }
        }
        JsonNode payoutId = data.path("payout").path("id");
        if (payoutId.isValueNode()) {
            ids.add(payoutId.asText());
        }
        if (data.path("id").isValueNode() && body.path("event").asText("").startsWith("payout")) {
            ids.add(data.path("id").asText());
        }
        return ids.stream().distinct().limit(5).toList();
    }
}
