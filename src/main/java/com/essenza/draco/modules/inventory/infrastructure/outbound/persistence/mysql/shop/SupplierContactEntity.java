package com.essenza.draco.modules.inventory.infrastructure.outbound.persistence.mysql.shop;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Index;

/** V13: persona de contacto del proveedor (se reemplazan todas al guardar la ficha). */
@Data
@NoArgsConstructor
@Entity
@Table(name = "supplier_contacts",
        indexes = {
                @Index(name = "ix_supplier_contacts_supplier", columnList = "supplier_id")})
public class SupplierContactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_supplier_contact")
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "position", length = 80)
    private String position;

    @Column(name = "area", nullable = false, length = 12)
    private String area;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "is_primary", nullable = false)
    private Boolean primary;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

}
