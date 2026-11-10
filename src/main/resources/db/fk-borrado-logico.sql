-- Llaves foráneas hacia tablas con borrado lógico (@SoftDelete).
--
-- Hibernate 7 no admite una asociación @ManyToOne LAZY hacia una entidad con @SoftDelete, así que estas llaves
-- no se declaran en las entidades y JPA no las crea. Las bases que vienen de las migraciones ya las tienen.
-- En una base nueva, creada por JPA, ejecuta este script UNA vez después del primer arranque.
-- Es idempotente: solo agrega las llaves que falten. Sirve en MySQL 8 y MariaDB.
-- Si una llave falla por datos huérfanos, corrige esos datos y vuelve a ejecutarlo.

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'cash_movements'
                 AND CONSTRAINT_NAME = 'fk_cash_movements_session' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE cash_movements ADD CONSTRAINT fk_cash_movements_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id_cash_session)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'cash_registers'
                 AND CONSTRAINT_NAME = 'fk_cash_registers_store' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE cash_registers ADD CONSTRAINT fk_cash_registers_store FOREIGN KEY (store_id) REFERENCES stores (id_store)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'cash_session_counts'
                 AND CONSTRAINT_NAME = 'fk_cash_session_counts_session' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE cash_session_counts ADD CONSTRAINT fk_cash_session_counts_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id_cash_session)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'cash_sessions'
                 AND CONSTRAINT_NAME = 'fk_cash_sessions_register' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE cash_sessions ADD CONSTRAINT fk_cash_sessions_register FOREIGN KEY (cash_register_id) REFERENCES cash_registers (id_cash_register)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_purchase_orders'
                 AND CONSTRAINT_NAME = 'fk_inv_purchase_orders_supplier' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE inv_purchase_orders ADD CONSTRAINT fk_inv_purchase_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id_supplier)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'inv_purchase_orders'
                 AND CONSTRAINT_NAME = 'fk_inv_purchase_orders_warehouse' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE inv_purchase_orders ADD CONSTRAINT fk_inv_purchase_orders_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id_warehouse)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'invoice_lines'
                 AND CONSTRAINT_NAME = 'fk_invoice_lines_invoice' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE invoice_lines ADD CONSTRAINT fk_invoice_lines_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id_invoice)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'invoice_sources'
                 AND CONSTRAINT_NAME = 'fk_invoice_sources_invoice' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE invoice_sources ADD CONSTRAINT fk_invoice_sources_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id_invoice)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'invoices'
                 AND CONSTRAINT_NAME = 'fk_invoices_related' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE invoices ADD CONSTRAINT fk_invoices_related FOREIGN KEY (related_invoice_id) REFERENCES invoices (id_invoice)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'invoices'
                 AND CONSTRAINT_NAME = 'fk_invoices_resolution' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE invoices ADD CONSTRAINT fk_invoices_resolution FOREIGN KEY (resolution_id) REFERENCES billing_resolutions (id_billing_resolution)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'
                 AND CONSTRAINT_NAME = 'fk_orders_cash_session' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE orders ADD CONSTRAINT fk_orders_cash_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id_cash_session)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'payment_events'
                 AND CONSTRAINT_NAME = 'fk_payment_events_payment' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE payment_events ADD CONSTRAINT fk_payment_events_payment FOREIGN KEY (payment_id) REFERENCES payments (id_payment)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'payments'
                 AND CONSTRAINT_NAME = 'fk_payments_cash_session' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE payments ADD CONSTRAINT fk_payments_cash_session FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id_cash_session)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'payments'
                 AND CONSTRAINT_NAME = 'fk_payments_order' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE payments ADD CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id_order)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'payments'
                 AND CONSTRAINT_NAME = 'fk_payments_type' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE payments ADD CONSTRAINT fk_payments_type FOREIGN KEY (payment_type_id) REFERENCES payment_types (id_payment_type)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'product_images'
                 AND CONSTRAINT_NAME = 'fk_product_images_product' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE product_images ADD CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id_product)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'product_images'
                 AND CONSTRAINT_NAME = 'fk_product_images_sku' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE product_images ADD CONSTRAINT fk_product_images_sku FOREIGN KEY (sku_id) REFERENCES product_skus (id_sku)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'product_skus'
                 AND CONSTRAINT_NAME = 'fk_product_skus_product' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE product_skus ADD CONSTRAINT fk_product_skus_product FOREIGN KEY (product_id) REFERENCES products (id_product)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'products'
                 AND CONSTRAINT_NAME = 'fk_products_template' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE products ADD CONSTRAINT fk_products_template FOREIGN KEY (template_id) REFERENCES product_templates (id_template)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_reservations'
                 AND CONSTRAINT_NAME = 'fk_reservations_warehouse' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE stock_reservations ADD CONSTRAINT fk_reservations_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id_warehouse)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'stock_items'
                 AND CONSTRAINT_NAME = 'fk_stock_items_warehouse' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE stock_items ADD CONSTRAINT fk_stock_items_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id_warehouse)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_contacts'
                 AND CONSTRAINT_NAME = 'fk_supplier_contacts_supplier' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE supplier_contacts ADD CONSTRAINT fk_supplier_contacts_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id_supplier)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_products'
                 AND CONSTRAINT_NAME = 'fk_supplier_products_supplier' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE supplier_products ADD CONSTRAINT fk_supplier_products_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id_supplier)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'template_attributes'
                 AND CONSTRAINT_NAME = 'fk_template_attributes_attribute' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE template_attributes ADD CONSTRAINT fk_template_attributes_attribute FOREIGN KEY (attribute_id) REFERENCES attributes (id_attribute)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'warehouse_locations'
                 AND CONSTRAINT_NAME = 'fk_warehouse_locations_warehouse' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE warehouse_locations ADD CONSTRAINT fk_warehouse_locations_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id_warehouse)', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

