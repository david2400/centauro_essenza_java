package com.essenza.draco.modules.sales.application.services.storefront;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontSetupDtos.CheckDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontSetupDtos.ConnectionTestDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontSetupDtos.GatewayDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontSetupDtos.MethodDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontSetupDtos.SetupDto;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateway;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateways;
import com.essenza.draco.modules.sales.application.output.storefront.ClienteHookSender;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore;
import com.essenza.draco.modules.sales.domain.model.payment.GatewayProvider;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Revisión de la configuración de la tienda en línea para el dueño (panel): qué falta para vender y cobrar. Lee la
 * configuración del servidor y los medios de pago de la empresa; nunca devuelve llaves ni secretos. La prueba de
 * conexión con la pasarela es de solo lectura (no crea cobros).
 */
@Service
public class StorefrontSetupService {

    static final String SALES_VIEW = "VENTAS.VER";
    static final String SALES_MANAGE = "VENTAS.GESTIONAR";

    private final ActorProvider actors;
    private final StorefrontReadStore read;
    private final PaymentGateways gateways;
    private final ClienteHookSender hooks;
    private final boolean requestedEnabled;
    private final Long companyId;
    private final String storeName;
    private final String currency;
    private final String returnUrl;

    @Autowired
    public StorefrontSetupService(ActorProvider actors, StorefrontReadStore read, PaymentGateways gateways, ClienteHookSender hooks,
                                  @Value("${essenza.storefront.enabled:false}") boolean enabled,
                                  @Value("${essenza.storefront.company-id:}") String companyId,
                                  @Value("${essenza.storefront.store-name:Tienda en línea}") String storeName,
                                  @Value("${essenza.marketplace.catalog-currency:COP}") String currency,
                                  @Value("${essenza.storefront.return-url:}") String returnUrl) {
        this.actors = actors;
        this.read = read;
        this.gateways = gateways;
        this.hooks = hooks;
        this.requestedEnabled = enabled;
        this.companyId = parseCompany(companyId);
        this.storeName = storeName == null || storeName.isBlank() ? "Tienda en línea" : storeName.strip();
        this.currency = currency == null ? "COP" : currency.strip().toUpperCase(Locale.ROOT);
        this.returnUrl = returnUrl == null || returnUrl.isBlank() ? null : returnUrl.strip();
    }

