package com.essenza.draco.shared.security;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Menús (rutas del panel) que draco exige con {@link MenuAccess}. Es la lista que se registra en access_control al
 * arrancar cuando {@code essenza.access-control.sync.enabled=true}: un módulo nuevo protegido por menú agrega aquí su
 * ruta y la usa desde esta constante.
 */
public final class Menus {

    public static final String ORDERS = "/ventas/orders";
    public static final String DISPATCHES = "/logistica/dispatches";
    public static final String SHIPPING_COSTS = "/logistica/shipping-costs";

    private static final Map<String, String> ALL = new LinkedHashMap<>();

    static {
        ALL.put(ORDERS, "Órdenes de venta");
        ALL.put(DISPATCHES, "Despachos");
        ALL.put(SHIPPING_COSTS, "Costos de envío");
    }

    private Menus() {
    }

    /** Ruta → nombre del menú, en orden. */
    public static Map<String, String> all() {
        return java.util.Collections.unmodifiableMap(ALL);
    }
}
