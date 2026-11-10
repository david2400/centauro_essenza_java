package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.schema;

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
 * Tabla {@code cash_register_methods}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@IdClass(CashRegisterMethodEntity.Pk.class)
@Table(name = "cash_register_methods")
public class CashRegisterMethodEntity {

    @Id
    @Column(name = "cash_register_id", columnDefinition = "bigint", nullable = false)
    private Long cashRegisterId;

    @Id
    @Column(name = "payment_method_id", columnDefinition = "bigint", nullable = false)
    private Long paymentMethodId;

    /** Llave compuesta. */
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long cashRegisterId;
        private Long paymentMethodId;
    }
}
