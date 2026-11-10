package com.essenza.draco.modules.shipping_logistics.tariff.domain;

/** Dato del tarifario o de la cotización que no cumple una regla (400 con el mensaje). */
public class TariffRuleViolation extends IllegalArgumentException {

    public TariffRuleViolation(String message) {
        super(message);
    }
}
