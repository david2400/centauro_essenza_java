package com.essenza.draco.security;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Qué acción exige cada ruta de los módulos de operación que no validan permisos por sí mismos
 * (órdenes, pagos, caja, devoluciones, facturación). Lectura (GET/HEAD) pide {@code X.VER};
 * cualquier cambio pide {@code X.GESTIONAR}. La acción se resuelve luego con las reglas
 * configurables ({@link AuthorizationRules}).
 * <p>
 * Quedan fuera las notificaciones de las pasarelas (las valida su firma) y la página de pago de
 * prueba, que abre el comprador sin sesión.
 */
public final class RouteRules {

    /** Prefijos sin sesión: los llama la pasarela o el navegador del comprador. */
    static final List<String> PUBLIC = List.of("/sales/payments/webhooks", "/sales/payments/sandbox");

    /** Prefijo → área (el orden importa: primero los más específicos). */
    static final List<String[]> AREAS = List.of(
            new String[] {"/sales/cash_sessions", "CAJA"},
            new String[] {"/sales/cash_registers", "CAJA"},
            new String[] {"/sales/stores", "CAJA"},
            new String[] {"/sales/pos", "CAJA"},
            new String[] {"/sales", "VENTAS"},
            new String[] {"/devolution", "POSTVENTA"},
            new String[] {"/billing", "FACTURACION"},
            new String[] {"/graphql", "VENTAS"});

    private RouteRules() {
    }

    /**
     * @param path ruta sin el context path (p. ej. {@code /sales/orders/5})
     * @return acción exigida; vacío si la ruta no está protegida aquí
     */
    public static Optional<String> capabilityFor(String method, String path) {
        if (path == null) {
            return Optional.empty();
        }
        String clean = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        for (String open : PUBLIC) {
            if (matches(clean, open)) {
                return Optional.empty();
            }
        }
        String verb = method == null ? "GET" : method.toUpperCase(Locale.ROOT);
        if ("OPTIONS".equals(verb)) {
            return Optional.empty();
        }
        for (String[] area : AREAS) {
            if (matches(clean, area[0])) {
                // GraphQL va por POST aunque consulte: se exige gestionar (no se distingue la operación).
                boolean read = ("GET".equals(verb) || "HEAD".equals(verb)) && !"/graphql".equals(area[0]);
                return Optional.of(area[1] + (read ? ".VER" : ".GESTIONAR"));
            }
        }
        return Optional.empty();
    }

    private static boolean matches(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
