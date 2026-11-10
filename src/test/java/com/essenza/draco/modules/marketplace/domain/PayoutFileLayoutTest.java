package com.essenza.draco.modules.marketplace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.essenza.draco.modules.marketplace.domain.payout.PayoutFileLayout;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutFileLayout.Align;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutFileLayout.Column;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutFileLayout.Field;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutFileLayout.FileType;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutFileLayout.Layout;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutFileLayout.Payment;

class PayoutFileLayoutTest {

    static final List<Payment> PAYMENTS = List.of(
            new Payment("Moda Andina SAS", "NIT 900123456", "Moda Ñandú SAS", "NIT", "900123456", "Bancolombia", "1007", "SAVINGS", null,
                    "12345678901", new BigDecimal("1250000.50"), "COP"),
            new Payment("Ana; Pérez", "CC 1020", "=Ana", "CC", "1020", "Bre-B", null, "BRE_B_KEY", "PHONE", "3001234567",
                    new BigDecimal("89900"), "COP"));

    static Column col(Field f, int width, Align align, char pad) {
        return new Column(f, null, width, align, pad, null);
    }

    @Test
    void delimitedWithBankCodesAndTitles() {
        Layout layout = new Layout(FileType.DELIMITED, ";", true, "\n", 2, ",", "dd/MM/yyyy", false,
                Map.of("SAVINGS", "37", "BRE_B_KEY", "BB"), Map.of("PHONE", "CEL"), Map.of("NIT", "3"), List.of(),
                List.of(new Column(Field.HOLDER_DOC_TYPE, "tipo", 0, Align.LEFT, ' ', null), new Column(Field.HOLDER_NAME, "nombre", 0, Align.LEFT, ' ', null),
                        new Column(Field.ACCOUNT_TYPE, "tc", 0, Align.LEFT, ' ', null), new Column(Field.KEY_TYPE, "ll", 0, Align.LEFT, ' ', null),
                        new Column(Field.AMOUNT, "valor", 0, Align.LEFT, ' ', null), new Column(Field.PAYMENT_DATE, "fecha", 0, Align.LEFT, ' ', null),
                        new Column(Field.SELLER_NAME, "vendedor", 0, Align.LEFT, ' ', null)),
                List.of());
        PayoutFileLayout.check(layout);
        String file = PayoutFileLayout.render(layout, "LIQ-000007", LocalDate.of(2026, 11, 9), PAYMENTS);
        assertThat(file.split("\n")).containsExactly(
                "tipo;nombre;tc;ll;valor;fecha;vendedor",
                "3;Moda Ñandú SAS;37;;1250000,50;09/11/2026;Moda Andina SAS",
                "CC;'=Ana;BB;CEL;89900,00;09/11/2026;\"Ana; Pérez\"");
    }

    @Test
    void fixedWidthWithHeaderAndFooter() {
        Layout layout = new Layout(FileType.FIXED, null, false, "\r\n", 2, "", "yyyyMMdd", true, Map.of("SAVINGS", "S", "BRE_B_KEY", "B"),
                Map.of(), Map.of(), List.of(new Column(Field.CONSTANT, null, 1, Align.LEFT, ' ', "1"), col(Field.PAYMENT_DATE, 8, Align.LEFT, ' '),
                        col(Field.COUNT, 4, Align.RIGHT, '0'), col(Field.TOTAL_AMOUNT, 12, Align.RIGHT, '0')),
                List.of(new Column(Field.CONSTANT, null, 1, Align.LEFT, ' ', "6"), col(Field.HOLDER_NAME, 10, Align.LEFT, ' '),
                        col(Field.ACCOUNT_TYPE, 1, Align.LEFT, ' '), col(Field.ACCOUNT_NUMBER, 12, Align.RIGHT, '0'),
                        col(Field.AMOUNT, 10, Align.RIGHT, '0')),
                List.of(new Column(Field.CONSTANT, null, 1, Align.LEFT, ' ', "9"), col(Field.COUNT, 4, Align.RIGHT, '0')));
        PayoutFileLayout.check(layout);
        String file = PayoutFileLayout.render(layout, "LIQ-1", LocalDate.of(2026, 11, 9), PAYMENTS);
        assertThat(file.split("\r\n")).containsExactly(
                "1202611090002000133990050",
                "6Moda NanduS0123456789010125000050", // nombre recortado a 10, sin tildes ni ñ
                "6=Ana      B0030012345670008990000",
                "90002");
        assertThat(file).endsWith("\r\n");
    }

    @Test
    void layoutValidation() {
        Layout noWidth = new Layout(FileType.FIXED, null, false, "\n", 2, "", "yyyyMMdd", false, Map.of(), Map.of(), Map.of(), List.of(),
                List.of(col(Field.AMOUNT, 0, Align.RIGHT, '0')), List.of());
        assertThatThrownBy(() -> PayoutFileLayout.check(noWidth)).hasMessageContaining("ancho");
        Layout badHeader = new Layout(FileType.DELIMITED, ",", false, "\n", 2, ".", "yyyyMMdd", false, Map.of(), Map.of(), Map.of(),
                List.of(col(Field.ACCOUNT_NUMBER, 0, Align.LEFT, ' ')), List.of(col(Field.AMOUNT, 0, Align.LEFT, ' ')), List.of());
        assertThatThrownBy(() -> PayoutFileLayout.check(badHeader)).hasMessageContaining("solo van totales");
        Layout badDate = new Layout(FileType.DELIMITED, ",", false, "\n", 2, ".", "yyyyMMddqqqqqqp{", false, Map.of(), Map.of(), Map.of(),
                List.of(), List.of(col(Field.AMOUNT, 0, Align.LEFT, ' ')), List.of());
        assertThatThrownBy(() -> PayoutFileLayout.check(badDate)).hasMessageContaining("fecha");
        Layout badDelimiter = new Layout(FileType.DELIMITED, "ab", false, "\n", 2, ".", "yyyyMMdd", false, Map.of(), Map.of(), Map.of(),
                List.of(), List.of(col(Field.AMOUNT, 0, Align.LEFT, ' ')), List.of());
        assertThatThrownBy(() -> PayoutFileLayout.check(badDelimiter)).hasMessageContaining("separador");
        PayoutFileLayout.check(PayoutFileLayout.generic());
    }
}
