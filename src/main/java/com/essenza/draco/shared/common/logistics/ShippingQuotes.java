package com.essenza.draco.shared.common.logistics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cotizador de envíos expuesto por logística al resto de módulos (kernel compartido): la orden, el despacho y la
 * tienda en línea cotizan con el mismo tarifario sin depender del módulo de logística.
 *
 * <p>Cotizar no guarda nada. Quien confirma una opción guarda su valor, transportadora y fecha (la orden ya los
 * tiene), así que un cambio posterior del tarifario no altera lo que ya se pactó.
 */
public interface ShippingQuotes {

    /**
     * Opciones de envío para la empresa, ordenadas por valor y plazo. Lista vacía = destino sin cobertura (queda
     * registrado para que logística sepa qué tarifas faltan).
     */
    QuoteResult quote(long companyId, QuoteRequest request);

    /** La empresa tiene al menos un tarifario activo (si no, quien cotiza puede usar su valor fijo). */
    boolean hasTariff(long companyId);

    /**
     * Pedido de cotización. El origen es una bodega (vacía = la bodega principal). El destino lleva los ids del
     * catálogo de parametros; los nombres solo sirven para el registro de destinos sin cobertura.
     */
    record QuoteRequest(Long originWarehouseId, Long countryId, Long stateId, Long cityId, String destinationLabel,
                        List<Item> items, BigDecimal declaredValue, BigDecimal orderSubtotal, LocalDate shipDate,
                        Long carrierId) {
        public QuoteRequest {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /** Lo que se envía: SKU (o producto) y unidades; el peso y las medidas salen del catálogo. */
    record Item(Long skuId, Long productId, int quantity) {
    }

    record QuoteResult(String zoneKey, String zoneName, Long originWarehouseId, List<Option> options) {
        public QuoteResult {
            options = options == null ? List.of() : List.copyOf(options);
        }

        public boolean covered() {
            return !options.isEmpty();
        }
    }

    /**
     * Opción de una transportadora. {@code total} es lo que se cobra al cliente: flete + seguro, o 0 si aplica envío
     * gratis ({@code free}). {@code chargeableKg} es el mayor entre peso real y volumétrico.
     */
    record Option(long rateCardId, long carrierId, String carrierName, String zoneKey, String zoneName,
                  BigDecimal actualKg, BigDecimal volumetricKg, BigDecimal chargeableKg, boolean weightEstimated,
                  BigDecimal freight, BigDecimal insurance, BigDecimal total, boolean free, int minDays, int maxDays,
                  LocalDate deliveryFrom, LocalDate deliveryTo) {
    }
}
