package com.essenza.draco.modules.catalog.infrastructure.outbound.persistence.schema;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tabla {@code product_variant_axes}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@IdClass(ProductVariantAxisEntity.Pk.class)
@Table(name = "product_variant_axes",
        indexes = {
        @Index(name = "ix_product_variant_axes_attribute", columnList = "attribute_id")})
public class ProductVariantAxisEntity {

    @Id
    @Column(name = "product_id", columnDefinition = "bigint", nullable = false)
    private Long productId;

    @Id
    @Column(name = "attribute_id", columnDefinition = "bigint", nullable = false)
    private Long attributeId;

    @Column(name = "position", columnDefinition = "int default 0 not null")
    private Integer position;

    @Column(name = "image_axis", columnDefinition = "tinyint(1) default 0 not null")
    private Boolean imageAxis;

    /** Llave compuesta. */
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long productId;
        private Long attributeId;
    }
}
