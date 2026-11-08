package com.essenza.draco.modules.sales.application.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.essenza.draco.shared.common.domain.dto.AuditInfoDto;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto extends AuditInfoDto {
    private Long id;
    private String complementaryOrder;
    private BigDecimal total;
    /** Impuesto incluido en el total y base gravable (total − impuesto). */
    private BigDecimal taxAmount;
    private BigDecimal netAmount;
    /** BACKOFFICE, POS u ONLINE. */
    private String channel;
    private Long cashSessionId;
    /** Valor acreditado por devoluciones; lo que se cobra es total − devuelto. */
    private BigDecimal returnedAmount;
    private String state;
    /** La orden reserva y descuenta stock (órdenes creadas desde la Fase 5). */
    private Boolean stockManaged;
    private String cancelReason;
    // ── V16 ──────────────────────────────────────────────────────────────────
    /** Número visible de la orden (PREFIJO-000123). */
    private String orderNumber;

    // V19: compra multivendedor y vendedor del subpedido (true = vendedor externo).
    private Long purchaseId;
    private Long sellerId;
    private Boolean thirdPartySale;
    /** Cliente y datos de facturación congelados en la orden. */
    private Long customerId;
    private String customerKind;
    private String customerDocType;
    private String customerDocNumber;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String billingAddress;
    private String billingCity;
    /** SHIPPING (envío) o PICKUP (retiro en tienda). */
    private String deliveryMethod;
    private Long pickupStoreId;
    private String pickupStoreName;
    private String recipientName;
    private String recipientPhone;
    private String shipAddress;
    private String shipAddressDetail;
    private Long shipCountryId;
    private Long shipStateId;
    private Long shipCityId;
    private String shipCountry;
    private String shipState;
    private String shipCity;
    private String shipPostalCode;
    private String deliveryNotes;
    private Long carrierId;
    private String carrierName;
    private String trackingNumber;
    /** Costo de envío y lo que se descontó de él (cupón de envío gratis); el total ya los incluye. */
    private BigDecimal shippingAmount;
    private BigDecimal shippingDiscount;
    private java.time.LocalDate promisedDate;
    /** Origen: tienda, bodega de despacho, vendedor asignado y prioridad (LOW, NORMAL, HIGH, URGENT). */
    private Long storeId;
    private String storeName;
    private Long warehouseId;
    private String warehouseName;
    private String assignedTo;
    private String priority;
    /** Descuento global: NONE, PERCENT, AMOUNT o COUPON; discountAmount es lo descontado de las líneas. */
    private String discountType;
    private BigDecimal discountValue;
    private Long couponId;
    private String couponCode;
    private BigDecimal discountAmount;
    private String internalNotes;
    private String customerNotes;
    private java.time.Instant paidAt;
    private java.time.Instant shippedAt;
    private java.time.Instant deliveredAt;
    private java.time.Instant cancelledAt;
    /** Calculados (solo lectura): cobro, productos y preparación. */
    private String paymentStatus;
    private BigDecimal paidAmount;
    private BigDecimal balanceDue;
    private Integer itemsCount;
    private Integer unitsCount;
    private Integer pickedUnits;
    /** Subtotal de productos antes del descuento global. */
    private BigDecimal itemsSubtotal;
    /** Tiene fecha prometida vencida y aún no se entrega. */
    private Boolean overdue;
    /** Estados a los que puede pasar (solo lectura). */
    private java.util.List<String> nextStates;
}
