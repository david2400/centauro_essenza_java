package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.schema;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tabla {@code payment_checkout_group_items}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@IdClass(PaymentCheckoutGroupItemEntity.Pk.class)
@Table(name = "payment_checkout_group_items",
        uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_checkout_group_items_payment", columnNames = {"payment_id"})})
public class PaymentCheckoutGroupItemEntity {

    @Id
    @Column(name = "reference", columnDefinition = "varchar(40)", nullable = false)
    private String reference;

    @Id
    @Column(name = "payment_id", columnDefinition = "bigint", nullable = false)
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference", referencedColumnName = "reference", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_payment_checkout_group_items_group"))
    private PaymentCheckoutGroupEntity referenceRef;

    /** Llave compuesta. */
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private static final long serialVersionUID = 1L;

        private String reference;
        private Long paymentId;
    }
}
