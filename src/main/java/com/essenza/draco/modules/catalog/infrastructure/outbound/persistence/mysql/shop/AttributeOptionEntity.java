package com.essenza.draco.modules.catalog.infrastructure.outbound.persistence.mysql.shop;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.ForeignKey;

/** Opción de un atributo OPTION. Borrado físico: solo se elimina si ningún valor la usa. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "attribute_options",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_attribute_options_value", columnNames = {"attribute_id", "value"})})
public class AttributeOptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_option")
    private Long id;

    // EAGER: Hibernate no admite LAZY hacia una entidad con @SoftDelete.
    @ManyToOne(optional = false)
    @JoinColumn(name = "attribute_id", nullable = false, foreignKey = @ForeignKey(name = "fk_attribute_options_attribute"))
    private AttributeEntity attribute;

    @Column(nullable = false, length = 120)
    private String value;

    @Column(nullable = false)
    private int position;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
