package com.essenza.draco.modules.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.essenza.draco.modules.inventory.application.dto.InventoryMovementDto;
import com.essenza.draco.modules.inventory.application.dto.SkuRef;
import com.essenza.draco.modules.inventory.application.output.repository.InventoryMovementRepository;
import com.essenza.draco.modules.inventory.application.output.repository.LegacyStockProjectionPort;
import com.essenza.draco.modules.inventory.application.output.repository.MainWarehousePort;
import com.essenza.draco.modules.inventory.application.output.repository.SkuCatalogPort;
import com.essenza.draco.modules.inventory.application.output.repository.StockItemRepository;
import com.essenza.draco.modules.inventory.application.services.InventoryMovementServiceImpl;
import com.essenza.draco.modules.inventory.domain.model.StockItem;
import com.essenza.draco.shared.exceptions.ConflictException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryMovementServiceImplTest {

    @Mock InventoryMovementRepository movements;
    @Mock StockItemRepository stockItems;
    @Mock SkuCatalogPort skuCatalog;
    @Mock MainWarehousePort mainWarehouse;
    @Mock LegacyStockProjectionPort projection;

    private InventoryMovementServiceImpl service;
    private final Map<Long, StockItem> byWarehouse = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new InventoryMovementServiceImpl(movements, stockItems, skuCatalog, mainWarehouse, projection);
        lenient().when(skuCatalog.resolve(any(), any())).thenReturn(new SkuRef(7L, 3L));
        lenient().when(stockItems.lockOrEmpty(anyLong(), anyLong(), anyLong())).thenAnswer(inv ->
                byWarehouse.computeIfAbsent(inv.getArgument(2), w -> StockItem.empty(7L, 3L, w)));
        lenient().when(stockItems.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(movements.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(stockItems.totalOnHand(7L)).thenAnswer(inv ->
                byWarehouse.values().stream().mapToInt(StockItem::getOnHand).sum());
    }

    private InventoryMovementDto movement(Long from, Long to, int qty) {
        InventoryMovementDto dto = new InventoryMovementDto();
        dto.setProductId(3L);
        dto.setFromWarehouseId(from);
        dto.setToWarehouseId(to);
        dto.setQuantity(qty);
        dto.setType("X");
        return dto;
    }

    @Test
    void entradaSumaEnLaBodegaYRegistraElSkuYRefrescaLaProyeccion() {
        InventoryMovementDto saved = service.registerEntry(movement(null, 1L, 4));

        assertThat(byWarehouse.get(1L).getOnHand()).isEqualTo(4);
        assertThat(saved.getSkuId()).isEqualTo(7L);
        assertThat(saved.getType()).isEqualTo("ENTRY");
        assertThat(saved.getReferenceType()).isEqualTo("MANUAL");
        verify(projection).refresh(3L);
    }

    @Test
    void salidaSinStockSuficienteEsConflictoYNoRegistraMovimiento() {
        service.registerEntry(movement(null, 1L, 2));

        assertThatThrownBy(() -> service.registerExit(movement(1L, null, 5)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("disponibles 2");
        assertThat(byWarehouse.get(1L).getOnHand()).isEqualTo(2);
    }

    @Test
    void trasladoMueveUnidadesYBloqueaEnOrdenDeIdDeBodega() {
        service.registerEntry(movement(null, 5L, 6));

        service.registerTransfer(movement(5L, 2L, 4));

        assertThat(byWarehouse.get(5L).getOnHand()).isEqualTo(2);
        assertThat(byWarehouse.get(2L).getOnHand()).isEqualTo(4);
        InOrder order = inOrder(stockItems);
        order.verify(stockItems).lockOrEmpty(7L, 3L, 5L); // entrada previa
        order.verify(stockItems).lockOrEmpty(7L, 3L, 2L); // destino (menor id) primero
        order.verify(stockItems).lockOrEmpty(7L, 3L, 5L);
    }

    @Test
    void trasladoALaMismaBodegaNoSePermite() {
        assertThatThrownBy(() -> service.registerTransfer(movement(1L, 1L, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alinearSubeElStockEnLaBodegaPrincipal() {
        when(mainWarehouse.mainWarehouseId()).thenReturn(9L);

        InventoryMovementDto adjustment = service.align(3L, 7L, 10, "PRODUCT_EDIT");

        assertThat(byWarehouse.get(9L).getOnHand()).isEqualTo(10);
        assertThat(adjustment.getType()).isEqualTo("ENTRY");
        assertThat(adjustment.getReferenceType()).isEqualTo("PRODUCT_EDIT");
    }

    @Test
    void alinearSinCambiosNoGeneraMovimiento() {
        service.registerEntry(movement(null, 1L, 3));

        assertThat(service.align(3L, 7L, 3, "PRODUCT_EDIT")).isNull();
        verify(mainWarehouse, never()).mainWarehouseId();
    }

    @Test
    void alinearHaciaAbajoSoloDescuentaDeLaPrincipalYSiNoAlcanzaEsConflicto() {
        when(mainWarehouse.mainWarehouseId()).thenReturn(9L);
        service.registerEntry(movement(null, 9L, 2));
        service.registerEntry(movement(null, 4L, 5));   // otra bodega

        InventoryMovementDto adjustment = service.align(3L, 7L, 6, "PRODUCT_EDIT");
        assertThat(adjustment.getType()).isEqualTo("EXIT");
        assertThat(byWarehouse.get(9L).getOnHand()).isEqualTo(1);

        assertThatThrownBy(() -> service.align(3L, 7L, 2, "PRODUCT_EDIT"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("bodega principal");
    }
}
