package com.essenza.draco.modules.sales.application.services.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.sales.application.dto.order.CreateFullOrderDto;
import com.essenza.draco.modules.sales.application.dto.order.OrderDto;
import com.essenza.draco.modules.sales.application.dto.order.SaveOrderDetailsDto;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.CreatePurchaseRequest;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.OfferOptionDto;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.OrderPurchaseDto;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.PurchaseDetailDto;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.PurchaseSummaryDto;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.SkuOptionsDto;
import com.essenza.draco.modules.sales.application.dto.purchase.PurchaseDtos.SubOrderDto;
import com.essenza.draco.modules.sales.application.input.order.CreateFullOrderUseCase;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger;
import com.essenza.draco.modules.sales.application.output.repository.OrderLedger.OrderLine;
import com.essenza.draco.modules.sales.application.output.repository.PurchaseLedger;
import com.essenza.draco.modules.sales.application.output.repository.PurchaseLedger.NewPurchase;
import com.essenza.draco.modules.sales.application.output.repository.PurchaseLedger.PurchaseFilter;
import com.essenza.draco.modules.sales.application.output.repository.PurchaseLedger.PurchaseRow;
import com.essenza.draco.modules.sales.application.output.repository.PurchaseLedger.SubOrderRow;
import com.essenza.draco.modules.sales.application.services.order.OrderSoporte;
import com.essenza.draco.modules.sales.domain.model.LineTax;
import com.essenza.draco.modules.sales.domain.model.OrderLineAmounts;
import com.essenza.draco.modules.sales.domain.model.OrderStatus;
import com.essenza.draco.modules.sales.domain.model.order.DeliveryMethod;
import com.essenza.draco.modules.sales.domain.model.order.OrderChannel;
import com.essenza.draco.modules.sales.domain.model.order.OrderEventType;
import com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation;
import com.essenza.draco.modules.sales.domain.model.purchase.CartSplit;
import com.essenza.draco.modules.sales.domain.model.purchase.CartSplit.Group;
import com.essenza.draco.modules.sales.domain.model.purchase.CartSplit.Item;
import com.essenza.draco.modules.sales.domain.model.purchase.PurchaseStatus;
import com.essenza.draco.shared.common.catalog.SkuPricing;
import com.essenza.draco.shared.common.catalog.SkuPricing.SkuSnapshot;
import com.essenza.draco.shared.common.marketplace.OfferStock;
import com.essenza.draco.shared.common.marketplace.OfferStock.OfferQuote;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Compra multivendedor: un subpedido (orden) por vendedor, cada uno con sus propios pagos,
 * reservas y flujo. Los productos propios usan el precio y la bodega del catálogo (mismo flujo
 * de siempre); los de un vendedor externo, el precio de su oferta y su cantidad disponible.
 * <p>
 * Seguridad: la empresa y el usuario salen del token. El vendedor de cada línea lo resuelve el
 * servidor desde la oferta; el navegador nunca lo envía.
 */
@Service
@Transactional
public class PurchaseService {

    public static final String SALES_VIEW = "VENTAS.VER";
    public static final String SALES_MANAGE = "VENTAS.GESTIONAR";

    private final PurchaseLedger purchases;
    private final OrderLedger ledger;
    private final CreateFullOrderUseCase orders;
    private final OrderSoporte soporte;
    private final SkuPricing skus;
    private final OfferStock offers;
    private final ActorProvider actors;
    private final String currency;

    public PurchaseService(PurchaseLedger purchases, OrderLedger ledger, CreateFullOrderUseCase orders, OrderSoporte soporte,
                           SkuPricing skus, OfferStock offers, ActorProvider actors,
                           @Value("${essenza.marketplace.catalog-currency:COP}") String currency) {
        this.purchases = purchases;
        this.ledger = ledger;
        this.orders = orders;
        this.soporte = soporte;
        this.skus = skus;
        this.offers = offers;
        this.actors = actors;
        this.currency = currency.trim().toUpperCase(Locale.ROOT);
    }

