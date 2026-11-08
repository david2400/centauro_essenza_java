package com.essenza.draco.modules.inventory.infrastructure.outbound.repositories.stock;

import com.essenza.draco.modules.inventory.application.dto.StockItemDto;
import com.essenza.draco.modules.inventory.application.output.repository.StockItemRepository;
import com.essenza.draco.modules.inventory.domain.model.StockItem;
import com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.mysql.shop.StockItemEntity;
import com.essenza.draco.shared.common.inventory.StockQuery;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persistencia de {@link StockItem} y consulta de stock para otros módulos ({@link StockQuery}). */
@Repository
public class StockItemRepositoryAdapter implements StockItemRepository, StockQuery {

    private final JpaStockItemRepository jpa;

    public StockItemRepositoryAdapter(JpaStockItemRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public StockItem lockOrEmpty(Long skuId, Long productId, Long warehouseId) {
        return jpa.findForUpdate(skuId, warehouseId)
                .map(StockItemRepositoryAdapter::toDomain)
                .orElseGet(() -> StockItem.empty(skuId, productId, warehouseId));
    }

    @Override
    @Transactional
    public StockItem save(StockItem item) {
        StockItemEntity entity = item.getId() == null
                ? StockItemEntity.builder().skuId(item.getSkuId()).productId(item.getProductId())
                        .warehouseId(item.getWarehouseId()).build()
                : jpa.findById(item.getId()).orElseThrow();
        entity.setOnHand(item.getOnHand());
        entity.setReserved(item.getReserved());
        entity.setMinThreshold(item.getMinThreshold());
        return toDomain(jpa.save(entity));
    }

    @Override
    public List<StockItem> lockAllForSku(Long skuId) {
        return jpa.findAllForUpdateBySku(skuId).stream().map(StockItemRepositoryAdapter::toDomain).toList();
    }

    @Override
    public int totalOnHand(Long skuId) {
        return (int) jpa.sumOnHandBySku(skuId);
    }

    @Override
    public long totalOnHandInWarehouse(Long warehouseId) {
        return jpa.sumOnHandInWarehouse(warehouseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockItemDto> find(Long skuId, Long productId, Long warehouseId) {
        return jpa.search(skuId, productId, warehouseId).stream().map(StockItemRepositoryAdapter::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> onHandBySku(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        return jpa.findBySkuIdIn(skuIds).stream()
                .collect(Collectors.groupingBy(StockItemEntity::getSkuId, Collectors.summingInt(StockItemEntity::getOnHand)));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> onHandBySku(Collection<Long> skuIds, Long warehouseId) {
        if (warehouseId == null) {
            return onHandBySku(skuIds);
        }
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        return jpa.findBySkuIdIn(skuIds).stream().filter(e -> warehouseId.equals(e.getWarehouseId()))
                .collect(Collectors.groupingBy(StockItemEntity::getSkuId, Collectors.summingInt(e -> nz(e.getOnHand()))));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> availableBySku(Collection<Long> skuIds, Long warehouseId) {
        if (warehouseId == null) {
            return availableBySku(skuIds);
        }
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        return jpa.findBySkuIdIn(skuIds).stream().filter(e -> warehouseId.equals(e.getWarehouseId()))
                .collect(Collectors.groupingBy(StockItemEntity::getSkuId,
                        Collectors.summingInt(e -> Math.max(nz(e.getOnHand()) - nz(e.getReserved()), 0))));
    }

    @Override
    public Map<Long, Integer> availableBySku(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        return jpa.findBySkuIdIn(skuIds).stream()
                .collect(Collectors.groupingBy(StockItemEntity::getSkuId,
                        Collectors.summingInt(e -> Math.max(nz(e.getOnHand()) - nz(e.getReserved()), 0))));
    }

    static StockItem toDomain(StockItemEntity e) {
        return new StockItem(e.getId(), e.getSkuId(), e.getProductId(), e.getWarehouseId(),
                nz(e.getOnHand()), nz(e.getReserved()), nz(e.getMinThreshold()));
    }

    static StockItemDto toDto(StockItemEntity e) {
        StockItem item = toDomain(e);
        return StockItemDto.builder()
                .id(e.getId())
                .skuId(e.getSkuId())
                .productId(e.getProductId())
                .warehouseId(e.getWarehouseId())
                .onHand(item.getOnHand())
                .reserved(item.getReserved())
                .available(item.available())
                .minThreshold(item.getMinThreshold())
                .belowThreshold(item.isBelowThreshold())
                .build();
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
