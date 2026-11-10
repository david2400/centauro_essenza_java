package com.essenza.draco.modules.marketplace.domain.payout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;

/**
 * Formato del archivo de pagos que se carga en el banco pagador de la tienda. Cada banco tiene el
 * suyo, así que el formato se configura: archivo delimitado (CSV, TXT con ; o |) o de ancho fijo,
 * con registro de encabezado, detalle (uno por pago) y control opcionales, columnas tomadas de los
 * datos del pago o constantes, y tablas de códigos propias del banco (tipo de cuenta, tipo de llave,
 * tipo de documento). El código de cada entidad destino sale del catálogo de bancos.
 */
public final class PayoutFileLayout {

    public enum FileType { DELIMITED, FIXED }

    public enum Align { LEFT, RIGHT }

    /** Datos disponibles por columna. Los de totales sirven en encabezado y control; los del pago, en el detalle. */
    public enum Field {
        CONSTANT, BATCH_NUMBER, PAYMENT_DATE, TOTAL_AMOUNT, COUNT,
        SEQUENCE, REFERENCE, SELLER_NAME, SELLER_DOC, HOLDER_NAME, HOLDER_DOC_TYPE, HOLDER_DOC_NUMBER,
        BANK_NAME, BANK_CODE, ACCOUNT_TYPE, KEY_TYPE, ACCOUNT_NUMBER, AMOUNT, CURRENCY
    }

    static final Set<Field> SUMMARY_FIELDS = Set.of(Field.CONSTANT, Field.BATCH_NUMBER, Field.PAYMENT_DATE, Field.TOTAL_AMOUNT, Field.COUNT);
    static final int MAX_COLUMNS = 60;

    /**
     * @param width   ancho fijo (obligatorio en FIXED; en DELIMITED, 0 = sin recortar)
     * @param pad     relleno en FIXED (espacio o 0)
     * @param value   texto de la columna CONSTANT
     */
    public record Column(Field field, String title, int width, Align align, char pad, String value) {
    }

    /**
     * @param titleRow       primera fila con los títulos de las columnas del detalle (solo DELIMITED)
     * @param amountDecimals decimales del valor (0 a 2); {@code amountSeparator} vacío = sin separador (p. ej. 1234500 = 12.345,00)
     * @param asciiOnly      quitar tildes y ñ (muchos bancos no las aceptan)
     */
    public record Layout(FileType type, String delimiter, boolean titleRow, String lineEnding, int amountDecimals,
                         String amountSeparator, String datePattern, boolean asciiOnly, Map<String, String> accountTypeCodes,
                         Map<String, String> keyTypeCodes, Map<String, String> docTypeCodes, List<Column> header, List<Column> detail,
                         List<Column> footer) {
    }

    /** Un pago del lote tal como sale en el archivo. */
    public record Payment(String sellerName, String sellerDoc, String holderName, String holderDocType, String holderDocNumber,
                          String bankName, String bankCode, String accountType, String keyType, String accountNumber, BigDecimal amount,
                          String currency) {
    }

    private PayoutFileLayout() {
    }

    /** Valida el formato antes de guardarlo. */
    public static void check(Layout layout) {
        MarketplaceRuleViolation.check(layout != null && layout.type() != null, "Indica el tipo de archivo: delimitado o de ancho fijo.");
        if (layout.type() == FileType.DELIMITED) {
            MarketplaceRuleViolation.check(layout.delimiter() != null && layout.delimiter().length() == 1
                    && !layout.delimiter().matches("[A-Za-z0-9\"]"), "El separador debe ser un solo carácter (, ; | o tabulador).");
        }
        MarketplaceRuleViolation.check("\r\n".equals(layout.lineEnding()) || "\n".equals(layout.lineEnding()), "Fin de línea no válido.");
        MarketplaceRuleViolation.check(layout.amountDecimals() >= 0 && layout.amountDecimals() <= 2, "Los decimales del valor van de 0 a 2.");
        MarketplaceRuleViolation.check(layout.amountSeparator() == null || layout.amountSeparator().isEmpty()
                || ".".equals(layout.amountSeparator()) || ",".equals(layout.amountSeparator()), "El separador decimal es punto, coma o ninguno.");
        try {
            DateTimeFormatter.ofPattern(layout.datePattern() == null ? "" : layout.datePattern());
            MarketplaceRuleViolation.check(layout.datePattern() != null && !layout.datePattern().isBlank(), "Indica el formato de fecha.");
        } catch (IllegalArgumentException ex) {
            throw new MarketplaceRuleViolation("Formato de fecha no válido: " + layout.datePattern() + " (p. ej. yyyyMMdd o dd/MM/yyyy).");
        }
        MarketplaceRuleViolation.check(layout.detail() != null && !layout.detail().isEmpty(), "El detalle necesita al menos una columna.");
        checkColumns(layout, layout.header(), "encabezado", true);
        checkColumns(layout, layout.detail(), "detalle", false);
        checkColumns(layout, layout.footer(), "control", true);
    }

