package com.essenza.draco.modules.product_details.application.dto.unit;

/** Dónde se usa una unidad. */
public record UnitUsage(long attributes, long products, long variants, long legacyFeatures) {

    public long total() {
        return attributes + products + variants + legacyFeatures;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        append(sb, attributes, "atributo(s)");
        append(sb, products, "producto(s)");
        append(sb, variants, "variante(s)");
        append(sb, legacyFeatures, "característica(s) antigua(s)");
        return sb.toString();
    }

    private static void append(StringBuilder sb, long count, String label) {
        if (count > 0) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(count).append(' ').append(label);
        }
    }
}
