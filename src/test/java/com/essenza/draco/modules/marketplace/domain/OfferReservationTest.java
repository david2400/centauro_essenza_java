package com.essenza.draco.modules.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.marketplace.domain.offer.OfferReservation;
import com.essenza.draco.modules.marketplace.domain.offer.OfferReservation.Move;
import com.essenza.draco.modules.marketplace.domain.offer.OfferReservation.Operation;
import com.essenza.draco.modules.marketplace.domain.offer.OfferReservation.Status;

class OfferReservationTest {

    @Test
    void activeReservationMoves() {
        assertThat(OfferReservation.move(Status.ACTIVE, Operation.COMMIT)).isEqualTo(new Move(Status.COMMITTED, false));
        assertThat(OfferReservation.move(Status.ACTIVE, Operation.RELEASE)).isEqualTo(new Move(Status.RELEASED, true));
        assertThat(OfferReservation.move(Status.ACTIVE, Operation.EXPIRE)).isEqualTo(new Move(Status.EXPIRED, true));
        assertThat(OfferReservation.move(Status.ACTIVE, Operation.RESTOCK)).isNull();
    }

    @Test
    void onlyCommittedIsRestockedAndOperationsRepeatSafely() {
        assertThat(OfferReservation.move(Status.COMMITTED, Operation.RESTOCK)).isEqualTo(new Move(Status.RETURNED, true));
        assertThat(OfferReservation.move(Status.COMMITTED, Operation.RELEASE)).isNull();
        assertThat(OfferReservation.move(Status.RELEASED, Operation.RELEASE)).isNull();
        assertThat(OfferReservation.move(Status.RETURNED, Operation.RESTOCK)).isNull();
        assertThat(OfferReservation.move(Status.EXPIRED, Operation.COMMIT)).isNull();
    }
}
