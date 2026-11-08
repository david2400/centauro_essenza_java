package com.essenza.draco.modules.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Action;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.DocType;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.DocumentKind;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.DocumentReview;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Kind;
import com.essenza.draco.modules.marketplace.domain.seller.SellerEnums.Status;
import com.essenza.draco.modules.marketplace.domain.seller.SellerProfile;
import com.essenza.draco.modules.marketplace.domain.seller.SellerProfile.SellerContact;
import com.essenza.draco.modules.marketplace.domain.seller.SellerWorkflow;
import com.essenza.draco.modules.marketplace.domain.seller.SellerWorkflow.DocumentState;
import com.essenza.draco.modules.marketplace.domain.seller.SellerWorkflow.Readiness;

class SellerWorkflowTest {

    private static SellerProfile profile(Kind kind, DocType docType, String email, List<SellerContact> contacts) {
        return new SellerProfile(null, kind, "Moda Andina SAS", null, docType, "900.123.456-7", email, "3001234567",
                null, null, "Medellín", null, null, null, contacts);
    }

    private static final SellerContact PRIMARY = new SellerContact("Laura Díaz", "Gerente", "laura@moda.co", null, true);

    @Test
    void normalizesProfile() {
        SellerProfile p = profile(Kind.EXTERNAL, DocType.NIT, "Ventas@Moda.CO", List.of(PRIMARY));
        assertThat(p.docNumber()).isEqualTo("900123456-7");
        assertThat(p.email()).isEqualTo("ventas@moda.co");
        assertThat(p.code()).isNull();
        assertThatThrownBy(() -> profile(Kind.EXTERNAL, DocType.NIT, "no-es-correo", List.of())).hasMessageContaining("correo");
        assertThatThrownBy(() -> new SellerContact("Ana", null, null, null, false)).hasMessageContaining("correo o teléfono");
        assertThatThrownBy(() -> profile(Kind.EXTERNAL, DocType.NIT, null, List.of(PRIMARY, PRIMARY)))
                .hasMessageContaining("un contacto principal");
    }

    @Test
    void readinessListsWhatIsMissing() {
        SellerProfile p = profile(Kind.EXTERNAL, DocType.NIT, null, List.of());
        Readiness r = SellerWorkflow.readiness(p, List.of(), false);
        assertThat(r.forSubmit()).containsExactly("EMAIL", "PRIMARY_CONTACT", "DOC_RUT", "DOC_CAMARA_COMERCIO");
        assertThat(r.forApproval()).contains("PLAN", "DOC_RUT");

        SellerProfile ok = profile(Kind.EXTERNAL, DocType.NIT, "a@b.co", List.of(PRIMARY));
        Readiness uploaded = SellerWorkflow.readiness(ok, List.of(
                new DocumentState(DocumentKind.RUT, DocumentReview.PENDING),
                new DocumentState(DocumentKind.CAMARA_COMERCIO, DocumentReview.ACCEPTED)), true);
        assertThat(uploaded.forSubmit()).isEmpty();
        assertThat(uploaded.forApproval()).containsExactly("DOC_RUT_REVIEW", "DOCS_PENDING");

        Readiness rejected = SellerWorkflow.readiness(ok, List.of(
                new DocumentState(DocumentKind.RUT, DocumentReview.REJECTED),
                new DocumentState(DocumentKind.CAMARA_COMERCIO, DocumentReview.ACCEPTED)), true);
        assertThat(rejected.forSubmit()).containsExactly("DOC_RUT");
    }

    @Test
    void personSellersNeedIdInsteadOfChamberOfCommerceAndOwnSellerNeedsNoDocsOrPlan() {
        assertThat(SellerWorkflow.requiredDocuments(Kind.EXTERNAL, DocType.CC))
                .containsExactlyInAnyOrder(DocumentKind.RUT, DocumentKind.CEDULA_REPRESENTANTE);
        SellerProfile own = profile(Kind.OWN, DocType.NIT, "a@b.co", List.of(PRIMARY));
        assertThat(SellerWorkflow.readiness(own, List.of(), false).forApproval()).isEmpty();
    }

    @Test
    void happyPathAndCorrectionLoop() {
        Readiness ok = Readiness.ready();
        Status s = SellerWorkflow.transition(Status.DRAFT, Action.SUBMIT, null, ok);
        assertThat(s).isEqualTo(Status.IN_REVIEW);
        s = SellerWorkflow.transition(s, Action.REQUEST_CHANGES, "Falta RUT actualizado", ok);
        assertThat(s).isEqualTo(Status.CHANGES_REQUESTED);
        s = SellerWorkflow.transition(s, Action.SUBMIT, null, ok);
        s = SellerWorkflow.transition(s, Action.APPROVE, null, ok);
        assertThat(s).isEqualTo(Status.APPROVED);
        s = SellerWorkflow.transition(s, Action.SUSPEND, "Reclamos sin atender", ok);
        assertThat(s).isEqualTo(Status.SUSPENDED);
        s = SellerWorkflow.transition(s, Action.REACTIVATE, "Plan de mejora cumplido", ok);
        assertThat(s).isEqualTo(Status.APPROVED);
        assertThat(SellerWorkflow.transition(s, Action.CLOSE, "Terminación del contrato", ok)).isEqualTo(Status.CLOSED);
    }

    @Test
    void invalidTransitionsAndReasons() {
        Readiness ok = Readiness.ready();
        assertThatThrownBy(() -> SellerWorkflow.transition(Status.DRAFT, Action.APPROVE, null, ok))
                .hasMessageContaining("No se puede aprobar");
        assertThatThrownBy(() -> SellerWorkflow.transition(Status.CLOSED, Action.REACTIVATE, "porque sí", ok))
                .hasMessageContaining("CLOSED");
        assertThatThrownBy(() -> SellerWorkflow.transition(Status.APPROVED, Action.SUSPEND, " ", ok))
                .hasMessageContaining("motivo");
        assertThatThrownBy(() -> SellerWorkflow.transition(Status.IN_REVIEW, Action.APPROVE, null,
                new Readiness(List.of(), List.of("PLAN")))).hasMessageContaining("PLAN");
        assertThatThrownBy(() -> SellerWorkflow.transition(Status.DRAFT, Action.SUBMIT, null,
                new Readiness(List.of("DOC_RUT"), List.of("DOC_RUT")))).hasMessageContaining("DOC_RUT");
        assertThat(SellerWorkflow.actionsFrom(Status.CLOSED)).isEmpty();
        assertThat(SellerWorkflow.actionsFrom(Status.APPROVED)).containsExactly(Action.SUSPEND, Action.CLOSE);
    }
}
