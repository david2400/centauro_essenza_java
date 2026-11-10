package com.essenza.draco.modules.catalog.infrastructure.outbound.persistence.mysql.shop;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Imagen de la galería de un producto; {@code sku_id} la asocia a un SKU concreto. */
@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "product_images", indexes = @Index(name = "idx_product_images_product", columnList = "product_id"))
public class ProductImageEntity extends AuditInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_image")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "sku_id")
    private Long skuId;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "alt_text")
    private String altText;

}
