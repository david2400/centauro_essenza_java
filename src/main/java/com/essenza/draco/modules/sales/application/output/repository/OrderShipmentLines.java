package com.essenza.draco.modules.sales.application.output.repository;

import java.util.List;

/** Líneas de una orden para cotizar su envío (SKU o producto y unidades). */
public interface OrderShipmentLines {

    record Line(Long skuId, Long productId, int quantity) {
    }

    List<Line> lines(long orderId);
}
