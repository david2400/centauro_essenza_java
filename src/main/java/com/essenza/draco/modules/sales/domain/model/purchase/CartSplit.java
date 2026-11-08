package com.essenza.draco.modules.sales.domain.model.purchase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation;

/**
 * Reparte un carrito en subpedidos, uno por vendedor y forma de despacho: lo que el vendedor
 * despacha y lo que la tienda despacha desde su consignación van en subpedidos distintos (cobra
 * uno u otro). El vendedor propio (sin oferta) va primero y luego los externos por id, siempre
 * en el mismo orden (las reservas se toman en un
 * orden fijo y dos compras simultáneas no se bloquean en cruz).
 */
public final class CartSplit {

    public static final int MAX_LINES = 100;
    public static final int MAX_QUANTITY = 9_999;

    private CartSplit() {
    }

    /**
     * Línea del carrito.
     *
     * @param offerId  oferta de un vendedor externo; {@code null} = venta propia con el precio del catálogo
     * @param sellerId vendedor de la oferta (lo resuelve el servidor, nunca el navegador); {@code null} en la propia
     */
    public record Item(Long skuId, Long offerId, Long sellerId, int quantity, boolean consignment) {
        public Item(Long skuId, Long offerId, Long sellerId, int quantity) {
            this(skuId, offerId, sellerId, quantity, false);
        }
    }

    /** Subpedido: vendedor ({@code null} = propio) y sus líneas. */
    public record Group(Long sellerId, List<Item> items, boolean consignment) {
        public Group(Long sellerId, List<Item> items) {
            this(sellerId, items, false);
        }

        public boolean own() {
            return sellerId == null;
        }
    }

    /**
     * Valida y une las líneas repetidas (mismo SKU y misma oferta) sumando cantidades. Aún sin
     * vendedor resuelto.
     */
    public static List<Item> merge(List<Item> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new OrderRuleViolation("La compra no tiene productos.");
        }
        if (lines.size() > MAX_LINES) {
            throw new OrderRuleViolation("Una compra admite máximo " + MAX_LINES + " líneas.");
        }
        Map<String, Item> merged = new LinkedHashMap<>();
        for (Item line : lines) {
            if (line == null || line.skuId() == null) {
                throw new OrderRuleViolation("Cada línea debe indicar el SKU.");
            }
            if (line.quantity() < 1) {
                throw new OrderRuleViolation("La cantidad de cada línea debe ser mayor que cero.");
            }
            String key = line.skuId() + ":" + (line.offerId() == null ? "own" : line.offerId());
            Item previous = merged.get(key);
            int quantity = previous == null ? line.quantity() : previous.quantity() + line.quantity();
            if (quantity > MAX_QUANTITY) {
                throw new OrderRuleViolation("Máximo " + MAX_QUANTITY + " unidades por producto y vendedor.");
            }
            merged.put(key, new Item(line.skuId(), line.offerId(), line.sellerId(), quantity, line.consignment()));
        }
        return List.copyOf(merged.values());
    }

    /** Agrupa por vendedor (líneas ya resueltas: cada oferta con su vendedor). */
    public static List<Group> groups(List<Item> items) {
        List<Item> own = new ArrayList<>();
        // Por vendedor y, dentro de él, primero lo que despacha el vendedor y luego la consignación.
        TreeMap<Long, TreeMap<Boolean, List<Item>>> bySeller = new TreeMap<>();
        for (Item item : items) {
            if (item.offerId() == null) {
                if (item.sellerId() != null) {
                    throw new OrderRuleViolation("Una venta propia no lleva vendedor externo.");
                }
                own.add(item);
            } else {
                Objects.requireNonNull(item.sellerId(), "Oferta sin vendedor resuelto");
                bySeller.computeIfAbsent(item.sellerId(), key -> new TreeMap<>())
                        .computeIfAbsent(item.consignment(), key -> new ArrayList<>()).add(item);
            }
        }
        List<Group> groups = new ArrayList<>();
        if (!own.isEmpty()) {
            groups.add(new Group(null, List.copyOf(own)));
        }
        bySeller.forEach((seller, byKind) -> byKind.forEach((consignment, list) -> {
            list.sort(Comparator.comparing(Item::offerId));
            groups.add(new Group(seller, List.copyOf(list), consignment));
        }));
        return List.copyOf(groups);
    }

    /** Ofertas en orden ascendente (orden fijo para bloquearlas). */
    public static List<Long> offersInLockOrder(List<Item> items) {
        return items.stream().map(Item::offerId).filter(Objects::nonNull).distinct().sorted().toList();
    }
}
