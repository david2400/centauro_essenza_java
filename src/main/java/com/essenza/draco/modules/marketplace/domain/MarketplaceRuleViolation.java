package com.essenza.draco.modules.marketplace.domain;

/**
 * Regla de negocio del marketplace incumplida (vendedores, planes, asignaciones). Se responde
 * como 400 con el mensaje, que está pensado para mostrarse al usuario.
 */
public class MarketplaceRuleViolation extends IllegalArgumentException {

    public MarketplaceRuleViolation(String message) {
        super(message);
    }

    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new MarketplaceRuleViolation(message);
        }
    }
}
