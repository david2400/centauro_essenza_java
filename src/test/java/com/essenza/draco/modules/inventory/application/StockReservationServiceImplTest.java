package com.essenza.draco.modules.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.essenza.draco.modules.inventory.application.dto.InventoryMovementDto;
import com.essenza.draco.modules.inventory.application.output.repository.InventoryMovementRepository;
import com.essenza.draco.modules.inventory.application.output.repository.LegacyStockProjectionPort;
import com.essenza.draco.modules.inventory.application.output.repository.MainWarehousePort;
import com.essenza.draco.modules.inventory.application.output.repository.StockItemRepository;
import com.essenza.draco.modules.inventory.application.output.repository.StockReservationRepository;
import com.essenza.draco.modules.inventory.application.services.StockReservationServiceImpl;
import com.essenza.draco.modules.inventory.domain.model.StockItem;
import com.essenza.draco.modules.inventory.domain.model.StockReservation;
import com.essenza.draco.shared.exceptions.ConflictException;

/** Reservas con un almacén en memoria: bodega 1 = principal, bodega 2 = secundaria. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockReservationServiceImplTest {

    @Mock StockReservationRepository reservationRepo;
    @Mock StockItemRepository stockItems;
    @Mock MainWarehousePort mainWarehouse;
    @Mock InventoryMovementRepository movements;
    @Mock LegacyStockProjectionPort projection;

    StockReservationServiceImpl service;
    final Map<Long, StockItem> stock = new HashMap<>();          // por bodega (SKU 10, producto 5)
    final List<StockReservation> rows = new ArrayList<>();
    final AtomicLong ids = new AtomicLong();
    static final Instant EXPIRES = Instant.parse("2026-10-07T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new StockReservationServiceImpl(reservationRepo, stockItems, mainWarehouse, movements, projection);
        when(mainWarehouse.mainWarehouseId()).thenReturn(1L);
        stock.put(1L, new StockItem(101L, 10L, 5L, 1L, 3, 0, 0));
        stock.put(2L, new StockItem(102L, 10L, 5L, 2L, 10, 0, 0));
        when(stockItems.lockAllForSku(10L)).thenAnswer(inv -> new ArrayList<>(stock.values()));
        when(stockItems.lockOrEmpty(any(), any(), any())).thenAnswer(inv -> stock.get(inv.<Long>getArgument(2)));
        when(stockItems.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepo.save(any())).thenAnswer(inv -> {
            StockReservation r = inv.getArgument(0);
            if (r.getId() == null) {
                StockReservation saved = new StockReservation(ids.incrementAndGet(), r.getOrderId(), r.getLineId(), r.getSkuId(),
                        r.getProductId(), r.getWarehouseId(), r.getQuantity(), r.getStatus(), r.getExpiresAt());
                rows.add(saved);
                return saved;
            }
            return r;
        });
        when(reservationRepo.activeByLine(anyLong())).thenAnswer(inv -> rows.stream()
                .filter(r -> r.getLineId().equals(inv.getArgument(0)) && r.isActive()).toList());
        when(reservationRepo.byOrder(anyLong())).thenAnswer(inv -> rows.stream()
                .filter(r -> r.getOrderId().equals(inv.getArgument(0))).toList());
    }

    @Test
    void reservaPrimeroEnLaPrincipalYLuegoEnLasDemas() {
        service.reserveLine(7L, 70L, 5L, 10L, 5, EXPIRES);

        assertThat(stock.get(1L).getReserved()).isEqualTo(3);
        assertThat(stock.get(2L).getReserved()).isEqualTo(2);
        assertThat(rows).extracting(StockReservation::getWarehouseId, StockReservation::getQuantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, 3), org.assertj.core.groups.Tuple.tuple(2L, 2));
    }

    @Test
    void sinStockSuficienteEs409YNoReservaNada() {
        assertThatThrownBy(() -> service.reserveLine(7L, 70L, 5L, 10L, 14, EXPIRES))
                .isInstanceOf(ConflictException.class).hasMessageContaining("disponibles 13");
        assertThat(stock.get(1L).getReserved()).isZero();
        assertThat(rows).isEmpty();
    }

    @Test
    void bajarLaCantidadLiberaPrimeroLaBodegaSecundaria() {
        service.reserveLine(7L, 70L, 5L, 10L, 5, EXPIRES);
        service.reserveLine(7L, 70L, 5L, 10L, 2, EXPIRES);

        assertThat(stock.get(2L).getReserved()).isZero();
        assertThat(stock.get(1L).getReserved()).isEqualTo(2);
        service.reserveLine(7L, 70L, 5L, 10L, 0, EXPIRES);
        assertThat(stock.get(1L).getReserved()).isZero();
        assertThat(rows).allMatch(r -> r.getStatus() == StockReservation.Status.RELEASED);
    }

    @Test
    void pagarDescuentaLoReservadoYRegistraSalidas() {
        service.reserveLine(7L, 70L, 5L, 10L, 5, EXPIRES);
        service.commitOrder(7L);

        assertThat(stock.get(1L).getOnHand()).isZero();
        assertThat(stock.get(2L).getOnHand()).isEqualTo(8);
        assertThat(stock.get(2L).getReserved()).isZero();
        ArgumentCaptor<InventoryMovementDto> movement = ArgumentCaptor.forClass(InventoryMovementDto.class);
        verify(movements, org.mockito.Mockito.times(2)).save(movement.capture());
        assertThat(movement.getAllValues()).allSatisfy(m -> {
            assertThat(m.getType()).isEqualTo("EXIT");
            assertThat(m.getReferenceType()).isEqualTo("ORDER");
            assertThat(m.getReferenceId()).isEqualTo(7L);
        });
        verify(projection).refresh(5L);
    }

    @Test
    void cancelarUnaPagadaDevuelveElStock() {
        service.reserveLine(7L, 70L, 5L, 10L, 4, EXPIRES);
        service.commitOrder(7L);
        service.restockOrder(7L);

        assertThat(stock.get(1L).getOnHand()).isEqualTo(3);
        assertThat(stock.get(2L).getOnHand()).isEqualTo(10);
        assertThat(rows).allMatch(r -> r.getStatus() == StockReservation.Status.RETURNED);
    }

    @Test
    void vencerLiberaYMarcaComoVencida() {
        service.reserveLine(7L, 70L, 5L, 10L, 2, EXPIRES);
        service.releaseOrder(7L, true);

        assertThat(stock.get(1L).getReserved()).isZero();
        assertThat(rows).allMatch(r -> r.getStatus() == StockReservation.Status.EXPIRED);
        verify(movements, never()).save(any());
    }

    @Test
    void lasSalidasManualesNoTocanLoReservado() {
        service.reserveLine(7L, 70L, 5L, 10L, 3, EXPIRES);
        StockItem main = stock.get(1L);
        assertThat(main.available()).isZero();
        assertThatThrownBy(() -> main.issue(1)).isInstanceOf(com.essenza.draco.modules.inventory.domain.model.InsufficientStockException.class);
    }
}
