package com.essenza.draco.modules.sales.domain.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.sales.domain.model.order.OrderRuleViolation;
import com.essenza.draco.modules.sales.domain.model.purchase.CartSplit;
import com.essenza.draco.modules.sales.domain.model.purchase.CartSplit.Group;
import com.essenza.draco.modules.sales.domain.model.purchase.CartSplit.Item;
import com.essenza.draco.modules.sales.domain.model.purchase.PurchaseNumber;

class CartSplitTest {

    @Test
    void mergesRepeatedLinesPerSkuAndOffer() {
        List<Item> merged = CartSplit.merge(List.of(new Item(1L, null, null, 2), new Item(1L, null, null, 3),
                new Item(1L, 50L, null, 1), new Item(1L, 50L, null, 1)));
        assertThat(merged).containsExactly(new Item(1L, null, null, 5), new Item(1L, 50L, null, 2));
    }

    @Test
    void oneGroupPerSellerOwnFirstInFixedOrder() {
        List<Group> groups = CartSplit.groups(List.of(new Item(3L, 70L, 9L, 1), new Item(1L, null, null, 1),
                new Item(2L, 60L, 4L, 1), new Item(4L, 65L, 9L, 2)));
        assertThat(groups).extracting(Group::sellerId).containsExactly(null, 4L, 9L);
        assertThat(groups.get(0).own()).isTrue();
        assertThat(groups.get(2).items()).extracting(Item::offerId).containsExactly(65L, 70L);
        assertThat(CartSplit.offersInLockOrder(List.of(new Item(3L, 70L, null, 1), new Item(2L, 60L, null, 1),
                new Item(1L, null, null, 1)))).containsExactly(60L, 70L);
    }

    @Test
    void invalidCarts() {
        assertThatThrownBy(() -> CartSplit.merge(List.of())).isInstanceOf(OrderRuleViolation.class);
        assertThatThrownBy(() -> CartSplit.merge(List.of(new Item(null, null, null, 1)))).hasMessageContaining("SKU");
        assertThatThrownBy(() -> CartSplit.merge(List.of(new Item(1L, null, null, 0)))).hasMessageContaining("cantidad");
        assertThatThrownBy(() -> CartSplit.merge(List.of(new Item(1L, null, null, 9_000), new Item(1L, null, null, 1_000))))
                .hasMessageContaining("Máximo");
        List<Item> many = new ArrayList<>();
        for (long i = 0; i <= CartSplit.MAX_LINES; i++) {
            many.add(new Item(i + 1, null, null, 1));
        }
        assertThatThrownBy(() -> CartSplit.merge(many)).hasMessageContaining("máximo");
        assertThatThrownBy(() -> CartSplit.groups(List.of(new Item(1L, null, 5L, 1)))).isInstanceOf(OrderRuleViolation.class);
    }

    @Test
    void consignmentGoesInItsOwnSubOrder() {
        // Mismo vendedor: lo que despacha él y lo que despacha la tienda desde su consignación se cobran por separado.
        List<CartSplit.Group> groups = CartSplit.groups(List.of(new Item(1L, 10L, 5L, 1, true), new Item(2L, 11L, 5L, 2, false),
                new Item(3L, null, null, 1), new Item(4L, 12L, 5L, 1, true)));
        assertThat(groups).extracting(CartSplit.Group::sellerId).containsExactly(null, 5L, 5L);
        assertThat(groups).extracting(CartSplit.Group::consignment).containsExactly(false, false, true);
        assertThat(groups.get(2).items()).extracting(Item::offerId).containsExactly(10L, 12L);
        assertThat(CartSplit.merge(List.of(new Item(1L, 10L, null, 1, true), new Item(1L, 10L, null, 2, true))).get(0).consignment())
                .isTrue();
    }

    @Test
    void purchaseNumbers() {
        assertThat(PurchaseNumber.of(7)).isEqualTo("CMP-000007");
        assertThat(PurchaseNumber.of(1234567)).isEqualTo("CMP-1234567");
        assertThatThrownBy(() -> PurchaseNumber.of(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
