package com.essenza.draco.modules.sales.application.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontSetupDtos.CheckDto;
import com.essenza.draco.modules.sales.application.dto.storefront.StorefrontSetupDtos.SetupDto;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateway;
import com.essenza.draco.modules.sales.application.output.gateway.PaymentGateways;
import com.essenza.draco.modules.sales.application.output.storefront.ClienteHookSender;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore.PaymentOption;
import com.essenza.draco.modules.sales.application.output.storefront.StorefrontReadStore.ProductPage;
import com.essenza.draco.modules.sales.application.services.storefront.StorefrontSetupService;
import com.essenza.draco.modules.sales.domain.model.payment.GatewayProvider;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/** Revisión de la configuración de la tienda para el dueño, con puertos simulados. */
class StorefrontSetupServiceTest {

    static final String RETURN = "https://tienda.example.co/es/pedido/{token}";
    RequestActor actor;
    ActorProvider actors;
    StorefrontReadStore read;
    PaymentGateways gateways;
    ClienteHookSender hooks;
    PaymentGateway wompi;

    @BeforeEach
    void setUp() {
        actor = new RequestActor(7, "u-1", "dueño", Set.of("VENTAS.VER", "VENTAS.GESTIONAR"));
        actors = () -> actor;
        read = mock(StorefrontReadStore.class);
        gateways = mock(PaymentGateways.class);
        hooks = mock(ClienteHookSender.class);
        wompi = mock(PaymentGateway.class);
        when(wompi.provider()).thenReturn(GatewayProvider.WOMPI);
        when(wompi.configured()).thenReturn(true);
        when(wompi.environment()).thenReturn("sandbox");
        when(gateways.find(GatewayProvider.WOMPI)).thenReturn(Optional.of(wompi));
        when(gateways.find(GatewayProvider.SANDBOX)).thenReturn(Optional.empty());
        when(gateways.find(GatewayProvider.MERCADO_PAGO)).thenReturn(Optional.empty());
        when(gateways.all()).thenReturn(List.of(wompi));
        when(gateways.webhookUrl(GatewayProvider.WOMPI)).thenReturn("https://abc.ngrok.app/api/shop/sales/payments/webhooks/wompi");
        when(read.gateways()).thenReturn(List.of(new PaymentOption(5, "Wompi", "WOMPI")));
        when(read.search(anyLong(), anyString(), any(StorefrontReadStore.SearchFilter.class), anyString(), anyInt(), anyInt()))
                .thenReturn(new ProductPage(List.of(1L), 12));
        when(hooks.enabled()).thenReturn(true);
    }

    StorefrontSetupService service(boolean enabled, String company, String returnUrl) {
        return new StorefrontSetupService(actors, read, gateways, hooks, enabled, company, "Essenza", "COP", returnUrl);
    }

    static Optional<CheckDto> check(SetupDto dto, String code) {
        return dto.checks().stream().filter(c -> c.code().equals(code)).findFirst();
    }

    @Test
    void conTodoConfiguradoLaTiendaEstaLista() {
        SetupDto dto = service(true, "7", RETURN).setup();
        assertThat(dto.ready()).isTrue();
        assertThat(dto.visibleProducts()).isEqualTo(12);
        assertThat(check(dto, "STORE_ENABLED")).get().extracting(CheckDto::status).isEqualTo("OK");
        assertThat(check(dto, "PAYMENT_METHOD")).get().extracting(CheckDto::detail).isEqualTo("Wompi");
        assertThat(check(dto, "WOMPI_TEST")).get().extracting(CheckDto::status).isEqualTo("WARN");
        assertThat(check(dto, "WEBHOOK_PUBLIC")).isPresent();
        assertThat(dto.gateways()).singleElement().satisfies(g -> {
            assertThat(g.provider()).isEqualTo("WOMPI");
            assertThat(g.webhookUrl()).endsWith("/webhooks/wompi");
        });
    }

    @Test
    void tiendaApagadaDiceQueVariablesFaltanYNoConsultaElCatalogo() {
        SetupDto dto = service(false, "", null).setup();
        assertThat(dto.ready()).isFalse();
        assertThat(check(dto, "STORE_ENABLED")).get().satisfies(c -> {
            assertThat(c.status()).isEqualTo("ERROR");
            assertThat(c.detail()).contains("STOREFRONT_ENABLED", "STOREFRONT_COMPANY_ID", "STOREFRONT_RETURN_URL");
        });
        verify(read, never()).search(anyLong(), anyString(), any(StorefrontReadStore.SearchFilter.class), anyString(), anyInt(), anyInt());
    }

