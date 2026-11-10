package com.essenza.draco.modules.sales.application.output.storefront;

import java.time.Instant;
import java.util.Optional;

/** Enlaces privados de seguimiento de las compras de la tienda pública (se guarda solo el hash del secreto). */
public interface StorefrontAccessStore {

    void insert(long purchaseId, long companyId, String tokenHash, String email, Instant now);

    record Access(long purchaseId, long companyId) {
    }

    Optional<Access> find(String tokenHash);
}
