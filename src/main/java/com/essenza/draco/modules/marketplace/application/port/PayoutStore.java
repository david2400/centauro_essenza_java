package com.essenza.draco.modules.marketplace.application.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.BatchSummaryDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.ItemDto;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutRules.BankAccount;
import com.essenza.draco.modules.marketplace.domain.payout.PayoutRules.BankRef;

/** Pagos a vendedores por lote y sus cuentas bancarias (JDBC, filtrado por empresa). */
public interface PayoutStore {

    // ── Catálogo de entidades (común a la plataforma) ──

    List<BankRow> banks(boolean includeInactive);

    Optional<BankRef> bank(long id);

    boolean bankCodeExists(String code);

    long insertBank(String code, String name, String kind, String achCode, int position, String actorId, Instant now);

    boolean updateBank(long id, String name, String kind, String achCode, boolean active, int position, String actorId, Instant now);

    // ── Formatos del archivo de pagos (comunes a la plataforma, como el catálogo) ──

    List<FormatRow> formats(boolean includeInactive);

    Optional<FormatRow> format(long id);

    boolean formatNameExists(String name, Long exceptId);

    long insertFormat(String name, String extension, String encoding, String layoutJson, String actorId, String actorName, Instant now);

    boolean updateFormat(long id, String name, String extension, String encoding, String layoutJson, boolean active, String actorId,
                         String actorName, Instant now);

    /** Vendedores externos de la empresa (no cerrados) con su documento. */
    Map<Long, SellerRef> sellers(long companyId);

    /**
     * Abonado por lo que cobró la tienda (ventas menos reversas) en órdenes aún retenidas: no
     * entregadas, o entregadas a partir de {@code releasedBefore}.
     */
    Map<Long, BigDecimal> held(long companyId, LocalDateTime releasedBefore);

    Map<Long, Stored> bankAccounts(long companyId);

    Optional<Stored> bankAccount(long companyId, long sellerId);

    void saveBankAccount(long companyId, long sellerId, BankAccount account, String actorId, String actorName, Instant now);

    /** Lote con pago pendiente de cada vendedor (id y número). */
    Map<Long, PendingRef> pendingBySeller(long companyId);

    long insertBatch(long companyId, int holdDays, String note, String actorId, String actorName, Instant now);

    void setBatchNumber(long id, String number);

    /** @throws org.springframework.dao.DataIntegrityViolationException si el vendedor ya tiene un pago pendiente */
    long insertItem(long batchId, SellerRef seller, BigDecimal amount, String currency, BankAccount account, Instant now);

    Optional<BatchRow> batch(long companyId, long id, boolean forUpdate);

    List<ItemDto> items(long batchId);

    /** Bloquea el pago si es del lote. */
    Optional<ItemDto> lockItem(long batchId, long itemId);

    /** @return false si el pago ya no estaba pendiente */
    boolean settleItem(long itemId, String status, String reference, LocalDate paidOn, String note, String actorId, String actorName,
                       Instant now);

    void setBatchStatus(long id, String status, String reason, Instant closedAt);

    Page<BatchSummaryDto> search(long companyId, String status, Pageable pageable);

    // ── Dispersión automática ──

    /** {@code providerBankId} null = quitarlo. */
    void setProviderBankId(long bankId, String providerBankId);

    List<DispatchRow> dispatchRows(long batchId);

    /** {@code providerBatchId} null conserva el que tenga; {@code dispatched_at/by} solo al pasar a SENT. */
    void markDispatch(long batchId, String provider, String status, String providerBatchId, String idempotencyKey, String detail,
                      String actorId, Instant now);

    void setItemProvider(long itemId, String reference, String txId, String status, String detail);

    /** Lotes abiertos con envío en curso en el proveedor (todas las empresas: para el sincronizador). */
    List<BatchKey> sentBatches(String provider);

    Optional<BatchKey> batchByProvider(String provider, String providerBatchId);

    record SellerRef(long id, String name, String doc) {
    }

    record BankRow(long id, String code, String name, String kind, String achCode, boolean active, int position, String wompiBankId) {
    }

    record FormatRow(long id, String name, String extension, String encoding, String layoutJson, boolean active, String updatedByName,
                     Instant updatedAt) {
    }

    record Stored(BankAccount account, String updatedByName, Instant updatedAt) {
    }

    record PendingRef(long batchId, String number) {
    }

    record BatchRow(BatchSummaryDto summary, int holdDays, String note, String statusReason, DispatchState dispatch) {
    }

    /** Envío del lote al proveedor de dispersión (campos nulos si nunca se envió). */
    record DispatchState(String provider, String status, String providerBatchId, String idempotencyKey, String detail,
                         Instant dispatchedAt) {
    }

    /** Lo que necesita cada pago para la dispersión: correo del vendedor e id de la entidad en el proveedor. */
    record DispatchRow(long itemId, long sellerId, String status, BigDecimal amount, String accountType, String keyType,
                       String accountNumber, String holderName, String holderDocType, String holderDocNumber, String email,
                       String providerBankId, String providerReference, String providerStatus) {
    }

    record BatchKey(long companyId, long batchId) {
    }
}
