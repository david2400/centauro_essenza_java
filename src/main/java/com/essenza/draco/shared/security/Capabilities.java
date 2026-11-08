package com.essenza.draco.shared.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Acciones que los módulos protegen (claves lógicas). No son permisos de access_control: cada
 * una se resuelve con la regla configurada en {@code essenza.authorization.rules[CLAVE]}
 * (permiso, rol o cualquier usuario del contexto). Los servicios preguntan por la clave con
 * {@link RequestActor#require(String)}.
 */
public final class Capabilities {

    private static final Map<String, String> ALL = new LinkedHashMap<>();

    static {
        ALL.put("VENDEDORES.VER", "Consultar vendedores, documentos e historial");
        ALL.put("VENDEDORES.GESTIONAR", "Crear y editar vendedores y sus documentos; enviarlos a revisión");
        ALL.put("VENDEDORES.APROBAR", "Revisar documentos, aprobar, suspender, reactivar y cerrar vendedores");
        ALL.put("PLANES.VER", "Consultar planes comerciales y usar el simulador");
        ALL.put("PLANES.GESTIONAR", "Crear, publicar, versionar y retirar planes");
        ALL.put("PLANES.ASIGNAR", "Asignar, programar y cancelar planes de vendedores");
        ALL.put("PLANES.EXCEPCIONES", "Autorizar comisión o mensualidad distinta a la del plan");
        ALL.put("OFERTAS.VER", "Consultar ofertas");
        ALL.put("OFERTAS.GESTIONAR", "Crear y editar ofertas; enviarlas a moderación, pausar, reanudar y cerrar");
        ALL.put("OFERTAS.MODERAR", "Aprobar o rechazar ofertas");
        ALL.put("COMISIONES.VER", "Ver comisiones y estados de cuenta de vendedores");
        ALL.put("COMISIONES.GESTIONAR", "Generar mensualidades, devengar y recalcular comisiones, registrar pagos");
        ALL.put("COMISIONES.AJUSTAR", "Ajustes manuales del estado de cuenta");
        ALL.put("COMPRAS.VER", "Consultar órdenes de compra a proveedores y sus recepciones");
        ALL.put("COMPRAS.GESTIONAR", "Crear, editar, enviar, cancelar y cerrar órdenes de compra");
        ALL.put("COMPRAS.RECIBIR", "Registrar la recepción en bodega de órdenes de compra");
        ALL.put("DISPUTAS.VER", "Consultar reclamos de compradores contra vendedores externos");
        ALL.put("DISPUTAS.GESTIONAR", "Abrir reclamos, registrar la respuesta del vendedor y retirarlos");
        ALL.put("DISPUTAS.RESOLVER", "Decidir reclamos: reembolso, cambio o rechazo, y cargo al vendedor");
        ALL.put("CONSIGNACION.VER", "Consultar la mercancía en consignación de los vendedores y sus movimientos");
        ALL.put("CONSIGNACION.GESTIONAR", "Recibir, devolver al vendedor y ajustar el conteo de la mercancía en consignación");
        ALL.put("VENTAS.VER", "Consultar órdenes, pagos, compras multivendedor y clientes");
        ALL.put("VENTAS.GESTIONAR", "Crear y cambiar órdenes, pagos, compras multivendedor y clientes");
        ALL.put("CAJA.VER", "Consultar tiendas, cajas y turnos");
        ALL.put("CAJA.GESTIONAR", "Abrir y cerrar turnos, movimientos de caja y ventas de mostrador; configurar tiendas y cajas");
        ALL.put("POSTVENTA.VER", "Consultar devoluciones");
        ALL.put("POSTVENTA.GESTIONAR", "Registrar y tramitar devoluciones");
        ALL.put("FACTURACION.VER", "Consultar facturas, resoluciones e impuestos");
        ALL.put("FACTURACION.GESTIONAR", "Emitir y anular facturas; configurar facturación e impuestos");
    }

    private Capabilities() {
    }

    public static Set<String> keys() {
        return ALL.keySet();
    }

    public static Map<String, String> descriptions() {
        return Map.copyOf(ALL);
    }
}
