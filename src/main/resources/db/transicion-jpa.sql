-- =============================================================================
-- Paso único para una base que venía con Flyway (V2–V23 aplicadas) y ahora la mantiene JPA.
-- Hibernate (ddl-auto=update) crea lo que falta, pero NO modifica columnas ni restricciones CHECK que ya
-- existen. Estos son los cambios de V24–V33 sobre tablas existentes que debes aplicar tú, una sola vez.
-- No se ejecuta solo. Haz un respaldo antes. MySQL 8.0.16+ (en MariaDB cambia DROP CHECK por DROP CONSTRAINT).
-- En una base nueva NO hace falta: Hibernate crea todo ya correcto.
-- =============================================================================

-- 1. Tipos de asiento del estado de cuenta del vendedor: almacenamiento (V24), cargos por reclamo (V25) y
--    ventas cobradas por la tienda (V28).
ALTER TABLE mp_seller_ledger DROP CHECK ck_mp_seller_ledger_type;
ALTER TABLE mp_seller_ledger ADD CONSTRAINT ck_mp_seller_ledger_type CHECK (entry_type IN ('COMMISSION', 'COMMISSION_REVERSAL',
    'MONTHLY_FEE', 'PAYMENT', 'ADJUSTMENT', 'CONSIGNMENT_SALE', 'CONSIGNMENT_REVERSAL', 'SETTLEMENT', 'STORAGE_FEE',
    'DISPUTE_CHARGE', 'MARKETPLACE_SALE', 'MARKETPLACE_REVERSAL'));

-- 2. Facturas sin orden (comisiones al vendedor, V32).
ALTER TABLE invoices MODIFY order_id BIGINT NULL;

-- 3. Historial de Flyway: ya no se usa (Flyway quedó apagado). Opcional.
-- DROP TABLE flyway_schema_history;
