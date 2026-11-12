package com.essenza.draco.config.schema;

import java.util.List;

/**
 * Lo que el esquema necesita y Hibernate no crea desde las entidades. Lo aplica {@link SchemaFinisher} al arrancar,
 * solo si falta; reemplaza a los scripts {@code db/fk-borrado-logico.sql} y {@code db/transicion-jpa.sql}.
 *
 * <ul>
 * <li>Llaves foráneas hacia entidades con borrado lógico ({@code @SoftDelete}): Hibernate 7 no admite un
 * {@code @ManyToOne} LAZY hacia ellas, así que no se declaran en las entidades.</li>
 * <li>Llaves foráneas entre módulos: un módulo no importa entidades de otro, pero la base sí debe protegerlas.</li>
 * <li>Columnas que pasaron a admitir NULL: {@code ddl-auto=update} no cambia la nulabilidad de una columna existente.</li>
 * </ul>
 *
 * Las restricciones CHECK no van aquí: se declaran en las entidades ({@code @Table(check = ...)}) y
 * {@link SchemaFinisher} las agrega o las actualiza en tablas existentes.
 */
public final class SchemaConstraints {

    /** {@code table.column → refTable.refColumn} con su nombre. */
    public record ForeignKey(String name, String table, String column, String refTable, String refColumn) {
    }

    /** Columna que la entidad declara opcional. */
    public record NullableColumn(String table, String column) {
    }

    /** Hacia entidades con borrado lógico (antes db/fk-borrado-logico.sql). */
    static final List<ForeignKey> SOFT_DELETE_TARGETS = List.of(
            fk("fk_cash_movements_session", "cash_movements", "cash_session_id", "cash_sessions", "id_cash_session"),
            fk("fk_cash_registers_store", "cash_registers", "store_id", "stores", "id_store"),
            fk("fk_cash_session_counts_session", "cash_session_counts", "cash_session_id", "cash_sessions", "id_cash_session"),
            fk("fk_cash_sessions_register", "cash_sessions", "cash_register_id", "cash_registers", "id_cash_register"),
            fk("fk_inv_purchase_orders_supplier", "inv_purchase_orders", "supplier_id", "suppliers", "id_supplier"),
            fk("fk_inv_purchase_orders_warehouse", "inv_purchase_orders", "warehouse_id", "warehouses", "id_warehouse"),
            fk("fk_invoice_lines_invoice", "invoice_lines", "invoice_id", "invoices", "id_invoice"),
            fk("fk_invoice_sources_invoice", "invoice_sources", "invoice_id", "invoices", "id_invoice"),
            fk("fk_invoices_related", "invoices", "related_invoice_id", "invoices", "id_invoice"),
            fk("fk_invoices_resolution", "invoices", "resolution_id", "billing_resolutions", "id_billing_resolution"),
            fk("fk_orders_cash_session", "orders", "cash_session_id", "cash_sessions", "id_cash_session"),
            fk("fk_payment_events_payment", "payment_events", "payment_id", "payments", "id_payment"),
            fk("fk_payments_cash_session", "payments", "cash_session_id", "cash_sessions", "id_cash_session"),
            fk("fk_payments_order", "payments", "order_id", "orders", "id_order"),
            fk("fk_payments_type", "payments", "payment_type_id", "payment_types", "id_payment_type"),
            fk("fk_product_images_product", "product_images", "product_id", "products", "id_product"),
            fk("fk_product_images_sku", "product_images", "sku_id", "product_skus", "id_sku"),
            fk("fk_product_skus_product", "product_skus", "product_id", "products", "id_product"),
            fk("fk_products_template", "products", "template_id", "product_templates", "id_template"),
            fk("fk_reservations_warehouse", "stock_reservations", "warehouse_id", "warehouses", "id_warehouse"),
            fk("fk_stock_items_warehouse", "stock_items", "warehouse_id", "warehouses", "id_warehouse"),
            fk("fk_supplier_contacts_supplier", "supplier_contacts", "supplier_id", "suppliers", "id_supplier"),
            fk("fk_supplier_products_supplier", "supplier_products", "supplier_id", "suppliers", "id_supplier"),
            fk("fk_template_attributes_attribute", "template_attributes", "attribute_id", "attributes", "id_attribute"),
            fk("fk_warehouse_locations_warehouse", "warehouse_locations", "warehouse_id", "warehouses", "id_warehouse")
    );

    /** Entre tablas de distintos módulos o sin asociación JPA (antes en las migraciones V4–V28). */
    static final List<ForeignKey> UNMAPPED = List.of(
            fk("fk_attributes_unit", "attributes", "unit_id", "units_measurement", "id_unit_measurement"),
            fk("fk_invoices_order", "invoices", "order_id", "orders", "id_order"),
            fk("fk_orders_seller", "orders", "seller_id", "mp_sellers", "id"),
            fk("fk_pav_attribute", "product_attribute_values", "attribute_id", "attributes", "id_attribute"),
            fk("fk_pav_option", "product_attribute_values", "option_id", "attribute_options", "id_option"),
            fk("fk_pav_product", "product_attribute_values", "product_id", "products", "id_product"),
            fk("fk_payment_checkout_group_items_payment", "payment_checkout_group_items", "payment_id", "payments", "id_payment"),
            fk("fk_product_orders_offer", "product_orders", "offer_id", "mp_offers", "id"),
            fk("fk_product_orders_sku", "product_orders", "sku_id", "product_skus", "id_sku"),
            fk("fk_products_tax_rate", "products", "tax_rate_id", "tax_rates", "id_tax_rate"),
            fk("fk_register_methods_method", "cash_register_methods", "payment_method_id", "payment_types", "id_payment_type"),
            fk("fk_register_methods_register", "cash_register_methods", "cash_register_id", "cash_registers", "id_cash_register"),
            fk("fk_reservations_line", "stock_reservations", "order_line_id", "product_orders", "id_product_order"),
            fk("fk_reservations_order", "stock_reservations", "order_id", "orders", "id_order"),
            fk("fk_reservations_sku", "stock_reservations", "sku_id", "product_skus", "id_sku"),
            fk("fk_sav_attribute", "sku_attribute_values", "attribute_id", "attributes", "id_attribute"),
            fk("fk_sav_option", "sku_attribute_values", "option_id", "attribute_options", "id_option"),
            fk("fk_sav_sku", "sku_attribute_values", "sku_id", "product_skus", "id_sku"),
            fk("fk_stock_items_sku", "stock_items", "sku_id", "product_skus", "id_sku"),
            fk("fk_stores_warehouse", "stores", "warehouse_id", "warehouses", "id_warehouse")
    );

    /** Antes db/transicion-jpa.sql: facturas sin orden (comisiones al vendedor). */
    static final List<NullableColumn> NULLABLE = List.of(
            new NullableColumn("invoices", "order_id"),
            // La entrega real se registra después de crear el despacho.
            new NullableColumn("dispatch_products", "real_delivery_date"));

    private SchemaConstraints() {
    }

    public static List<ForeignKey> foreignKeys() {
        return java.util.stream.Stream.concat(SOFT_DELETE_TARGETS.stream(), UNMAPPED.stream()).toList();
    }

    public static List<NullableColumn> nullableColumns() {
        return NULLABLE;
    }

    private static ForeignKey fk(String name, String table, String column, String refTable, String refColumn) {
        return new ForeignKey(name, table, column, refTable, refColumn);
    }
}
