package com.essenza.draco.modules.catalog.infrastructure.outbound.persistence.schema;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tabla {@code product_option_images}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@IdClass(ProductOptionImageEntity.Pk.class)
@Table(name = "product_option_images")
public class ProductOptionImageEntity {

    @Id
    @Column(name = "product_id", columnDefinition = "bigint", nullable = false)
    private Long productId;

    @Id
    @Column(name = "option_id", columnDefinition = "bigint", nullable = false)
    private Long optionId;

    @Column(name = "image_url", columnDefinition = "varchar(500) not null")
    private String imageUrl;

    /** Llave compuesta. */
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long productId;
        private Long optionId;
    }
}
