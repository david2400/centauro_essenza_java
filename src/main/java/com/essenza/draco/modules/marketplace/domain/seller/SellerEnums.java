package com.essenza.draco.modules.marketplace.domain.seller;

/** Enumeraciones de vendedores. */
public final class SellerEnums {

    private SellerEnums() {
    }

    /** Vendedor propio (la empresa que opera la tienda) o externo (tercero que publica y vende). */
    public enum Kind {
        OWN,
        EXTERNAL
    }

    /** Estados del vendedor. Ver {@link SellerWorkflow} para las transiciones. */
    public enum Status {
        DRAFT,
        IN_REVIEW,
        CHANGES_REQUESTED,
        APPROVED,
        SUSPENDED,
        CLOSED;

        /** Ficha editable por quien gestiona vendedores (no en revisión ni cerrada). */
        public boolean profileEditable() {
            return this == DRAFT || this == CHANGES_REQUESTED || this == APPROVED || this == SUSPENDED;
        }
    }

    /** Acción sobre el estado. Cada una exige su permiso (ver servicio). */
    public enum Action {
        SUBMIT,
        REQUEST_CHANGES,
        APPROVE,
        SUSPEND,
        REACTIVATE,
        CLOSE
    }

    /** Tipo de documento de identificación fiscal del vendedor. */
    public enum DocType {
        NIT,
        CC,
        CE,
        PP
    }

    /** Documentos de soporte que se revisan. */
    public enum DocumentKind {
        RUT,
        CAMARA_COMERCIO,
        CEDULA_REPRESENTANTE,
        CERTIFICACION_BANCARIA,
        OTRO
    }

    public enum DocumentReview {
        PENDING,
        ACCEPTED,
        REJECTED
    }
}
