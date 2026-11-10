package com.essenza.draco.modules.marketplace.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Pagos a vendedores por lote (JSON en snake_case). */
public final class PayoutDtos {

    private PayoutDtos() {
    }

    /**
     * Entidad del catálogo (banco, banco digital, billetera, cooperativa o financiera). {@code achCode}:
     * código de banco que pide el archivo del banco pagador (opcional). {@code accountTypes}: tipos que admite.
     */
    public record BankDto(Long id, String code, String name, String kind, String achCode, boolean active, int position,
                          List<String> accountTypes, String wompiBankId) {
    }

    // ── Formatos del archivo de pagos (banco pagador) ──

    /**
     * Columna. {@code field}: CONSTANT, BATCH_NUMBER, PAYMENT_DATE, TOTAL_AMOUNT, COUNT, SEQUENCE, REFERENCE,
     * SELLER_NAME, SELLER_DOC, HOLDER_NAME, HOLDER_DOC_TYPE, HOLDER_DOC_NUMBER, BANK_NAME, BANK_CODE, ACCOUNT_TYPE,
     * KEY_TYPE, ACCOUNT_NUMBER, AMOUNT o CURRENCY. {@code align}: LEFT o RIGHT; {@code pad}: " " o "0".
     */
    public record ColumnDto(String field, String title, Integer width, String align, String pad, String value) {
    }

    /**
     * Diseño. {@code type}: DELIMITED o FIXED; {@code lineEnding}: CRLF o LF; tablas de códigos del banco
     * pagador (p. ej. SAVINGS → "37"); {@code header}/{@code footer}: registros opcionales de totales.
     */
    public record LayoutDto(String type, String delimiter, Boolean titleRow, String lineEnding, Integer amountDecimals,
                            String amountSeparator, String datePattern, Boolean asciiOnly, Map<String, String> accountTypeCodes,
                            Map<String, String> keyTypeCodes, Map<String, String> docTypeCodes, List<ColumnDto> header,
                            List<ColumnDto> detail, List<ColumnDto> footer) {
    }

    /** {@code id} 0 = formato genérico CSV (no editable). */
    public record FormatDto(Long id, String name, String extension, String encoding, boolean active, boolean builtIn, LayoutDto layout,
                            String updatedByName, Instant updatedAt) {
    }

    public record SaveFormatRequest(String name, String extension, String encoding, Boolean active, LayoutDto layout) {
    }

    public record PreviewDtoRequest(LayoutDto layout) {
    }

    public record FormatPreviewDto(String content) {
    }

    /** Archivo generado (el controlador lo envía con su codificación). */
    public record ExportFile(String fileName, String encoding, String extension, byte[] content) {
    }

    /**
     * {@code code} solo al crear (se genera del nombre si viene vacío). {@code wompiBankId}: id de la entidad
     * en Wompi Pagos a terceros (vacío = quitarlo; null = no cambiarlo).
     */
    public record SaveBankRequest(String code, String name, String kind, String achCode, Boolean active, Integer position,
                                  String wompiBankId) {
    }

    /**
     * Cuenta o llave Bre-B del vendedor. {@code accountNumber} (número, celular o llave) enmascarado
     * salvo para quien gestiona pagos (COMISIONES.GESTIONAR). {@code keyType} solo en llaves Bre-B.
     */
    public record BankAccountDto(Long bankId, String bankCode, String bankName, String accountType, String keyType, String accountNumber,
                                 String holderName, String holderDocType, String holderDocNumber, boolean masked, String updatedByName,
                                 Instant updatedAt) {
    }

    /** {@code bankId} opcional solo con llave Bre-B ({@code accountType = BRE_B_KEY} y {@code keyType}). */
    public record SaveBankAccountRequest(Long bankId, String accountType, String accountNumber, String keyType, String holderName,
                                         String holderDocType, String holderDocNumber) {
    }

    /**
     * Vendedor con saldo a favor. {@code owed}: lo que la tienda le debe; {@code held}: retenido por
     * órdenes sin entregar o entregadas hace menos de {@code holdDays}; {@code payable}: lo pagable hoy.
     */
    public record PayeeDto(Long sellerId, String sellerName, String sellerDoc, BigDecimal owed, BigDecimal held, BigDecimal payable,
                           BankAccountDto bankAccount, Long pendingBatchId, String pendingBatchNumber) {
    }

    public record PreviewDto(int holdDays, LocalDate releasedThrough, BigDecimal totalOwed, BigDecimal totalPayable,
                             List<PayeeDto> payees) {
    }

    /** {@code amount} vacío = todo lo pagable. */
    public record CreateItem(Long sellerId, BigDecimal amount) {
    }

    public record CreateBatchRequest(List<CreateItem> items, String note) {
    }

    public record PayRequest(String reference, LocalDate paidOn, String note) {
    }

    public record ReasonRequest(String reason) {
    }

    public record ItemDto(Long id, Long sellerId, String sellerName, String sellerDoc, BigDecimal amount, String currency,
                          String bankName, String bankCode, String accountType, String keyType, String accountNumber, String holderName,
                          String holderDocType, String holderDocNumber, String status, String reference, LocalDate paidOn, String note,
                          String actorName, Instant updatedAt, String providerStatus, String providerDetail) {
    }

    public record BatchSummaryDto(Long id, String number, String status, int items, int pending, int paid, BigDecimal total,
                                  BigDecimal paidTotal, String createdByName, Instant createdAt, Instant closedAt) {
    }

    /**
     * Dispersión automática del lote. {@code status}: null (no enviado), SENT (en el proveedor), FAILED (no se
     * pudo enviar) o DONE (todos los pagos con resultado). {@code problems}: qué falta para enviarlo.
     */
    public record DispatchDto(String provider, String environment, boolean configured, String status, String providerBatchId,
                              String detail, Instant dispatchedAt, List<String> problems) {
    }

    /** Entidad del proveedor de dispersión (para ligarla al catálogo). */
    public record ProviderBankDto(String id, String name, String code, Long linkedBankId) {
    }

    public record BankMatchDto(int matched, List<String> unmatched, List<ProviderBankDto> providerBanks) {
    }

    /**
     * {@code actions}: PAY, REJECT, CANCEL, EXPORT, DISPATCH (enviar a dispersión) y SYNC (consultar el
     * resultado) según estado y permisos. Con el lote en dispersión no se marca nada a mano.
     */
    public record BatchDetailDto(BatchSummaryDto summary, int holdDays, String note, String statusReason, List<ItemDto> items,
                                 List<String> actions, DispatchDto dispatch) {
    }
}
