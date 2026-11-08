package com.essenza.draco.modules.sales.application.services.customer;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.essenza.draco.modules.sales.application.output.repository.CustomerRegistry;
import com.essenza.draco.modules.sales.domain.model.order.CustomerInfo;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Mantiene el registro de clientes al día con las órdenes: al registrar o editar una orden con
 * documento y nombre, crea el cliente (origen ORDERS) o completa los datos de contacto que le
 * falten. Nunca sobrescribe nombre, datos ya diligenciados ni el consentimiento de comunicaciones.
 *
 * <p>Es de mejor esfuerzo: un fallo aquí no detiene la venta (queda en el log sin el número de
 * documento) y el cliente se puede crear después con "Importar desde órdenes".
 */
@Component
public class CustomerSync {

    private static final Logger log = LoggerFactory.getLogger(CustomerSync.class);

    private final CustomerRegistry customers;
    private final ActorProvider actors;
    private final Long storeCompanyId;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CustomerSync(CustomerRegistry customers, ActorProvider actors,
                        @Value("${essenza.store.company-id:}") String storeCompanyId) {
        this(customers, actors, parse(storeCompanyId), Clock.systemUTC());
    }

    public CustomerSync(CustomerRegistry customers, ActorProvider actors, Long storeCompanyId, Clock clock) {
        this.customers = customers;
        this.actors = actors;
        this.storeCompanyId = storeCompanyId;
        this.clock = clock;
    }

    /** @return true si el cliente quedó registrado; false si la orden no trae documento y nombre o falló */
    public boolean fromOrder(CustomerInfo info) {
        if (info == null || blank(info.docNumber()) || blank(info.docType()) || blank(info.name())) {
            return false;
        }
        Long company = null;
        String actorId = "orders";
        try {
            RequestActor actor = actors.current();
            company = actor.companyId();
            actorId = actor.userId();
        } catch (RuntimeException noActor) {
            company = storeCompanyId; // órdenes de la tienda sin sesión del panel
        }
        if (company == null) {
            return false;
        }
        try {
            return customers.upsertFromOrder(company, info, actorId, clock.instant());
        } catch (RuntimeException ex) {
            log.warn("No se pudo sincronizar el cliente ({}) desde la orden: {}", info.docType(), ex.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Long parse(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
