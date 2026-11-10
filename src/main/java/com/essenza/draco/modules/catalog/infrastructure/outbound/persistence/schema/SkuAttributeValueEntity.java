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
 * Tabla {@code sku_attribute_values}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@IdClass(SkuAttributeValueEntity.Pk.class)
@Table(name = "sku_attribute_values",
        indexes = {
        @Index(name = "idx_sav_attribute", columnList = "attribute_id"),
        @Index(name = "idx_sav_option", columnList = "option_id")})
public class SkuAttributeValueEntity {

    @Id
    @Column(name = "sku_id", columnDefinition = "bigint", nullable = false)
    private Long skuId;

    @Id
    @Column(name = "attribute_id", columnDefinition = "bigint", nullable = false)
    private Long attributeId;

    @Column(name = "option_id", columnDefinition = "bigint not null")
    private Long optionId;

    /** Llave compuesta. */
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long skuId;
        private Long attributeId;
    }
}
