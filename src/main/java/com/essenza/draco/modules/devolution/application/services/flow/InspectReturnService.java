package com.essenza.draco.modules.devolution.application.services.flow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.essenza.draco.modules.devolution.application.dto.flow.InspectReturnDto;
import com.essenza.draco.modules.devolution.application.dto.order_devolution.OrderDevolutionDto;
import com.essenza.draco.modules.devolution.application.input.flow.InspectReturnUseCase;
import com.essenza.draco.modules.devolution.domain.model.DevolutionRuleViolation;
import com.essenza.draco.modules.devolution.domain.model.ItemCondition;
import com.essenza.draco.modules.devolution.domain.model.ReturnCase;
import com.essenza.draco.modules.devolution.domain.model.ReturnItem;
import com.essenza.draco.shared.common.inventory.StockReturns;
import com.essenza.draco.shared.common.marketplace.ConsignmentReturns;
import com.essenza.draco.shared.common.sales.OrderReturns.ReturnableLine;
import com.essenza.draco.shared.common.sales.OrderReturns.ReturnableOrder;

/**
 * Inspección: define estado, reingreso y cargo por línea, calcula el valor acreditado y
 * reingresa al stock (entrada "DEVOLUTION") lo que está en condiciones de venderse.
 */
@Service
@Transactional
public class InspectReturnService implements InspectReturnUseCase {

    private final ReturnSoporte soporte;
    private final StockReturns stock;
    private final ConsignmentReturns consignment;

    public InspectReturnService(ReturnSoporte soporte, StockReturns stock, ConsignmentReturns consignment) {
        this.soporte = soporte;
        this.stock = stock;
        this.consignment = consignment;
    }

    @Override
    public OrderDevolutionDto inspect(Long id, InspectReturnDto input) {
        ReturnCase returnCase = soporte.locked(id);
        ReturnableOrder order = soporte.order(returnCase.orderId());
        Map<Long, ReturnCase.LineValue> values = new HashMap<>();
        for (ReturnableLine line : order.lines()) {
            values.put(line.lineId(), new ReturnCase.LineValue(line.total(), line.quantity()));
        }
        Map<Long, ReturnCase.Inspection> inspections = new HashMap<>();
        if (input != null && input.lines() != null) {
            for (InspectReturnDto.Line line : input.lines()) {
                ReturnCase.Inspection inspection = new ReturnCase.Inspection(ItemCondition.parse(line.condition()),
                        line.restock(), line.restockingFee());
                if (inspections.put(line.detailId(), inspection) != null) {
                    throw new DevolutionRuleViolation("La línea " + line.detailId() + " está repetida.");
                }
            }
        }
        List<ReturnItem> restock = returnCase.inspect(input == null ? null : input.by(),
                input == null ? null : input.warehouseId(), inspections, values,
                input == null ? null : input.notes(), ReturnSoporte.now());
        if (!restock.isEmpty()) {
            // Lo vendido desde consignación vuelve al stock del vendedor; el resto, al inventario propio.
            Set<Long> consigned = consignment.receiveReturn(returnCase.orderId(), returnCase.id(), returnCase.warehouseId(),
                    restock.stream().map(item -> new ConsignmentReturns.ReturnedLine(item.productOrderId(), item.skuId(),
                            item.received())).toList());
            List<StockReturns.ReturnedUnit> own = restock.stream()
                    .filter(item -> !consigned.contains(item.productOrderId()))
                    .map(item -> new StockReturns.ReturnedUnit(item.productId(), item.skuId(), item.received()))
                    .toList();
            if (!own.isEmpty()) {
                stock.restock(returnCase.id(), returnCase.warehouseId(), own);
            }
        }
        return soporte.toDto(soporte.save(returnCase));
    }
}