    private static void checkColumns(Layout layout, List<Column> columns, String record, boolean summary) {
        if (columns == null) {
            return;
        }
        MarketplaceRuleViolation.check(columns.size() <= MAX_COLUMNS, "El " + record + " admite máximo " + MAX_COLUMNS + " columnas.");
        for (int i = 0; i < columns.size(); i++) {
            Column c = columns.get(i);
            String where = "Columna " + (i + 1) + " del " + record;
            MarketplaceRuleViolation.check(c != null && c.field() != null, where + ": elige el dato.");
            MarketplaceRuleViolation.check(!summary || SUMMARY_FIELDS.contains(c.field()),
                    where + ": en " + record + " solo van totales, fecha, lote o constantes.");
            MarketplaceRuleViolation.check(c.width() >= 0 && c.width() <= 200, where + ": el ancho va de 0 a 200.");
            if (layout.type() == FileType.FIXED) {
                MarketplaceRuleViolation.check(c.width() > 0, where + ": en ancho fijo cada columna necesita su ancho.");
            }
            MarketplaceRuleViolation.check(c.pad() == ' ' || c.pad() == '0', where + ": el relleno es espacio o 0.");
            MarketplaceRuleViolation.check(c.field() != Field.CONSTANT || (c.value() != null && c.value().length() <= 200),
                    where + ": escribe el valor constante (máximo 200 caracteres).");
        }
    }

    /** Genera el archivo (sin los pagos rechazados, que el servicio ya excluye). */
    public static String render(Layout layout, String batchNumber, LocalDate paymentDate, List<Payment> payments) {
        BigDecimal total = payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String date = paymentDate.format(DateTimeFormatter.ofPattern(layout.datePattern(), Locale.ROOT));
        List<String> lines = new ArrayList<>();
        if (layout.type() == FileType.DELIMITED && layout.titleRow()) {
            lines.add(String.join(layout.delimiter(), layout.detail().stream()
                    .map(c -> cell(layout, c, c.title() == null ? c.field().name() : c.title())).toList()));
        }
        if (layout.header() != null && !layout.header().isEmpty()) {
            lines.add(line(layout, layout.header(), batchNumber, date, total, payments.size(), 0, null));
        }
        for (int i = 0; i < payments.size(); i++) {
            lines.add(line(layout, layout.detail(), batchNumber, date, total, payments.size(), i + 1, payments.get(i)));
        }
        if (layout.footer() != null && !layout.footer().isEmpty()) {
            lines.add(line(layout, layout.footer(), batchNumber, date, total, payments.size(), 0, null));
        }
        return String.join(layout.lineEnding(), lines) + layout.lineEnding();
    }

    private static String line(Layout layout, List<Column> columns, String batch, String date, BigDecimal total, int count, int sequence,
                               Payment p) {
        List<String> cells = new ArrayList<>();
        for (Column c : columns) {
            cells.add(cell(layout, c, value(layout, c, batch, date, total, count, sequence, p)));
        }
        return String.join(layout.type() == FileType.DELIMITED ? layout.delimiter() : "", cells);
    }

