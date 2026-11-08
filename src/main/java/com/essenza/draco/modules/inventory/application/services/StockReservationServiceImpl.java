package com.essenza.draco.modules.inventory.application.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.inventory.application.dto.InventoryMovementDto;
import com.essenza.draco.modules.inventory.application.output.repository.InventoryMovementRepository;
import com.essenza.draco.modules.inventory.application.output.repository.LegacyStockProjectionPort;
import com.essenza.draco.modules.inventory.application.output.repository.MainWarehousePort;
import com.essenza.draco.modules.inventory.application.output.repository.StockItemRepository;
import com.essenza.draco.modules.inventory.application.output.repository.StockReservationRepository;
import com.essenza.draco.modules.inventory.domain.model.StockItem;
import com.essenza.draco.modules.inventory.domain.model.StockReservation;
import com.essenza.draco.shared.common.inventory.StockReservations;
import com.essenza.draco.shared.exceptions.ConflictException;

/**
 * Reservas de stock para órdenes. Se une a la transacción de ventas: si algo
 * falla (p. ej. falta stock), se revierte todo, incluida la línea de la orden.
 */
@Service
@Transactional(propagation = Propagation.REQUIRED)
public class StockReservationServiceImpl implements StockReservations {

    static final String ORDER = "ORDER";
    static final String ORDER_CANCEL = "ORDER_CANCEL";

    private final StockReservationRepository reservations;
    private final StockItemRepository stockItems;
    private final MainWarehousePort mainWarehouse;
    private final InventoryMovementRepository movements;
    private final LegacyStockProjectionPort legacyProjection;

    public StockReservationServiceImpl(StockReservationRepository reservations, StockItemRepository stockItems,
                                       MainWarehousePort mainWarehouse, InventoryMovementRepository movements,
                                       LegacyStockProjectionPort legacyProjection) {
        this.reservations = reservations;
        this.stockItems = stockItems;
        this.mainWarehouse = mainWarehouse;
        this.movements = movements;
        this.legacyProjection = legacyProjection;
    }

    @Override
    public void reserveLine(Long orderId, Long lineId, Long productId, Long skuId, int quantity, Instant expiresAt) {
        reserveLine(orderId, lineId, productId, skuId, quantity, expiresAt, null);
    }

    @Override
    public void reserveLine(Long orderId, Long lineId, Long productId, Long skuId, int quantity, Instant expiresAt,
                            Long warehouseId) {
        if (quantity < 0) {
            throw new IllegalArgumentException("La cantidad no puede ser negativa");
        }
        List<StockReservation> active = new ArrayList<>(reservations.activeByLine(lineId));
        // Si cambió el SKU de la línea, se libera todo lo anterior y se reserva el nuevo.
        if (active.stream().anyMatch(reservation -> !Objects.equals(reservation.getSkuId(), skuId))) {
            for (StockReservation reservation : active) {
                unreserve(reservation, reservation.getQuantity());
            }
            active.clear();
        }
        int current = active.stream().mapToInt(StockReservation::getQuantity).sum();
        if (quantity > current) {
            allocate(orderId, lineId, productId, skuId, quantity - current, expiresAt, active, warehouseId);
        } else if (quantity < current) {
            int remaining = current - quantity;
            // Se libera primero lo último que se reservó (las bodegas secundarias).
            active.sort(Comparator.comparing(StockReservation::getId, Comparator.nullsLast(Comparator.reverseOrder())));
            for (StockReservation reservation : active) {
                if (remaining == 0) {
                    break;
                }
                int units = Math.min(remaining, reservation.getQuantity());
                unreserve(reservation, units);
                remaining -= units;
            }
        }
        for (StockReservation reservation : reservations.activeByLine(lineId)) {
            reservation.extendUntil(expiresAt);
            reservations.save(reservation);
        }
    }

