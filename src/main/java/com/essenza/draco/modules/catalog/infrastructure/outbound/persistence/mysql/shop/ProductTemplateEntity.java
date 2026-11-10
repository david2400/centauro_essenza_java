package com.essenza.draco.modules.catalog.infrastructure.outbound.persistence.mysql.shop;

import java.util.ArrayList;
import java.util.List;

import com.essenza.draco.shared.common.domain.entity.AuditInfo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.UniqueConstraint;

/** Plantilla de producto (tabla {@code product_templates}, V5; antes {@code type_products}). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_templates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_templates_name_active", columnNames = {"name_active"}),
                @UniqueConstraint(name = "uk_product_templates_legacy", columnNames = {"legacy_type_product_id"})})
public class ProductTemplateEntity extends AuditInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_template")
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TemplateAttributeEntity> attributes = new ArrayList<>();

    /** Columna generada (solo lectura para JPA). */
    @Column(name = "name_active", columnDefinition = "varchar(120) generated always as (if(deleted = 0, name, null)) virtual", insertable = false, updatable = false)
    private String nameActive;

    /** La escribe JDBC (solo lectura para JPA). */
    @Column(name = "legacy_type_product_id", columnDefinition = "bigint", insertable = false, updatable = false)
    private Long legacyTypeProductId;
}
