package com.essenza.draco.modules.marketplace.infrastructure.outbound.persistence.schema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Tabla {@code mp_seller_contacts}: solo define el esquema para que Hibernate la cree ({@code ddl-auto}). La lectura y
 * escritura se hacen con JDBC en el adaptador del módulo; no usar esta clase en repositorios.
 */
@Getter
@Entity
@Table(name = "mp_seller_contacts",
        indexes = {
        @Index(name = "ix_mp_seller_contacts_seller", columnList = "seller_id, position")})
public class MpSellerContactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "seller_id", columnDefinition = "bigint not null")
    private Long sellerId;

    @Column(name = "position", columnDefinition = "int not null")
    private Integer position;

    @Column(name = "name", columnDefinition = "varchar(160) not null")
    private String name;

    @Column(name = "role", columnDefinition = "varchar(80)")
    private String role;

    @Column(name = "email", columnDefinition = "varchar(160)")
    private String email;

    @Column(name = "phone", columnDefinition = "varchar(40)")
    private String phone;

    @Column(name = "is_primary", columnDefinition = "tinyint(1) default 0 not null")
    private Boolean isPrimary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", referencedColumnName = "id", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_mp_seller_contacts_seller"))
    private MpSellerEntity seller;
}