    @Test
    void medioDePagoSinLlavesNoCuentaComoUsable() {
        when(wompi.configured()).thenReturn(false);
        SetupDto dto = service(true, "7", RETURN).setup();
        assertThat(dto.ready()).isFalse();
        assertThat(check(dto, "NO_PAYMENT_METHOD")).get().extracting(CheckDto::status).isEqualTo("ERROR");
        assertThat(check(dto, "METHOD_WITHOUT_KEYS")).get().extracting(CheckDto::detail).isEqualTo("Wompi (WOMPI)");
        assertThat(dto.methods()).singleElement().extracting(m -> m.usable()).isEqualTo(false);
        assertThat(check(dto, "WOMPI_TEST")).isEmpty();
    }

    @Test
    void webhookLocalYUrlSinTokenSonAdvertencias() {
        when(gateways.webhookUrl(GatewayProvider.WOMPI)).thenReturn("http://localhost:8082/api/shop/sales/payments/webhooks/wompi");
        SetupDto dto = service(true, "7", "https://tienda.example.co/gracias").setup();
        assertThat(dto.ready()).isTrue();
        assertThat(check(dto, "WEBHOOK_LOCAL")).get().extracting(CheckDto::status).isEqualTo("WARN");
        assertThat(check(dto, "RETURN_URL_TOKEN")).get().extracting(CheckDto::status).isEqualTo("WARN");
    }

    @Test
    void otraEmpresaAvisaYNoMuestraSuCatalogo() {
        SetupDto dto = service(true, "99", RETURN).setup();
        assertThat(check(dto, "OTHER_COMPANY")).get().extracting(CheckDto::detail).isEqualTo("99");
        assertThat(dto.visibleProducts()).isZero();
        verify(read, never()).search(anyLong(), anyString(), any(StorefrontReadStore.SearchFilter.class), anyString(), anyInt(), anyInt());
    }

    @Test
    void catalogoVacioYAvisosApagadosSonAdvertencias() {
        when(read.search(anyLong(), anyString(), any(StorefrontReadStore.SearchFilter.class), anyString(), anyInt(), anyInt()))
                .thenReturn(new ProductPage(List.of(), 0));
        when(hooks.enabled()).thenReturn(false);
        SetupDto dto = service(true, "7", RETURN).setup();
        assertThat(check(dto, "CATALOG_EMPTY")).isPresent();
        assertThat(check(dto, "CUSTOMER_NOTICES_OFF")).isPresent();
    }

    @Test
    void verLaConfiguracionPideVentasYProbarPideGestionar() {
        actor = new RequestActor(7, "u-2", "cajero", Set.of("CAJA.GESTIONAR"));
        assertThatThrownBy(() -> service(true, "7", RETURN).setup()).isInstanceOf(ForbiddenException.class);
        actor = new RequestActor(7, "u-3", "consulta", Set.of("VENTAS.VER"));
        assertThat(service(true, "7", RETURN).setup()).isNotNull();
        assertThatThrownBy(() -> service(true, "7", RETURN).testGateway("WOMPI")).isInstanceOf(ForbiddenException.class);
        verify(wompi, never()).testConnection();
    }

    @Test
    void laPruebaDeConexionDevuelveElResultadoDeLaPasarela() {
        when(wompi.testConnection()).thenReturn(new PaymentGateway.ConnectionTest(true, true, "Llave pública válida.", "Tienda X"));
        var result = service(true, "7", RETURN).testGateway("wompi");
        assertThat(result.provider()).isEqualTo("WOMPI");
        assertThat(result.ok()).isTrue();
        assertThat(result.merchant()).isEqualTo("Tienda X");
        var missing = service(true, "7", RETURN).testGateway("MERCADO_PAGO");
        assertThat(missing.supported()).isFalse();
        assertThat(missing.ok()).isFalse();
    }

    @Test
    void detectaDireccionesLocales() {
        assertThat(StorefrontSetupService.isLocal("http://127.0.0.1:8082/x")).isTrue();
        assertThat(StorefrontSetupService.isLocal("http://192.168.1.4/x")).isTrue();
        assertThat(StorefrontSetupService.isLocal("https://api.tienda.co/x")).isFalse();
    }
}
