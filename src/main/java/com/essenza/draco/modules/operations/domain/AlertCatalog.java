package com.essenza.draco.modules.operations.domain;

import java.util.List;

/**
 * Alertas del centro de operaciones: qué se revisa, qué tan urgente es, qué permiso hace falta
 * para verla y en qué orden se muestra. Los permisos son los configurables de
 * {@code essenza.authorization.rules}; quien no ve un módulo no ve sus alertas.
 */
public final class AlertCatalog {

    private AlertCatalog() {
    }

    public enum Severity {
        CRITICAL, WARNING, INFO
    }

    public enum Area {
        SALES, MARKETPLACE, CONSIGNMENT, BILLING, AFTER_SALES, INVENTORY
    }

    /** @param permissions basta con uno */
    public record Definition(String key, Area area, Severity severity, List<String> permissions) {
    }

    public static final List<Definition> ALL = List.of(
            new Definition("DISPATCH_OVERDUE", Area.SALES, Severity.CRITICAL, List.of("VENTAS.VER", "VENTAS.GESTIONAR")),
            new Definition("SELLERS_OVERDUE", Area.MARKETPLACE, Severity.CRITICAL, List.of("COMISIONES.VER", "COMISIONES.GESTIONAR")),
            new Definition("DISPUTES_OVERDUE", Area.MARKETPLACE, Severity.CRITICAL,
                    List.of("DISPUTAS.VER", "DISPUTAS.GESTIONAR", "DISPUTAS.RESOLVER")),
            new Definition("INVOICES_REJECTED", Area.BILLING, Severity.CRITICAL, List.of("FACTURACION.VER", "FACTURACION.GESTIONAR")),
            new Definition("DISPATCH_DUE_TODAY", Area.SALES, Severity.WARNING, List.of("VENTAS.VER", "VENTAS.GESTIONAR")),
            new Definition("COMMISSIONS_IN_REVIEW", Area.MARKETPLACE, Severity.WARNING, List.of("COMISIONES.VER", "COMISIONES.GESTIONAR")),
            new Definition("DISPUTES_TO_DECIDE", Area.MARKETPLACE, Severity.WARNING,
                    List.of("DISPUTAS.VER", "DISPUTAS.RESOLVER")),
            new Definition("SELLERS_PAYABLE", Area.MARKETPLACE, Severity.WARNING, List.of("COMISIONES.VER", "COMISIONES.GESTIONAR")),
            new Definition("CONSIGNMENT_WITHOUT_OFFER", Area.CONSIGNMENT, Severity.WARNING,
                    List.of("CONSIGNACION.VER", "CONSIGNACION.GESTIONAR")),
            new Definition("INVOICES_PENDING", Area.BILLING, Severity.WARNING, List.of("FACTURACION.VER", "FACTURACION.GESTIONAR")),
            new Definition("RETURNS_STALLED", Area.AFTER_SALES, Severity.WARNING, List.of("POSTVENTA.VER", "POSTVENTA.GESTIONAR")),
            new Definition("PURCHASES_OVERDUE", Area.INVENTORY, Severity.WARNING,
                    List.of("COMPRAS.VER", "COMPRAS.GESTIONAR", "COMPRAS.RECIBIR")),
            new Definition("LOW_STOCK", Area.INVENTORY, Severity.WARNING, List.of("VENTAS.VER", "VENTAS.GESTIONAR")),
            new Definition("DISPATCH_WITHOUT_DATE", Area.SALES, Severity.INFO, List.of("VENTAS.VER", "VENTAS.GESTIONAR")),
            new Definition("OFFERS_IN_REVIEW", Area.MARKETPLACE, Severity.INFO, List.of("OFERTAS.VER", "OFERTAS.MODERAR")),
            new Definition("SELLERS_IN_REVIEW", Area.MARKETPLACE, Severity.INFO, List.of("VENDEDORES.VER", "VENDEDORES.APROBAR")),
            new Definition("CONSIGNMENT_AGED", Area.CONSIGNMENT, Severity.INFO, List.of("CONSIGNACION.VER", "CONSIGNACION.GESTIONAR")));

    /** Umbrales configurables (días). */
    public record Thresholds(int consignmentAgedDays, int returnsStalledDays, int invoicesPendingDays) {
        public Thresholds {
            if (consignmentAgedDays < 1 || returnsStalledDays < 1 || invoicesPendingDays < 0) {
                throw new IllegalArgumentException("Umbrales de alertas no válidos.");
            }
        }
    }
}