    private static String value(Layout layout, Column c, String batch, String date, BigDecimal total, int count, int sequence, Payment p) {
        return switch (c.field()) {
            case CONSTANT -> c.value() == null ? "" : c.value();
            case BATCH_NUMBER -> batch;
            case PAYMENT_DATE -> date;
            case TOTAL_AMOUNT -> amount(layout, total);
            case COUNT -> String.valueOf(count);
            case SEQUENCE -> String.valueOf(sequence);
            case REFERENCE -> batch + "-" + sequence;
            case SELLER_NAME -> p == null ? "" : nz(p.sellerName());
            case SELLER_DOC -> p == null ? "" : nz(p.sellerDoc());
            case HOLDER_NAME -> p == null ? "" : nz(p.holderName());
            case HOLDER_DOC_TYPE -> p == null ? "" : code(layout.docTypeCodes(), p.holderDocType());
            case HOLDER_DOC_NUMBER -> p == null ? "" : nz(p.holderDocNumber());
            case BANK_NAME -> p == null ? "" : nz(p.bankName());
            case BANK_CODE -> p == null ? "" : nz(p.bankCode());
            case ACCOUNT_TYPE -> p == null ? "" : code(layout.accountTypeCodes(), p.accountType());
            case KEY_TYPE -> p == null ? "" : code(layout.keyTypeCodes(), p.keyType());
            case ACCOUNT_NUMBER -> p == null ? "" : nz(p.accountNumber());
            case AMOUNT -> p == null ? "" : amount(layout, p.amount());
            case CURRENCY -> p == null ? "" : nz(p.currency());
        };
    }

    static String amount(Layout layout, BigDecimal value) {
        BigDecimal scaled = value.setScale(layout.amountDecimals(), RoundingMode.HALF_UP);
        String plain = scaled.toPlainString();
        String sep = layout.amountSeparator() == null ? "" : layout.amountSeparator();
        return layout.amountDecimals() == 0 ? plain : plain.replace(".", sep);
    }

    private static String code(Map<String, String> codes, String value) {
        if (value == null) {
            return "";
        }
        return codes != null && codes.containsKey(value) ? codes.get(value) : value;
    }

    private static String cell(Layout layout, Column c, String raw) {
        String v = raw == null ? "" : raw.replace("\r", " ").replace("\n", " ");
        if (layout.asciiOnly()) {
            v = Normalizer.normalize(v, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^\\x20-\\x7E]", "");
        }
        if (layout.type() == FileType.FIXED) {
            if (v.length() > c.width()) {
                v = c.align() == Align.RIGHT ? v.substring(v.length() - c.width()) : v.substring(0, c.width());
            }
            String pad = String.valueOf(c.pad()).repeat(c.width() - v.length());
            return c.align() == Align.RIGHT ? pad + v : v + pad;
        }
        if (c.width() > 0 && v.length() > c.width()) {
            v = v.substring(0, c.width());
        }
        // Sin fórmulas al abrirlo en una hoja de cálculo; comillas si trae el separador.
        if (!v.isEmpty() && "=+@\t".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(layout.delimiter()) || v.contains("\"")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    /** Formato genérico (CSV con títulos), el de siempre. */
    public static Layout generic() {
        Map<Field, String> titles = new java.util.LinkedHashMap<>();
        titles.put(Field.BATCH_NUMBER, "lote");
        titles.put(Field.SEQUENCE, "secuencia");
        titles.put(Field.SELLER_NAME, "vendedor");
        titles.put(Field.SELLER_DOC, "documento_vendedor");
        titles.put(Field.BANK_NAME, "banco");
        titles.put(Field.BANK_CODE, "codigo_banco");
        titles.put(Field.ACCOUNT_TYPE, "tipo_cuenta");
        titles.put(Field.KEY_TYPE, "tipo_llave");
        titles.put(Field.ACCOUNT_NUMBER, "numero_cuenta_o_llave");
        titles.put(Field.HOLDER_NAME, "titular");
        titles.put(Field.HOLDER_DOC_TYPE, "tipo_documento_titular");
        titles.put(Field.HOLDER_DOC_NUMBER, "documento_titular");
        titles.put(Field.AMOUNT, "valor");
        titles.put(Field.CURRENCY, "moneda");
        List<Column> detail = new ArrayList<>();
        titles.forEach((field, title) -> detail.add(new Column(field, title, 0, Align.LEFT, ' ', null)));
        return new Layout(FileType.DELIMITED, ",", true, "\r\n", 2, ".", "yyyy-MM-dd", false, Map.of(), Map.of(), Map.of(), List.of(),
                detail, List.of());
    }
}
