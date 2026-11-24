package com.essenza.draco.modules.sales.application.dto.storefront;

import java.util.List;

/** Estado de la configuración de la tienda en línea para el panel (sin secretos: solo si están y su ambiente). */
public final class StorefrontSetupDtos {

    private StorefrontSetupDtos() {
    }

    /** {@code status}: OK, WARN o ERROR. {@code code} identifica el punto (el panel lo traduce); {@code detail}: dato extra. */
    public record CheckDto(String code, String status, String detail) {
    }

    /** Medio de pago activo de tipo pasarela y si su pasarela tiene credenciales en el servidor. */
    public record MethodDto(long id, String name, String provider, boolean usable) {
    }

    /** {@code environment}: test, production o sandbox. {@code webhookUrl}: la que se registra en el panel de la pasarela. */
    public record GatewayDto(String provider, boolean configured, String environment, String webhookUrl) {
    }

    public record SetupDto(boolean ready, String storeName, String currency, Long companyId, String returnUrl, long visibleProducts,
                           List<GatewayDto> gateways, List<MethodDto> methods, List<CheckDto> checks) {
    }

    public record ConnectionTestDto(String provider, boolean supported, boolean ok, String message, String merchant) {
    }
}
