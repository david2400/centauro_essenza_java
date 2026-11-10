package com.essenza.draco.modules.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.marketplace.domain.payout.PayoutRules;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutRules.BankRef;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutRules.BatchStatus;

class PayoutRulesTest {

    @Test
    void payableIsOwedMinusHeld() {
        assertThat(PayoutRules.payable(new BigDecimal("-140000"), new BigDecimal("50000"))).isEqualByComparingTo("90000");
        assertThat(PayoutRules.payable(new BigDecimal("-40000"), new BigDecimal("50000"))).isEqualByComparingTo("0");
        assertThat(PayoutRules.payable(new BigDecimal("25000"), BigDecimal.ZERO)).isEqualByComparingTo("0"); // el vendedor debe
        assertThat(PayoutRules.payable(new BigDecimal("-100"), new BigDecimal("-30"))).isEqualByComparingTo("100");
        assertThat(PayoutRules.payable(null, null)).isEqualByComparingTo("0");
    }

    @Test
    void itemAmount() {
        assertThat(PayoutRules.itemAmount(null, new BigDecimal("90000"), "A")).isEqualByComparingTo("90000");
        assertThat(PayoutRules.itemAmount(new BigDecimal("1000.5"), new BigDecimal("90000"), "A")).isEqualByComparingTo("1000.50");
        assertThatThrownBy(() -> PayoutRules.itemAmount(new BigDecimal("90001"), new BigDecimal("90000"), "A"))
                .hasMessageContaining("máximo");
        assertThatThrownBy(() -> PayoutRules.itemAmount(null, BigDecimal.ZERO, "A")).hasMessageContaining("no se le puede pagar");
        assertThatThrownBy(() -> PayoutRules.itemAmount(new BigDecimal("1.001"), BigDecimal.TEN, "A")).hasMessageContaining("decimales");
        assertThatThrownBy(() -> PayoutRules.itemAmount(new BigDecimal("-1"), BigDecimal.TEN, "A")).hasMessageContaining("mayor que cero");
    }

    static final BankRef BANCOLOMBIA = new BankRef(1, "Bancolombia", "BANK", "1007", true);
    static final BankRef NEQUI = new BankRef(2, "Nequi", "WALLET", null, true);
    static final BankRef NU = new BankRef(3, "Nu Colombia", "DIGITAL_BANK", null, true);
    static final BankRef CLOSED = new BankRef(4, "Banco Cerrado", "BANK", null, false);

    @Test
    void bankAccountsInBanksDigitalBanksAndWallets() {
        var account = PayoutRules.checkBankAccount(BANCOLOMBIA, "savings", "123-456 789 01", null, "Moda Andina SAS", "nit", "900.123.456-7");
        assertThat(account.accountType()).isEqualTo("SAVINGS");
        assertThat(account.accountNumber()).isEqualTo("12345678901");
        assertThat(account.bankName()).isEqualTo("Bancolombia");
        assertThat(account.bankCode()).isEqualTo("1007");
        assertThat(account.holderDocType()).isEqualTo("NIT");
        assertThat(account.holderDocNumber()).isEqualTo("900123456-7");
        assertThat(PayoutRules.checkBankAccount(NEQUI, "DIGITAL_WALLET", "300 123 4567", null, "Ana", "CC", "1020").accountNumber())
                .isEqualTo("3001234567");
        assertThat(PayoutRules.checkBankAccount(NU, "SAVINGS", "12345678", null, "Ana", "CC", "1020").bankName()).isEqualTo("Nu Colombia");
        assertThatThrownBy(() -> PayoutRules.checkBankAccount(NEQUI, "SAVINGS", "1234567", null, "T", "CC", "1234")).hasMessageContaining("no admite");
        assertThatThrownBy(() -> PayoutRules.checkBankAccount(NU, "CHECKING", "1234567", null, "T", "CC", "1234")).hasMessageContaining("no admite");
        assertThatThrownBy(() -> PayoutRules.checkBankAccount(NEQUI, "DIGITAL_WALLET", "6012345678", null, "T", "CC", "1234"))
                .hasMessageContaining("celular");
        assertThatThrownBy(() -> PayoutRules.checkBankAccount(CLOSED, "SAVINGS", "1234567", null, "T", "CC", "1234")).hasMessageContaining("no está activo");
        assertThatThrownBy(() -> PayoutRules.checkBankAccount(null, "SAVINGS", "1234567", null, "T", "CC", "1234")).hasMessageContaining("Elige");
        assertThatThrownBy(() -> PayoutRules.checkBankAccount(BANCOLOMBIA, "CRYPTO", "1234567", null, "T", "CC", "1234")).hasMessageContaining("Tipo de cuenta");
        assertThatThrownBy(() -> PayoutRules.checkBankAccount(BANCOLOMBIA, "SAVINGS", "12AB", null, "T", "CC", "1234")).hasMessageContaining("dígitos");
        assertThatThrownBy(() -> PayoutRules.checkBankAccount(BANCOLOMBIA, "SAVINGS", "1234567", null, " ", "CC", "1234")).hasMessageContaining("titular");
        assertThat(PayoutRules.mask("12345678901")).isEqualTo("••••8901");
        assertThat(PayoutRules.mask("ana.perez@correo.co")).isEqualTo("a••••@correo.co");
    }

