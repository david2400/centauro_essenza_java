package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.schema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Tabla {@code order_numbering}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "order_numbering")
public class OrderNumberingEntity {

    @Id
    @Column(name = "id", columnDefinition = "tinyint", nullable = false)
    private Short id;

    @Column(name = "prefix", columnDefinition = "varchar(10) default 'ORD' not null")
    private String prefix;

    @Column(name = "padding", columnDefinition = "int default 6 not null")
    private Integer padding;

    @Column(name = "next_number", columnDefinition = "bigint default 1 not null")
    private Long nextNumber;
}
