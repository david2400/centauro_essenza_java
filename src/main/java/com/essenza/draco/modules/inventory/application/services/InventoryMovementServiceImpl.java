package com.essenza.draco.modules.inventory.application.services;

import com.essenza.draco.modules.inventory.application.dto.InventoryMovementDto;
import com.essenza.draco.modules.inventory.application.dto.SkuRef;
import com.essenza.draco.modules.inventory.application.dto.StockItemDto;
import com.essenza.draco.modules.inventory.application.input.movement.RegisterEntryUseCase;
import com.essenza.draco.modules.inventory.application.input.movement.RegisterExitUseCase;
import com.essenza.draco.modules.inventory.application.input.movement.RegisterTransferUseCase;
import com.essenza.draco.modules.inventory.application.input.stock.AlignStockUseCase;
import com.essenza.draco.modules.inventory.application.input.stock.FindStockUseCase;
import com.essenza.draco.modules.inventory.application.output.repository.InventoryMovementRepository;
import com.essenza.draco.modules.inventory.application.output.repository.LegacyStockProjectionPort;
import com.essenza.draco.modules.inventory.application.output.repository.MainWarehousePort;
import com.essenza.draco.modules.inventory.application.output.repository.SkuCatalogPort;
import com.essenza.draco.modules.inventory.application.output.repository.StockItemRepository;
import com.essenza.draco.modules.inventory.domain.model.InsufficientStockException;
import com.essenza.draco.modules.inventory.domain.model.StockItem;
import com.essenza.draco.shared.exceptions.ConflictException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Movimientos de inventario por SKU y bodega (Fase 3).
 *
 * <p>Cada operación bloquea las filas de {@code stock_items} afectadas
 * (SELECT … FOR UPDATE), aplica las reglas del agregado {@link StockItem},
 * registra el movimiento en el ledger y refresca las columnas heredadas.
 */
