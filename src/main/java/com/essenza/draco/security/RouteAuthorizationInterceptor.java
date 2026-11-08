package com.essenza.draco.security;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Protege órdenes, pagos, caja, devoluciones y facturación aunque el resto de draco siga en modo
 * observación: exige sesión válida de Essenza (401 sin token), que la empresa del token sea la de
 * la tienda (403) y la acción de la ruta según las reglas configurables (403).
 * <p>
 * {@code essenza.authorization.protect-operations=false} lo apaga (solo para una emergencia).
 * {@code essenza.store.company-id} (variable ESSENZA_TIENDA_COMPANY_ID) es la empresa dueña de las
 * órdenes: estas tablas no tienen empresa propia, así que un token de otra empresa no entra.
 */
@Component
public class RouteAuthorizationInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RouteAuthorizationInterceptor.class);

    private final ActorProvider actors;
    private final boolean enabled;
    private final Long storeCompanyId;

    public RouteAuthorizationInterceptor(ActorProvider actors,
                                         @Value("${essenza.authorization.protect-operations:true}") boolean enabled,
                                         @Value("${essenza.store.company-id:}") String storeCompanyId) {
        this.actors = actors;
        this.enabled = enabled;
        this.storeCompanyId = storeCompanyId == null || storeCompanyId.isBlank() ? null : Long.valueOf(storeCompanyId.trim());
        if (!enabled) {
            log.warn("Órdenes, pagos, caja, devoluciones y facturación SIN protección (essenza.authorization.protect-operations=false).");
        } else if (this.storeCompanyId == null) {
            log.warn("essenza.store.company-id vacío: no se verifica que el token sea de la empresa de la tienda.");
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Optional<String> capability = RouteRules.capabilityFor(request.getMethod(), path);
        if (capability.isEmpty()) {
            return true;
        }
        RequestActor actor = actors.current();
        if (storeCompanyId != null && actor.companyId() != storeCompanyId) {
            throw new ForbiddenException("Tu sesión es de otra empresa: esta tienda no está disponible para ella.");
        }
        actor.require(capability.get());
        return true;
    }
}
