package com.essenza.draco.modules.inventory.infrastructure.outbound.repositories.stock;

import com.essenza.draco.modules.inventory.application.dto.SkuRef;
import com.essenza.draco.modules.inventory.application.output.repository.SkuCatalogPort;
import com.essenza.draco.shared.exceptions.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Lectura de SKUs del catálogo por SQL (sin importar clases del módulo catalog):
 * el inventario solo necesita saber qué SKU corresponde a un movimiento.
 */
@Component
public class SkuCatalogAdapter implements SkuCatalogPort {

    @PersistenceContext
    private EntityManager em;

    @Override
    public SkuRef resolve(Long productId, Long skuId) {
        if (skuId != null) {
            List<?> rows = em.createNativeQuery(
                            "select product_id from product_skus where id_sku = :sku and deleted = 0")
                    .setParameter("sku", skuId).getResultList();
            if (rows.isEmpty()) throw new NotFoundException("SKU no encontrado: " + skuId);
            Long owner = ((Number) rows.get(0)).longValue();
            if (productId != null && !Objects.equals(owner, productId)) {
                throw new IllegalArgumentException("El SKU " + skuId + " no pertenece al producto " + productId);
            }
            return new SkuRef(skuId, owner);
        }
        if (productId == null) throw new IllegalArgumentException("Indica productId o skuId");
        List<?> type = em.createNativeQuery(
                        "select product_type from products where id_product = :p and deleted = 0")
                .setParameter("p", productId).getResultList();
        if (type.isEmpty()) throw new NotFoundException("Producto no encontrado: " + productId);
        if ("VARIANT".equals(type.get(0))) {
            List<?> variants = em.createNativeQuery(
                            "select id_sku from product_skus where product_id = :p and legacy_child_id is not null and deleted = 0")
                    .setParameter("p", productId).getResultList();
            if (variants.size() != 1) {
                throw new IllegalArgumentException("El producto " + productId + " tiene variantes: indica el SKU (sku_id) del movimiento");
            }
            return new SkuRef(((Number) variants.get(0)).longValue(), productId);
        }
        List<?> sku = em.createNativeQuery(
                        "select id_sku from product_skus where product_id = :p and legacy_child_id is null and deleted = 0 "
                                + "order by is_default desc, id_sku limit 1")
                .setParameter("p", productId).getResultList();
        if (sku.isEmpty()) throw new NotFoundException("El producto " + productId + " no tiene SKU por defecto");
        return new SkuRef(((Number) sku.get(0)).longValue(), productId);
    }
}
