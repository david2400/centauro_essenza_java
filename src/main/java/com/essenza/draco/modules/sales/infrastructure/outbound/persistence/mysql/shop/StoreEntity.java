package com.essenza.draco.modules.sales.infrastructure.outbound.persistence.mysql.shop;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;
import jakarta.persistence.UniqueConstraint;

/** V11: punto de venta (tienda o sede). */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@Entity
@Table(name = "stores",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_stores_code_active", columnNames = {"code_active"})})
public class StoreEntity extends AuditInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_store")
    private Long id;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "city", length = 80)
    private String city;

    /** V12: ubicación del catálogo del servicio parametros (ids, sin FK: otra base de datos). */
    @Column(name = "country_id")
    private Long countryId;

    @Column(name = "state_id")
    private Long stateId;

    @Column(name = "city_id")
    private Long cityId;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "opening_hours", length = 255)
    private String openingHours;

    @Column(name = "ticket_header", length = 500)
    private String ticketHeader;

    @Column(name = "ticket_footer", length = 500)
    private String ticketFooter;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /** Columna generada (solo lectura para JPA). */
    @Column(name = "code_active", columnDefinition = "varchar(30) generated always as (if(deleted = 0, code, null)) stored", insertable = false, updatable = false)
    private String codeActive;
}