    private void allocate(Long orderId, Long lineId, Long productId, Long skuId, int units, Instant expiresAt,
                          List<StockReservation> active, Long onlyWarehouse) {
        Long main = mainWarehouse.mainWarehouseId();
        // Bloqueo en orden fijo (por bodega) para evitar interbloqueos; luego se elige principal primero.
        List<StockItem> items = new ArrayList<>(stockItems.lockAllForSku(skuId));
        if (onlyWarehouse != null) {
            // Venta de mostrador: solo la bodega del punto de venta.
            items.removeIf(item -> !Objects.equals(item.getWarehouseId(), onlyWarehouse));
        }
        int available = items.stream().mapToInt(StockItem::available).sum();
        if (available < units) {
            throw new ConflictException("Stock insuficiente para el SKU " + skuId
                    + (onlyWarehouse != null ? " en la bodega del punto de venta" : "") + ": disponibles " + available
                    + ", se necesitan " + units + " más.");
        }
        items.sort(Comparator.comparing((StockItem item) -> !Objects.equals(item.getWarehouseId(), main))
                .thenComparing(Comparator.comparingInt(StockItem::available).reversed())
                .thenComparing(StockItem::getWarehouseId));
        int remaining = units;
        for (StockItem item : items) {
            if (remaining == 0) {
                break;
            }
            int take = Math.min(remaining, item.available());
            if (take <= 0) {
                continue;
            }
            item.reserve(take);
            stockItems.save(item);
            Optional<StockReservation> existing = active.stream()
                    .filter(reservation -> Objects.equals(reservation.getWarehouseId(), item.getWarehouseId()))
                    .findFirst();
            if (existing.isPresent()) {
                existing.get().increase(take);
                reservations.save(existing.get());
            } else {
                active.add(reservations.save(StockReservation.open(orderId, lineId, skuId, productId,
                        item.getWarehouseId(), take, expiresAt)));
            }
            remaining -= take;
        }
    }

    private void unreserve(StockReservation reservation, int units) {
        StockItem item = stockItems.lockOrEmpty(reservation.getSkuId(), reservation.getProductId(), reservation.getWarehouseId());
        item.unreserve(units);
        stockItems.save(item);
        reservation.decrease(units);
        reservations.save(reservation);
    }

    @Override
    public void commitOrder(Long orderId) {
        Set<Long> products = new LinkedHashSet<>();
        for (StockReservation reservation : sorted(reservations.byOrder(orderId))) {
            if (!reservation.isActive() || reservation.getQuantity() == 0) {
                continue;
            }
            StockItem item = stockItems.lockOrEmpty(reservation.getSkuId(), reservation.getProductId(), reservation.getWarehouseId());
            item.commitReserved(reservation.getQuantity());
            stockItems.save(item);
            reservation.commit();
            reservations.save(reservation);
            record(reservation, reservation.getWarehouseId(), null, "EXIT", ORDER, "Venta · orden #" + orderId);
            products.add(reservation.getProductId());
        }
        products.forEach(legacyProjection::refresh);
    }

    @Override
    public void releaseOrder(Long orderId, boolean expired) {
        for (StockReservation reservation : sorted(reservations.byOrder(orderId))) {
            if (!reservation.isActive()) {
                continue;
            }
            StockItem item = stockItems.lockOrEmpty(reservation.getSkuId(), reservation.getProductId(), reservation.getWarehouseId());
            item.unreserve(reservation.getQuantity());
            stockItems.save(item);
            reservation.release(expired);
            reservations.save(reservation);
        }
    }

    @Override
    public void restockOrder(Long orderId) {
        Set<Long> products = new LinkedHashSet<>();
        for (StockReservation reservation : sorted(reservations.byOrder(orderId))) {
            if (reservation.getStatus() != StockReservation.Status.COMMITTED || reservation.getQuantity() == 0) {
                continue;
            }
            StockItem item = stockItems.lockOrEmpty(reservation.getSkuId(), reservation.getProductId(), reservation.getWarehouseId());
            item.receive(reservation.getQuantity());
            stockItems.save(item);
            reservation.markReturned();
            reservations.save(reservation);
            record(reservation, null, reservation.getWarehouseId(), "ENTRY", ORDER_CANCEL, "Cancelación · orden #" + orderId);
            products.add(reservation.getProductId());
        }
        products.forEach(legacyProjection::refresh);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationLine> reservations(Long orderId) {
        return reservations.byOrder(orderId).stream()
                .map(reservation -> new ReservationLine(reservation.getId(), reservation.getLineId(), reservation.getSkuId(),
                        reservation.getWarehouseId(), reservation.getQuantity(), reservation.getStatus().name(),
                        reservation.getExpiresAt()))
                .toList();
    }

    /** Orden fijo (SKU, bodega) al bloquear varias existencias en la misma transacción. */
    private static List<StockReservation> sorted(List<StockReservation> list) {
        List<StockReservation> copy = new ArrayList<>(list);
        copy.sort(Comparator.comparing(StockReservation::getSkuId).thenComparing(StockReservation::getWarehouseId));
        return copy;
    }

    private void record(StockReservation reservation, Long from, Long to, String type, String referenceType, String reason) {
        InventoryMovementDto movement = new InventoryMovementDto();
        movement.setProductId(reservation.getProductId());
        movement.setSkuId(reservation.getSkuId());
        movement.setFromWarehouseId(from);
        movement.setToWarehouseId(to);
        movement.setType(type);
        movement.setQuantity(reservation.getQuantity());
        movement.setReason(reason);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(reservation.getOrderId());
        movements.save(movement);
    }
}
