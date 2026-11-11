package com.essenza.draco.modules.sales.cash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.sales.application.dto.cash.CashierDto;
import com.essenza.draco.modules.sales.application.services.cash.CashierService;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.security.CompanyUsers;
import com.essenza.draco.shared.security.CompanyUsers.CompanyUser;
import com.essenza.draco.shared.security.RequestActor;

class CashierServiceTest {

    private static final RequestActor ME = new RequestActor(3, "7", "djaramillo", Set.of("CAJA.GESTIONAR"));
    private static final List<CompanyUser> USERS = List.of(new CompanyUser("8", "ana", "Ana Ruiz"),
            new CompanyUser("7", "djaramillo", "David Jaramillo Lopez"));

    private final AtomicBoolean down = new AtomicBoolean();
    private final CompanyUsers users = () -> {
        if (down.get()) {
            throw new IllegalStateException("access_control caído");
        }
        return USERS;
    };
    private final CashierService service = new CashierService(() -> ME, users);

    @Test
    void theSessionUserComesFirst() {
        assertThat(service.findAll()).containsExactly(new CashierDto("7", "djaramillo", "David Jaramillo Lopez", true),
                new CashierDto("8", "ana", "Ana Ruiz", false));
    }

    @Test
    void withoutAChoiceTheShiftIsOpenedByTheSessionUser() {
        assertThat(service.resolveName(null)).isEqualTo("David Jaramillo Lopez");
        assertThat(service.resolveName(" 7 ")).isEqualTo("David Jaramillo Lopez");
    }

    @Test
    void anotherCashierMustBelongToTheCompanyApplication() {
        assertThat(service.resolveName("8")).isEqualTo("Ana Ruiz");
        assertThatThrownBy(() -> service.resolveName("99")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ifAccessControlIsDownTheSessionUserCanStillOpenButNotPickSomeoneElse() {
        down.set(true);
        assertThat(service.resolveName(null)).isEqualTo("djaramillo");
        assertThatThrownBy(() -> service.resolveName("8")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void outsideAccessControlTheSessionUserIsStillListed() {
        CashierService alone = new CashierService(() -> ME, List::of);
        assertThat(alone.findAll()).containsExactly(new CashierDto("7", "djaramillo", "djaramillo", true));
    }
}