    @Test
    void breBKeysReachAnyEntity() {
        var phone = PayoutRules.checkBankAccount(null, "BRE_B_KEY", "+57 300 123 4567", "phone", "Ana", "CC", "1020");
        assertThat(phone.accountNumber()).isEqualTo("3001234567");
        assertThat(phone.keyType()).isEqualTo("PHONE");
        assertThat(phone.bankName()).isEqualTo("Bre-B");
        assertThat(phone.bankId()).isNull();
        assertThat(PayoutRules.checkBankAccount(NEQUI, "BRE_B_KEY", "Ana@Correo.co", "EMAIL", "Ana", "CC", "1020").accountNumber())
                .isEqualTo("ana@correo.co");
        assertThat(PayoutRules.checkBankAccount(null, "BRE_B_KEY", "@modaandina", "ALPHANUMERIC", "Ana", "CC", "1020").accountNumber())
                .isEqualTo("@modaandina");
        assertThat(PayoutRules.checkBankAccount(null, "BRE_B_KEY", "1.020.304.050", "DOCUMENT", "Ana", "CC", "1020").accountNumber())
                .isEqualTo("1020304050");
        assertThatThrownBy(() -> PayoutRules.checkBankAccount(null, "BRE_B_KEY", "modaandina", "ALPHANUMERIC", "A", "CC", "1234"))
                .hasMessageContaining("@");
        assertThatThrownBy(() -> PayoutRules.checkBankAccount(null, "BRE_B_KEY", "no-es-correo", "EMAIL", "A", "CC", "1234"))
                .hasMessageContaining("correo");
        assertThatThrownBy(() -> PayoutRules.checkBankAccount(null, "BRE_B_KEY", "3001234567", null, "A", "CC", "1234"))
                .hasMessageContaining("tipo de llave");
    }

    @Test
    void bankCatalogEntries() {
        PayoutRules.checkBank("Banco Pichincha", "BANK", "1060");
        PayoutRules.checkBank("Cooperativa X", "COOPERATIVE", null);
        assertThatThrownBy(() -> PayoutRules.checkBank(" ", "BANK", null)).hasMessageContaining("nombre");
        assertThatThrownBy(() -> PayoutRules.checkBank("X", "CRYPTO", null)).hasMessageContaining("Tipo de entidad");
        assertThatThrownBy(() -> PayoutRules.checkBank("X", "BANK", "12-34")).hasMessageContaining("código");
        assertThat(PayoutRules.accountTypesFor("WALLET")).containsExactlyInAnyOrder("DIGITAL_WALLET", "BRE_B_KEY");
    }

    @Test
    void statusNumberAndCsv() {
        assertThat(PayoutRules.statusOf(1, 3)).isEqualTo(BatchStatus.OPEN);
        assertThat(PayoutRules.statusOf(0, 1)).isEqualTo(BatchStatus.CLOSED);
        assertThat(PayoutRules.statusOf(0, 0)).isEqualTo(BatchStatus.CANCELLED);
        assertThat(PayoutRules.number(12)).isEqualTo("LIQ-000012");
        assertThat(PayoutRules.csvCell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(PayoutRules.csvCell("+57")).isEqualTo("'+57");
        assertThat(PayoutRules.csvCell("Moda, SAS")).isEqualTo("\"Moda, SAS\"");
        assertThat(PayoutRules.csvCell(null)).isEmpty();
    }
}
