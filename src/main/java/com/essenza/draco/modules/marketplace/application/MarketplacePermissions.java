package com.essenza.draco.modules.marketplace.application;

/**
 * Permisos del marketplace (se crean en access_control para la aplicación ESSENZA y viajan en
 * el claim {@code permissions} del token). Ver database/seed/permisos_marketplace.sql en
 * access_control.
 */
public final class MarketplacePermissions {

    /** Consultar vendedores, su ficha, historial y documentos. */
    public static final String SELLERS_VIEW = "VENDEDORES.VER";
    /** Crear y editar vendedores, contactos y documentos; enviar a revisión. */
    public static final String SELLERS_MANAGE = "VENDEDORES.GESTIONAR";
    /** Revisar documentos, aprobar, pedir correcciones, suspender, reactivar y cerrar. */
    public static final String SELLERS_APPROVE = "VENDEDORES.APROBAR";
    /** Consultar planes y usar el simulador. */
    public static final String PLANS_VIEW = "PLANES.VER";
    /** Crear, editar, publicar, versionar y retirar planes. */
    public static final String PLANS_MANAGE = "PLANES.GESTIONAR";
    /** Asignar, programar y cancelar planes de vendedores. */
    public static final String PLANS_ASSIGN = "PLANES.ASIGNAR";
    /** Autorizar excepciones comerciales (comisión o mensualidad distinta a la del plan). */
    public static final String PLANS_EXCEPTIONS = "PLANES.EXCEPCIONES";

    /** Consultar ofertas y su historial. */
    public static final String OFFERS_VIEW = "OFERTAS.VER";
    /** Crear y editar ofertas de vendedores; enviarlas a moderación, pausar, reanudar y cerrar. */
    public static final String OFFERS_MANAGE = "OFERTAS.GESTIONAR";
    /** Aprobar o rechazar ofertas en moderación. */
    public static final String OFFERS_MODERATE = "OFERTAS.MODERAR";
    /** Consultar reclamos de compradores. */
    public static final String DISPUTES_VIEW = "DISPUTAS.VER";
    /** Abrir reclamos, registrar la respuesta del vendedor y retirarlos. */
    public static final String DISPUTES_MANAGE = "DISPUTAS.GESTIONAR";
    /** Decidir reclamos y cargar al vendedor. */
    public static final String DISPUTES_RESOLVE = "DISPUTAS.RESOLVER";
    /** Consultar la mercancía en consignación (stock del vendedor en bodegas propias) y su kardex. */
    public static final String CONSIGNMENT_VIEW = "CONSIGNACION.VER";
    /** Recibir mercancía del vendedor, devolvérsela y ajustar el conteo. */
    public static final String CONSIGNMENT_MANAGE = "CONSIGNACION.GESTIONAR";
    /** Ver comisiones, estado de cuenta y saldos de los vendedores. */
    public static final String BILLING_VIEW = "COMISIONES.VER";
    /** Generar mensualidades, devengar comisiones y registrar pagos de vendedores. */
    public static final String BILLING_MANAGE = "COMISIONES.GESTIONAR";
    /** Ajustes manuales del estado de cuenta (con motivo). */
    public static final String BILLING_ADJUST = "COMISIONES.AJUSTAR";

    private MarketplacePermissions() {
    }
}