    public PurchaseDetailDto create(CreatePurchaseRequest input) {
        RequestActor actor = actors.current().require(SALES_MANAGE);
        long company = actor.companyId();
        SaveOrderDetailsDto details = input.details();

        List<Item> cart = CartSplit.merge(input.lines().stream()
                .map(line -> new Item(line.skuId(), line.offerId(), null, line.quantity() == null ? 0 : line.quantity()))
                .toList());
        // Las ofertas se bloquean en orden de id: dos compras simultáneas no se cruzan.
        Map<Long, OfferQuote> quotes = new HashMap<>();
        for (Long offerId : CartSplit.offersInLockOrder(cart)) {
            OfferQuote quote = offers.lockForSale(company, offerId);
            if (!currency.equalsIgnoreCase(quote.currency())) {
                throw new OrderRuleViolation("La oferta de " + quote.sellerName() + " está en " + quote.currency()
                        + "; las compras se cobran en " + currency + ".");
            }
            quotes.put(offerId, quote);
        }
        List<Item> resolved = new ArrayList<>();
        for (Item item : cart) {
            if (item.offerId() == null) {
                resolved.add(item);
                continue;
            }
            OfferQuote quote = quotes.get(item.offerId());
            if (!quote.skuId().equals(item.skuId())) {
                throw new OrderRuleViolation("La oferta " + item.offerId() + " no corresponde al SKU " + item.skuId() + ".");
            }
            resolved.add(new Item(item.skuId(), item.offerId(), quote.sellerId(), item.quantity(), quote.consignment()));
        }
        List<Group> groups = CartSplit.groups(resolved);
        boolean hasOwn = groups.get(0).own();
        boolean hasExternal = groups.size() > (hasOwn ? 1 : 0);
        if (hasExternal && details.delivery() != null
                && DeliveryMethod.parse(details.delivery().method()) == DeliveryMethod.PICKUP) {
            throw new OrderRuleViolation("Los productos de vendedores externos los envía cada vendedor: la compra no admite "
                    + "retiro en tienda.");
        }
        if (!hasOwn && details.discount() != null && details.discount().type() != null
                && !"NONE".equalsIgnoreCase(details.discount().type())) {
            throw new OrderRuleViolation("Los descuentos y cupones aplican a productos de la tienda propia; esta compra no tiene.");
        }

        Instant now = Instant.now();
        SaveOrderDetailsDto.Customer customer = details.customer();
        long purchaseId = purchases.insert(company, new NewPurchase(channelOf(details), currency,
                customer == null ? null : clip(customer.name(), 200), customer == null ? null : clip(customer.docType(), 10),
                customer == null ? null : clip(customer.docNumber(), 30), customer == null ? null : clip(customer.email(), 160),
                customer == null ? null : clip(customer.phone(), 40)), actor.userId(), clip(actor.displayName(), 120), now);
        PurchaseRow header = purchases.find(company, purchaseId).orElseThrow();
        Long ownSeller = offers.ownSeller(company).map(OfferStock.SellerRef::id).orElse(null);

        boolean first = true;
        for (Group group : groups) {
            if (group.own()) {
                List<CreateFullOrderDto.Line> lines = group.items().stream()
                        .map(item -> new CreateFullOrderDto.Line(null, item.skuId(), item.quantity(), null)).toList();
                OrderDto order = orders.createFull(new CreateFullOrderDto(ownDetails(details, header.number()), lines));
                purchases.linkOrder(purchaseId, order.getId(), ownSeller);
                note(order.getId(), header.number(), null, actor);
            } else {
                // Envío y descuento van con el subpedido propio; si no hay, el envío va con el primero.
                OrderDto order = orders.createFull(new CreateFullOrderDto(
                        sellerDetails(details, header.number(), first && !hasOwn), List.of()));
                purchases.linkOrder(purchaseId, order.getId(), group.sellerId());
                for (Item item : group.items()) {
                    addOfferLine(company, order.getId(), item, quotes.get(item.offerId()));
                }
                soporte.reprice(order.getId());
                note(order.getId(), header.number(), quotes.get(group.items().get(0).offerId()).sellerName(), actor);
            }
            first = false;
        }
        purchases.flush();
        return detail(purchases.find(company, purchaseId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public Page<PurchaseSummaryDto> search(PurchaseFilter filter, Pageable pageable) {
        RequestActor actor = actors.current().requireAny(SALES_VIEW, SALES_MANAGE);
        return purchases.search(actor.companyId(), filter, pageable).map(PurchaseService::summary);
    }

    @Transactional(readOnly = true)
    public PurchaseDetailDto get(long purchaseId) {
        RequestActor actor = actors.current().requireAny(SALES_VIEW, SALES_MANAGE);
        return detail(purchases.find(actor.companyId(), purchaseId)
                .orElseThrow(() -> new NotFoundException("No se encontró la compra " + purchaseId + ".")));
    }

    /** Compra y vendedor de una orden; 404 si la orden no es un subpedido de una compra de la empresa. */
    @Transactional(readOnly = true)
    public OrderPurchaseDto ofOrder(long orderId) {
        RequestActor actor = actors.current().requireAny(SALES_VIEW, SALES_MANAGE);
        return purchases.linkOf(actor.companyId(), orderId)
                .map(link -> new OrderPurchaseDto(link.purchaseId(), link.purchaseNumber(), link.sellerId(), link.sellerName(),
                        "EXTERNAL".equals(link.sellerKind()), link.subOrders()))
                .orElseThrow(() -> new NotFoundException("La orden " + orderId + " no pertenece a una compra multivendedor."));
    }

    /** Quién vende el SKU: tienda propia (precio de catálogo) y ofertas activas en la moneda de venta. */
    @Transactional(readOnly = true)
    public SkuOptionsDto skuOptions(long skuId) {
        RequestActor actor = actors.current().require(SALES_MANAGE);
        SkuSnapshot sku = skus.resolve(null, skuId);
        List<OfferOptionDto> options = offers.forSale(actor.companyId(), skuId).stream()
                .filter(offer -> currency.equalsIgnoreCase(offer.currency()))
                .map(offer -> new OfferOptionDto(offer.offerId(), offer.sellerId(), offer.sellerName(), offer.price(),
                        offer.availableQuantity(), offer.handlingDays(), offer.condition()))
                .toList();
        return new SkuOptionsDto(sku.skuId(), sku.code(), sku.name(), sku.sellable(), sku.unitPrice(), currency,
                sku.sellable() ? options : List.of());
    }

    private void addOfferLine(long company, Long orderId, Item item, OfferQuote quote) {
        SkuSnapshot sku = skus.resolve(null, item.skuId());
        if (!sku.sellable()) {
            throw new ConflictException("\"" + sku.name() + "\" (" + sku.code() + ") no está a la venta en el catálogo.");
        }
        OrderLineAmounts amounts = OrderLineAmounts.of(quote.price(), item.quantity(), null);
        LineTax tax = LineTax.included(sku.taxRateId(), sku.taxCode(), sku.taxKind(), sku.taxRate(), amounts.total());
        OrderLine line = ledger.saveLine(new OrderLine(null, orderId, sku.productId(), sku.skuId(), sku.code(), sku.name(),
                amounts.unitPrice(), amounts.quantity(), amounts.discount(), amounts.subtotal(), amounts.total(), tax));
        purchases.setLineOffer(line.id(), item.offerId());
        offers.reserveLine(company, item.offerId(), orderId, line.id(), item.quantity());
    }

    private void note(Long orderId, String purchaseNumber, String sellerName, RequestActor actor) {
        soporte.log(orderId, OrderEventType.NOTE, null, null, "Subpedido de la compra " + purchaseNumber
                + (sellerName == null ? " · tienda propia" : " · vendedor " + sellerName), actor.displayName());
    }

    /** Detalles del subpedido propio: los del comprador (con envío y descuento), con la compra como referencia. */
    private static SaveOrderDetailsDto ownDetails(SaveOrderDetailsDto d, String number) {
        return new SaveOrderDetailsDto(d.customer(), d.delivery(), d.discount(), d.channel(), d.storeId(), d.warehouseId(),
                d.assignedTo(), d.priority(), number, d.internalNotes(), d.customerNotes(), d.actor());
    }

    /** Subpedido de un vendedor externo: sin descuento, sin tienda ni bodega propias. */
    private static SaveOrderDetailsDto sellerDetails(SaveOrderDetailsDto d, String number, boolean carriesShipping) {
        return new SaveOrderDetailsDto(d.customer(), carriesShipping ? d.delivery() : withoutShipping(d.delivery()), null,
                d.channel(), null, null, d.assignedTo(), d.priority(), number, d.internalNotes(), d.customerNotes(), d.actor());
    }

    private static SaveOrderDetailsDto.Delivery withoutShipping(SaveOrderDetailsDto.Delivery d) {
        if (d == null) {
            return null;
        }
        return new SaveOrderDetailsDto.Delivery(d.method(), d.pickupStoreId(), d.recipientName(), d.recipientPhone(),
                d.address(), d.addressDetail(), d.countryId(), d.stateId(), d.cityId(), d.country(), d.state(), d.city(),
                d.postalCode(), d.notes(), d.carrierId(), BigDecimal.ZERO, d.promisedDate());
    }

    private static String channelOf(SaveOrderDetailsDto details) {
        OrderChannel channel = OrderChannel.parse(details.channel());
        if (channel == OrderChannel.POS) {
            throw new OrderRuleViolation("Las ventas de mostrador se registran desde la caja (POS).");
        }
        return channel.name();
    }

    static PurchaseSummaryDto summary(PurchaseRow row) {
        return new PurchaseSummaryDto(row.id(), row.number(), row.status().name(), row.counts().partiallyCancelled(),
                row.channel(), row.currency(), row.total(), row.counts().total(), row.sellers(), row.customerName(),
                row.customerDocNumber(), row.createdAt());
    }

    private PurchaseDetailDto detail(PurchaseRow row) {
        List<SubOrderRow> subOrders = purchases.subOrders(row.id());
        List<OrderStatus> states = subOrders.stream().map(s -> OrderStatus.tryParse(s.state()).orElse(OrderStatus.PENDING))
                .toList();
        BigDecimal pending = subOrders.stream().filter(s -> "PENDING".equals(s.state())).map(SubOrderRow::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PurchaseStatus.Counts counts = PurchaseStatus.Counts.of(states);
        return new PurchaseDetailDto(row.id(), row.number(), PurchaseStatus.derive(counts).name(),
                counts.partiallyCancelled(), row.channel(), row.currency(), row.total(), pending.setScale(2,
                java.math.RoundingMode.HALF_UP), row.customerName(), row.customerDocType(), row.customerDocNumber(),
                row.customerEmail(), row.customerPhone(), row.createdBy(), row.createdByName(), row.createdAt(),
                subOrders.stream().map(s -> new SubOrderDto(s.orderId(), s.orderNumber(), s.state(), s.total(), s.sellerId(),
                        s.sellerName(), "EXTERNAL".equals(s.sellerKind()), s.items(), s.units(), s.paidAt())).toList());
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
