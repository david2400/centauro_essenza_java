package com.essenza.draco.shared.security;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Actor fijado por el servidor para un bloque de código, p. ej. la tienda pública: el comprador no tiene
 * token, así que la compra se registra a nombre de la tienda en línea, con la empresa de la configuración y
 * solo los permisos que ese flujo necesita. Nunca se arma desde datos del navegador. El actor vive solo
 * mientras corre {@code work} en el mismo hilo y se restaura el anterior al terminar.
 */
public final class ActorScope {

    private static final ThreadLocal<RequestActor> CURRENT = new ThreadLocal<>();

    private ActorScope() {
    }

    public static <T> T runAs(RequestActor actor, Supplier<T> work) {
        if (actor == null) {
            throw new IllegalArgumentException("Falta el actor.");
        }
        RequestActor previous = CURRENT.get();
        CURRENT.set(actor);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static Optional<RequestActor> current() {
        return Optional.ofNullable(CURRENT.get());
    }
}
