package com.essenza.draco.modules.catalog.infrastructure.outbound.persistence.mysql.shop;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * SKU: unidad vendible. Un producto simple o combo tiene un SKU por defecto; un
 * producto con variantes tiene un SKU por variante ({@code legacy_child_id}).
 * En la Fase 2 se mantiene sincronizado desde el producto (ProductSkuSynchronizer);
 * el stock por SKU llega en la Fase 3.
 */
@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "product_skus",
        indexes = @Index(name = "idx_product_skus_product", columnList = "product_id"),
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_skus_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_product_skus_legacy_child", columnNames = "legacy_child_id")
        })
public class ProductSkuEntity extends AuditInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sku")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "cost_price", precision = 15, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "compare_at_price", precision = 15, scale = 2)
    private BigDecimal compareAtPrice;

    @Column(length = 64)
    private String barcode;

    private Double weight;
    private Double length;
    private Double width;
    private Double height;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    @Column(nullable = false)
    private Boolean active;

    /** Variante heredada (product_childs.id_product_child) de la que proviene. */
    @Column(name = "legacy_child_id")
    private Long legacyChildId;

    @Version
    @Column(nullable = false)
    private Long version;

}