@Service
@RequiredArgsConstructor
public class InventoryMovementServiceImpl implements RegisterEntryUseCase, RegisterExitUseCase, RegisterTransferUseCase,
        AlignStockUseCase, FindStockUseCase,
        com.essenza.draco.modules.inventory.application.input.movement.ReceiveInitialStockUseCase {

    static final String MANUAL = "MANUAL";

    private final InventoryMovementRepository movementRepository;
    private final StockItemRepository stockItems;
    private final SkuCatalogPort skuCatalog;
    private final MainWarehousePort mainWarehouse;
    private final LegacyStockProjectionPort legacyProjection;

    @Transactional
    public InventoryMovementDto registerEntry(InventoryMovementDto dto) {
        if (dto.getToWarehouseId() == null) throw new IllegalArgumentException("toWarehouseId es obligatorio en una ENTRADA");
        SkuRef sku = skuCatalog.resolve(dto.getProductId(), dto.getSkuId());
        receive(sku, dto.getToWarehouseId(), dto.getQuantity());
        return finish(sku, null, dto.getToWarehouseId(), "ENTRY", dto.getQuantity(), dto.getReason(), MANUAL);
    }

    @Transactional
    public InventoryMovementDto registerExit(InventoryMovementDto dto) {
        if (dto.getFromWarehouseId() == null) throw new IllegalArgumentException("fromWarehouseId es obligatorio en una SALIDA");
        SkuRef sku = skuCatalog.resolve(dto.getProductId(), dto.getSkuId());
        issue(sku, dto.getFromWarehouseId(), dto.getQuantity());
        return finish(sku, dto.getFromWarehouseId(), null, "EXIT", dto.getQuantity(), dto.getReason(), MANUAL);
    }

    @Transactional
    public InventoryMovementDto registerTransfer(InventoryMovementDto dto) {
        Long from = dto.getFromWarehouseId();
        Long to = dto.getToWarehouseId();
        if (from == null || to == null) throw new IllegalArgumentException("fromWarehouseId y toWarehouseId son obligatorios en un TRASLADO");
        if (Objects.equals(from, to)) throw new IllegalArgumentException("La bodega de origen y la de destino deben ser distintas");
        SkuRef sku = skuCatalog.resolve(dto.getProductId(), dto.getSkuId());
        // Orden fijo de bloqueo (menor id primero) para evitar interbloqueos entre traslados cruzados.
        if (from < to) {
            issue(sku, from, dto.getQuantity());
            receive(sku, to, dto.getQuantity());
        } else {
            StockItem target = stockItems.lockOrEmpty(sku.skuId(), sku.productId(), to);
            issue(sku, from, dto.getQuantity());
            target.receive(dto.getQuantity());
            stockItems.save(target);
        }
        return finish(sku, from, to, "TRANSFER", dto.getQuantity(), dto.getReason(), MANUAL);
    }

    @Override
    @Transactional
    public InventoryMovementDto align(Long productId, Long skuId, int desiredOnHand, String source) {
        if (desiredOnHand < 0) throw new IllegalArgumentException("El stock no puede ser negativo");
        SkuRef sku = skuCatalog.resolve(productId, skuId);
        int delta = desiredOnHand - stockItems.totalOnHand(sku.skuId());
        if (delta == 0) {
            return null;
        }
        Long main = mainWarehouse.mainWarehouseId();
        String reason = "Ajuste desde la ficha del producto (stock total " + desiredOnHand + ")";
        if (delta > 0) {
            receive(sku, main, delta);
            return finish(sku, null, main, "ENTRY", delta, reason, source);
        }
        StockItem item = stockItems.lockOrEmpty(sku.skuId(), sku.productId(), main);
        try {
            item.issue(-delta);
        } catch (InsufficientStockException ex) {
            throw new ConflictException("No se puede bajar el stock a " + desiredOnHand + " desde la ficha: la bodega principal solo tiene "
                    + ex.getAvailable() + " unidades disponibles. Registra salidas o traslados en las demás bodegas.");
        }
        stockItems.save(item);
        return finish(sku, main, null, "EXIT", -delta, reason, source);
    }

    @Override
    @Transactional
    public void receiveInitial(Long productId, Long skuId, Long warehouseId, int quantity, String source) {
        if (quantity < 0) throw new IllegalArgumentException("El stock inicial no puede ser negativo");
        if (quantity == 0) {
            return;
        }
        SkuRef sku = skuCatalog.resolve(productId, skuId);
        Long target = warehouseId == null ? mainWarehouse.mainWarehouseId() : warehouseId;
        receive(sku, target, quantity);
        finish(sku, null, target, "ENTRY", quantity, "Stock inicial de la variante", source);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockItemDto> find(Long skuId, Long productId, Long warehouseId) {
        return stockItems.find(skuId, productId, warehouseId);
    }

    @Transactional(readOnly = true)
    public Page<InventoryMovementDto> list(Pageable pageable) {
        return movementRepository.findAll(pageable);
    }

    private void receive(SkuRef sku, Long warehouseId, int quantity) {
        StockItem item = stockItems.lockOrEmpty(sku.skuId(), sku.productId(), warehouseId);
        item.receive(quantity);
        stockItems.save(item);
    }

    private void issue(SkuRef sku, Long warehouseId, int quantity) {
        StockItem item = stockItems.lockOrEmpty(sku.skuId(), sku.productId(), warehouseId);
        try {
            item.issue(quantity);
        } catch (InsufficientStockException ex) {
            throw new ConflictException("Stock insuficiente en la bodega " + warehouseId + ": disponibles "
                    + ex.getAvailable() + ", solicitadas " + ex.getRequested());
        }
        stockItems.save(item);
    }

    private InventoryMovementDto finish(SkuRef sku, Long from, Long to, String type, int quantity, String reason, String source) {
        InventoryMovementDto movement = new InventoryMovementDto();
        movement.setProductId(sku.productId());
        movement.setSkuId(sku.skuId());
        movement.setFromWarehouseId(from);
        movement.setToWarehouseId(to);
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setReason(reason);
        movement.setReferenceType(source);
        InventoryMovementDto saved = movementRepository.save(movement);
        legacyProjection.refresh(sku.productId());
        return saved;
    }
}
