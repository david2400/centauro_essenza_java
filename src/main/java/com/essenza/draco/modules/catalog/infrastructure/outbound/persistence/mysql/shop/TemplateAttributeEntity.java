package com.essenza.draco.modules.catalog.infrastructure.outbound.persistence.mysql.shop;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Index;
import jakarta.persistence.ForeignKey;

/** Atributo de una plantilla (tabla {@code template_attributes}, V5). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "template_attributes",
        indexes = {
                @Index(name = "idx_template_attributes_attribute", columnList = "attribute_id")})
public class TemplateAttributeEntity {

    @EmbeddedId
    private Key id = new Key();

    @MapsId("templateId")
    // EAGER: Hibernate no admite LAZY hacia una entidad con @SoftDelete.
    @ManyToOne(optional = false)
    @JoinColumn(name = "template_id", foreignKey = @ForeignKey(name = "fk_template_attributes_template"))
    private ProductTemplateEntity template;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "variant_axis", nullable = false)
    private boolean variantAxis;

    @Column(nullable = false)
    private boolean filterable;

    @Column(nullable = false)
    private int position;

    @Getter
    @Setter
    @NoArgsConstructor
    @Embeddable
    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;

        @Column(name = "template_id")
        private Long templateId;

        @Column(name = "attribute_id")
        private Long attributeId;

        public Key(Long templateId, Long attributeId) {
            this.templateId = templateId;
            this.attributeId = attributeId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(templateId, key.templateId) && Objects.equals(attributeId, key.attributeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(templateId, attributeId);
        }
    }

}
