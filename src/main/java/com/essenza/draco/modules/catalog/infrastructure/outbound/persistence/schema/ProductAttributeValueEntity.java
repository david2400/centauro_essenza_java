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
 * Tabla {@code product_attribute_values}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@IdClass(ProductAttributeValueEntity.Pk.class)
@Table(name = "product_attribute_values",
        indexes = {
        @Index(name = "idx_pav_attribute", columnList = "attribute_id"),
        @Index(name = "idx_pav_option", columnList = "option_id")})
public class ProductAttributeValueEntity {

    @Id
    @Column(name = "product_id", columnDefinition = "bigint", nullable = false)
    private Long productId;

    @Id
    @Column(name = "attribute_id", columnDefinition = "bigint", nullable = false)
    private Long attributeId;

    @Column(name = "value_text", columnDefinition = "varchar(500)")
    private String valueText;

    @Column(name = "value_number", columnDefinition = "decimal(18,4)")
    private java.math.BigDecimal valueNumber;

    @Column(name = "value_boolean", columnDefinition = "tinyint(1)")
    private Boolean valueBoolean;

    @Column(name = "option_id", columnDefinition = "bigint")
    private Long optionId;

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
