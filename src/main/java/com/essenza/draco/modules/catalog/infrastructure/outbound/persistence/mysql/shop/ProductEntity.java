package com.essenza.draco.modules.catalog.infrastructure.outbound.persistence.mysql.shop;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.util.List;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Column;

@SuperBuilder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "products",
        uniqueConstraints = @UniqueConstraint(name = "uk_products_slug", columnNames = "slug"),
        indexes = {
                @Index(name = "idx_products_template", columnList = "template_id")})
public class ProductEntity extends AuditInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_product")
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false)
    private Integer stock;

    /** Precio de costo. DECIMAL(15,2) desde la Fase 2 (migración V3). */
    @Column(name = "real_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal realPrice;

    /** Precio de venta. DECIMAL(15,2) desde la Fase 2 (migración V3). */
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /** Estado editorial: DRAFT | ACTIVE | INACTIVE | ARCHIVED (Fase 2). */
    @Column(nullable = false, length = 20)
    private String status;

    /** Identificador legible y único para URLs (Fase 2; único por uk_products_slug). */
    @Column(length = 180)
    private String slug;

    /** SIMPLE | VARIANT | COMBO, mantenido por ProductSkuSynchronizer (Fase 2). */
    @Column(name = "product_type", nullable = false, length = 20)
    private String productType;

    @Column(nullable = true)
    private Double length;

    @Column(nullable = true)
    private Double width;

    @Column(nullable = true)
    private Double height;

    @Column(nullable = true)
    private Double weight;

    @Column(name = "net_content", precision = 18, scale = 4)
    private java.math.BigDecimal netContent;

    @Column(name = "net_content_unit_id")
    private Long netContentUnitId;

    /** Tarifa de impuesto (V9); NULL = la tarifa por defecto. */
    @Column(name = "tax_rate_id")
    private Long taxRateId;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false)
    private Boolean available = true;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "brand_id", insertable = false, updatable = false)
    private BrandEntity brand;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private CategoryEntity category;

    @Column(name = "subcategory_id", nullable = false)
    private Long subcategoryId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subcategory_id", insertable = false, updatable = false)
    private SubcategoryEntity subcategory;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    // Sin asociación JPA hacia proveedor (inventario): se referencia solo por id (Fase 1, fronteras entre módulos).

    @Column(name = "is_combo", nullable = false)
    private Boolean isCombo = false;

    @OneToMany(mappedBy = "combo", fetch = FetchType.LAZY)
    private List<ProductComboEntity> productCombos;

    /** La escribe JDBC (solo lectura para JPA). */
    @Column(name = "template_id", columnDefinition = "bigint", insertable = false, updatable = false)
    private Long templateId;

}
