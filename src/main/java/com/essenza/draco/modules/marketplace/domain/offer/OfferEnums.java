package com.essenza.draco.modules.marketplace.domain.offer;

/** Enumeraciones de ofertas (publicaciones de un vendedor sobre un SKU del catálogo). */
public final class OfferEnums {

    private OfferEnums() {
    }

    /** Ver {@link OfferWorkflow} para las transiciones. */
    public enum Status {
        DRAFT,
        PENDING_REVIEW,
        /** Publicada (visible solo si además el vendedor está aprobado y el SKU es vendible). */
        ACTIVE,
        REJECTED,
        PAUSED,
        CLOSED
    }

    public enum Action {
        SUBMIT,
        APPROVE,
        REJECT,
        PAUSE,
        RESUME,
        CLOSE
    }

    public enum Condition {
        NEW,
        USED,
        REFURBISHED
    }

    /**
     * Quién despacha. SELLER: el vendedor desde su propia ubicación (cantidad declarada).
     * PLATFORM: la tienda, con mercancía del vendedor en consignación en una bodega propia; el
     * disponible sale del saldo de consignación (V23).
     */
    public enum Fulfillment {
        SELLER,
        PLATFORM
    }
}
