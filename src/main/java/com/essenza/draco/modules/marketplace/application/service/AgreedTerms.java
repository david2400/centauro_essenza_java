package com.essenza.draco.modules.marketplace.application.service;

import java.time.LocalDate;
import java.util.Optional;

import com.essenza.draco.modules.marketplace.application.port.AssignmentStore;
import com.essenza.draco.modules.marketplace.application.port.AssignmentStore.AssignmentRow;
import com.essenza.draco.modules.marketplace.domain.plan.PlanTerms;

/** Condiciones acordadas (plan + excepciones, congeladas en la vigencia) de un vendedor. */
final class AgreedTerms {

    record InForce(AssignmentRow assignment, PlanTerms terms) {
    }

    private AgreedTerms() {
    }

    /** Vigencia en curso el día indicado (no cancelada). */
    static Optional<InForce> on(AssignmentStore store, long companyId, long sellerId, LocalDate date) {
        return store.bySeller(companyId, sellerId).stream()
                .filter(a -> a.cancelledAt() == null && !a.startsOn().isAfter(date)
                        && (a.endsOn() == null || !a.endsOn().isBefore(date)))
                .findFirst()
                .map(AgreedTerms::inForce);
    }

    static Optional<InForce> byId(AssignmentStore store, long companyId, long sellerId, Long assignmentId) {
        if (assignmentId == null) {
            return Optional.empty();
        }
        return store.bySeller(companyId, sellerId).stream().filter(a -> a.id() == assignmentId).findFirst()
                .map(AgreedTerms::inForce);
    }

    private static InForce inForce(AssignmentRow row) {
        return new InForce(row, TermsMapper.fromDto(TermsMapper.fromJson(row.agreedTermsJson())));
    }
}
