package com.essenza.draco.modules.sales.application.output.storefront;

/** Envío firmado de un aviso a centauro_cliente. */
public interface ClienteHookSender {

    /** false si no está configurado (sin URL o secreto): no se encola ni se envía nada. */
    boolean enabled();

    /** Lanza una excepción si centauro_cliente no confirmó la recepción (2xx). */
    void send(ClienteHookOutbox.Hook hook);
}
