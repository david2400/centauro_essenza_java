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
import jakarta.persistence.CheckConstraint;

/**
 * Atributo del catálogo (tabla {@code attributes}, V5). El código es único entre
 * los no borrados mediante la columna generada {@code code_active} (no mapeada).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "attributes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_attributes_code_active", columnNames = {"code_active"}),
                @UniqueConstraint(name = "uk_attributes_legacy_feature", columnNames = {"legacy_feature_id"})},
        check = {
                @CheckConstraint(name = "ck_attributes_data_type", constraint = "data_type IN ('TEXT', 'NUMBER', 'BOOLEAN', 'OPTION')")})
public class AttributeEntity extends AuditInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_attribute")
    private Long id;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType;

    @Column(name = "unit_id")
    private Long unitId;

    @Column(name = "legacy_feature_id", insertable = false, updatable = false)
    private Long legacyFeatureId;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "attribute", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    private List<AttributeOptionEntity> options = new ArrayList<>();

    /** Columna generada (solo lectura para JPA). */
    @Column(name = "code_active", columnDefinition = "varchar(60) generated always as (if(deleted = 0, code, null)) virtual", insertable = false, updatable = false)
    private String codeActive;
}