    public SetupDto setup() {
        RequestActor actor = actors.current().requireAny(SALES_VIEW, SALES_MANAGE);
        List<CheckDto> checks = new ArrayList<>();

        // 1. Tienda encendida, con empresa y URL de regreso (sin ellas draco responde 404 a la tienda).
        List<String> missing = new ArrayList<>();
        if (!requestedEnabled) {
            missing.add("STOREFRONT_ENABLED");
        }
        if (companyId == null) {
            missing.add("STOREFRONT_COMPANY_ID");
        }
        if (returnUrl == null) {
            missing.add("STOREFRONT_RETURN_URL");
        }
        boolean enabled = missing.isEmpty();
        checks.add(enabled ? ok("STORE_ENABLED", null) : error("STORE_ENABLED", String.join(", ", missing)));
        if (returnUrl != null && !returnUrl.contains("{token}")) {
            checks.add(warn("RETURN_URL_TOKEN", returnUrl));
        }

        // 2. La empresa del panel es la de la tienda (si no, lo que se configure aquí no se ve en la tienda).
        boolean sameCompany = companyId != null && companyId == actor.companyId();
        if (companyId != null && !sameCompany) {
            checks.add(warn("OTHER_COMPANY", String.valueOf(companyId)));
        }

        // 3. Pasarelas y medios de pago.
        List<GatewayDto> gatewayRows = gateways.all().stream()
                .map(g -> new GatewayDto(g.provider().name(), g.configured(), g.environment(), gateways.webhookUrl(g.provider())))
                .toList();
        List<MethodDto> methods = read.gateways().stream()
                .map(m -> new MethodDto(m.id(), m.name(), m.provider(), usable(m.provider())))
                .toList();
        List<MethodDto> usableMethods = methods.stream().filter(MethodDto::usable).toList();
        if (usableMethods.isEmpty()) {
            checks.add(error("NO_PAYMENT_METHOD", null));
        } else {
            checks.add(ok("PAYMENT_METHOD", String.join(", ", usableMethods.stream().map(MethodDto::name).toList())));
        }
        methods.stream().filter(m -> !m.usable())
                .forEach(m -> checks.add(warn("METHOD_WITHOUT_KEYS", m.name() + " (" + m.provider() + ")")));
        if (usableMethods.stream().anyMatch(m -> "SANDBOX".equalsIgnoreCase(m.provider()))) {
            checks.add(warn("SANDBOX_ACTIVE", null));
        }
        gateway(GatewayProvider.WOMPI).filter(PaymentGateway::configured).ifPresent(wompi -> {
            checks.add("production".equals(wompi.environment()) ? ok("WOMPI_PRODUCTION", null) : warn("WOMPI_TEST", null));
            String webhook = gateways.webhookUrl(GatewayProvider.WOMPI);
            checks.add(isLocal(webhook) ? warn("WEBHOOK_LOCAL", webhook) : ok("WEBHOOK_PUBLIC", webhook));
        });

        // 4. Catálogo visible.
        long visible = enabled && sameCompany ? read.search(companyId, currency, new StorefrontReadStore.SearchFilter(null, null, null,
                null, null), "NEWEST", 0, 1).total() : 0;
        if (enabled && sameCompany) {
            checks.add(visible > 0 ? ok("CATALOG", String.valueOf(visible)) : warn("CATALOG_EMPTY", null));
        }

        // 5. Avisos al comprador (centauro_cliente).
        checks.add(hooks.enabled() ? ok("CUSTOMER_NOTICES", null) : warn("CUSTOMER_NOTICES_OFF", null));

        boolean ready = checks.stream().noneMatch(c -> "ERROR".equals(c.status()));
        return new SetupDto(ready, storeName, currency, companyId, returnUrl, visible, gatewayRows, methods, checks);
    }

    /** Prueba de conexión de solo lectura con la pasarela (VENTAS.GESTIONAR: consulta un servicio externo). */
    public ConnectionTestDto testGateway(String provider) {
        actors.current().require(SALES_MANAGE);
        GatewayProvider code = GatewayProvider.parse(provider);
        PaymentGateway.ConnectionTest test = gateway(code).map(PaymentGateway::testConnection)
                .orElse(new PaymentGateway.ConnectionTest(false, false, "La pasarela no está disponible en este servidor.", null));
        return new ConnectionTestDto(code.name(), test.supported(), test.ok(), test.message(), test.merchant());
    }

    private boolean usable(String provider) {
        return GatewayProvider.tryParse(provider).flatMap(this::gateway).map(PaymentGateway::configured).orElse(false);
    }

    private Optional<PaymentGateway> gateway(GatewayProvider provider) {
        return gateways.find(provider);
    }

    public static boolean isLocal(String url) {
        if (url == null) {
            return true;
        }
        String host = url.replaceFirst("^https?://", "").split("[/:]")[0].toLowerCase(Locale.ROOT);
        return host.equals("localhost") || host.startsWith("127.") || host.equals("0.0.0.0") || host.endsWith(".local")
                || host.startsWith("192.168.") || host.startsWith("10.");
    }

    private static CheckDto ok(String code, String detail) {
        return new CheckDto(code, "OK", detail);
    }

    private static CheckDto warn(String code, String detail) {
        return new CheckDto(code, "WARN", detail);
    }

    private static CheckDto error(String code, String detail) {
        return new CheckDto(code, "ERROR", detail);
    }

    private static Long parseCompany(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.strip());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
