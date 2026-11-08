package com.essenza.draco.modules.marketplace.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.marketplace.application.MarketplacePermissions;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.AdjustmentRequest;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.FeeRunDto;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.LedgerEntryDto;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.SellerAccountDto;
import com.essenza.draco.modules.marketplace.application.dto.BillingDtos.SellerPaymentRequest;
import com.essenza.draco.modules.marketplace.application.port.AssignmentStore;
import com.essenza.draco.modules.marketplace.application.port.CommissionStore;
import com.essenza.draco.modules.marketplace.application.port.CommissionStore.Totals;
import com.essenza.draco.modules.marketplace.application.port.SellerLedgerStore;
import com.essenza.draco.modules.marketplace.application.port.SellerLedgerStore.LedgerRow;
import com.essenza.draco.modules.marketplace.application.port.SellerLedgerStore.NewEntry;
import com.essenza.draco.modules.marketplace.application.port.SellerStore;
import com.essenza.draco.modules.marketplace.application.port.SellerStore.SellerRow;
import com.essenza.draco.modules.marketplace.application.service.AgreedTerms.InForce;
import com.essenza.draco.modules.marketplace.domain.MarketplaceRuleViolation;
import com.essenza.draco.modules.marketplace.domain.billing.FeeCycle;
import com.essenza.draco.modules.marketplace.domain.billing.SellerStatement;
import com.essenza.draco.modules.marketplace.domain.billing.SellerStatement.OpenCharge;
import com.essenza.draco.modules.marketplace.domain.plan.Money;
import com.essenza.draco.shared.exceptions.ConflictException;
import com.essenza.draco.shared.exceptions.NotFoundException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Estado de cuenta del vendedor con la plataforma: cargos (comisiones exigibles, mensualidades),
 * abonos (reversos, pagos recibidos) y ajustes con motivo. Nada se cobra solo: los pagos los
 * registra una persona hasta que exista una pasarela.
 */
@Service
@Transactional
public class SellerAccountService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");

    private final SellerStore sellers;
    private final SellerLedgerStore ledger;
    private final CommissionStore commissions;
    private final AssignmentStore assignments;
    private final ActorProvider actors;
    private final Clock clock;
    private final ConsignmentService consignment;

    /** Para pruebas sin consignación. */
    public SellerAccountService(SellerStore sellers, SellerLedgerStore ledger, CommissionStore commissions,
                                AssignmentStore assignments, ActorProvider actors, Clock marketplaceClock) {
        this(sellers, ledger, commissions, assignments, actors, marketplaceClock, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SellerAccountService(SellerStore sellers, SellerLedgerStore ledger, CommissionStore commissions,
                                AssignmentStore assignments, ActorProvider actors, Clock marketplaceClock,
                                ConsignmentService consignment) {
        this.sellers = sellers;
        this.ledger = ledger;
        this.commissions = commissions;
        this.assignments = assignments;
        this.actors = actors;
        this.clock = marketplaceClock;
        this.consignment = consignment;
    }

    @Transactional(readOnly = true)
    public SellerAccountDto statement(long sellerId, LocalDate asOf) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_VIEW);
        SellerRow seller = seller(actor, sellerId, false);
        LocalDate date = asOf == null ? LocalDate.now(clock) : asOf;
        List<LedgerRow> rows = ledger.entries(actor.companyId(), sellerId);
        String currency = rows.isEmpty() ? "COP" : rows.get(0).currency();
        SellerStatement.Result result = SellerStatement.of(currency, rows.stream()
                .map(r -> new SellerStatement.Entry(r.id(), r.amount(), r.entryDate(), r.dueOn(), r.interestMonthlyPct()))
                .toList(), date);
        Map<Long, OpenCharge> open = new HashMap<>();
        result.open().forEach(charge -> open.put(charge.entryId(), charge));
        Totals pending = commissions.totalsByStatus(actor.companyId(), sellerId).getOrDefault("PENDING",
                new Totals(0, BigDecimal.ZERO));
        List<LedgerEntryDto> entries = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            LedgerRow r = rows.get(i);
            OpenCharge charge = open.get(r.id());
            entries.add(new LedgerEntryDto(r.id(), r.type(), r.amount(), r.currency(), r.entryDate(), r.dueOn(),
                    charge == null ? BigDecimal.ZERO : charge.open(), charge == null ? 0 : charge.daysOverdue(), r.reference(),
                    r.commissionId(), r.feeCycle(), r.note(), r.createdByName(), r.createdAt()));
        }
        return new SellerAccountDto(seller.id(), seller.profile().tradeName() != null ? seller.profile().tradeName() : seller.profile().legalName(),
                currency, date, result.charges(), result.credits(), result.balance(), result.overdue(), result.lateInterest(),
                result.unappliedCredit(), pending.amount(), entries);
    }

    /** Pago recibido del vendedor (transferencia, consignación…). Lo registra una persona. */
    public SellerAccountDto registerPayment(long sellerId, SellerPaymentRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        seller(actor, sellerId, true);
        String currency = currencyOf(actor, sellerId);
        BigDecimal amount = Money.amount(request.amount(), currency, "El valor del pago");
        MarketplaceRuleViolation.check(amount.signum() > 0, "El valor del pago debe ser mayor que cero.");
        MarketplaceRuleViolation.check(amount.compareTo(MAX_AMOUNT) < 0, "El valor del pago es demasiado alto.");
        LocalDate date = dateOf(request.date());
        String reference = clip(request.reference(), 80);
        MarketplaceRuleViolation.check(reference != null, "Indica la referencia del pago (comprobante o transacción).");
        boolean posted = ledger.post(actor.companyId(), new NewEntry(sellerId, "PAYMENT", amount.negate(), currency, date, null,
                BigDecimal.ZERO, reference, null, null, "PAY:" + key(request.key()), clip(request.note(), 500)),
                actor.userId(), actor.displayName(), clock.instant());
        if (!posted) {
            throw new ConflictException("Ese pago ya estaba registrado.");
        }
        return statement(sellerId, null);
    }

    /**
     * Liquidación: la tienda le paga al vendedor lo que le debe (ventas en consignación cobradas
     * por la tienda, menos comisiones y cargos). Solo hasta el saldo a favor del vendedor.
     */
    public SellerAccountDto registerSettlement(long sellerId, SellerPaymentRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        seller(actor, sellerId, true);
        String currency = currencyOf(actor, sellerId);
        BigDecimal amount = Money.amount(request.amount(), currency, "El valor de la liquidación");
        MarketplaceRuleViolation.check(amount.signum() > 0, "El valor de la liquidación debe ser mayor que cero.");
        BigDecimal balance = ledger.balances(actor.companyId()).getOrDefault(sellerId, BigDecimal.ZERO);
        BigDecimal payable = balance.signum() < 0 ? balance.negate() : BigDecimal.ZERO;
        MarketplaceRuleViolation.check(amount.compareTo(payable) <= 0, "Al vendedor se le deben "
                + payable.stripTrailingZeros().toPlainString() + " " + currency + ": no se puede liquidar más.");
        LocalDate date = dateOf(request.date());
        String reference = clip(request.reference(), 80);
        MarketplaceRuleViolation.check(reference != null, "Indica la referencia de la liquidación (comprobante o transferencia).");
        boolean posted = ledger.post(actor.companyId(), new NewEntry(sellerId, "SETTLEMENT", amount, currency, date, null,
                BigDecimal.ZERO, reference, null, null, "SET:" + key(request.key()), clip(request.note(), 500)),
                actor.userId(), actor.displayName(), clock.instant());
        if (!posted) {
            throw new ConflictException("Esa liquidación ya estaba registrada.");
        }
        return statement(sellerId, null);
    }

    /** Ajuste manual con motivo: positivo suma a la deuda, negativo la reduce. */
    public SellerAccountDto adjust(long sellerId, AdjustmentRequest request) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_ADJUST);
        seller(actor, sellerId, true);
        String currency = currencyOf(actor, sellerId);
        MarketplaceRuleViolation.check(request.amount() != null && request.amount().signum() != 0,
                "El ajuste debe ser distinto de cero.");
        BigDecimal amount = Money.amount(request.amount().abs(), currency, "El valor del ajuste");
        MarketplaceRuleViolation.check(amount.compareTo(MAX_AMOUNT) < 0, "El valor del ajuste es demasiado alto.");
        String reason = clip(request.reason(), 500);
        MarketplaceRuleViolation.check(reason != null && reason.length() >= 5, "Explica el motivo del ajuste (mínimo 5 caracteres).");
        LocalDate date = dateOf(request.date());
        BigDecimal signed = request.amount().signum() < 0 ? amount.negate() : amount;
        if (!ledger.post(actor.companyId(), new NewEntry(sellerId, "ADJUSTMENT", signed, currency, date,
                signed.signum() > 0 ? date : null, BigDecimal.ZERO, null, null, null, "ADJ:" + key(request.key()), reason),
                actor.userId(), actor.displayName(), clock.instant())) {
            throw new ConflictException("Ese ajuste ya estaba registrado.");
        }
        return statement(sellerId, null);
    }

    /**
     * Mensualidades del ciclo para los vendedores externos de la empresa. Cobra la vigencia en curso
     * el día de cobro (día 1 por adelantado, último día vencido). Repetir no duplica: un cobro por
     * vendedor y ciclo.
     */
    public FeeRunDto generateFees(String cycleText) {
        RequestActor actor = actors.current().require(MarketplacePermissions.BILLING_MANAGE);
        YearMonth cycle = FeeCycle.parse(cycleText);
        LocalDate today = LocalDate.now(clock);
        MarketplaceRuleViolation.check(!cycle.atDay(1).isAfter(today), "No se generan mensualidades de ciclos futuros.");
        int created = 0;
        int existing = 0;
        int withoutFee = 0;
        BigDecimal total = BigDecimal.ZERO;
        List<String> notYet = new ArrayList<>();
        Instant now = clock.instant();
        for (Long sellerId : ledger.billableSellers(actor.companyId())) {
            Optional<InForce> charging = chargingAssignment(actor.companyId(), sellerId, cycle);
            if (charging.isEmpty()) {
                withoutFee++;
                continue;
            }
            var terms = charging.get().terms();
            LocalDate chargeDate = FeeCycle.chargeDate(cycle, terms.feeBilling());
            if (!FeeCycle.chargeable(cycle, terms.feeBilling(), today)) {
                notYet.add(String.valueOf(sellerId));
                continue;
            }
            boolean posted = ledger.post(actor.companyId(), new NewEntry(sellerId, "MONTHLY_FEE", terms.monthlyFee(),
                    terms.currency(), chargeDate, chargeDate.plusDays(terms.graceDays()), terms.lateInterestMonthlyPercent(),
                    charging.get().assignment().planCode() + " " + cycle, null, cycle.toString(),
                    FeeCycle.key(sellerId, cycle), "Mensualidad " + cycle), actor.userId(), actor.displayName(), now);
            if (posted) {
                created++;
                total = total.add(terms.monthlyFee());
            } else {
                existing++;
            }
        }
        // Almacenamiento de la consignación: solo ciclos cerrados (se cuentan los días completos).
        int storageCreated = 0;
        BigDecimal storageTotal = BigDecimal.ZERO;
        if (consignment != null && cycle.atEndOfMonth().isBefore(today)) {
            for (Long sellerId : ledger.billableSellers(actor.companyId())) {
                Optional<InForce> inForce = AgreedTerms.on(assignments, actor.companyId(), sellerId, cycle.atEndOfMonth());
                BigDecimal rate = inForce.map(t -> t.terms().storageFeePerUnitDay()).orElse(null);
                if (rate == null || rate.signum() <= 0) {
                    continue;
                }
                var terms = inForce.get().terms();
                long unitDays = consignment.unitDays(actor.companyId(), sellerId, cycle.atDay(1), cycle.atEndOfMonth());
                BigDecimal amount = com.essenza.draco.modules.marketplace.domain.consignment.StorageCharge.amount(unitDays, rate,
                        terms.currency());
                if (amount.signum() <= 0) {
                    continue;
                }
                LocalDate chargeDate = cycle.atEndOfMonth();
                boolean posted = ledger.post(actor.companyId(), new NewEntry(sellerId, "STORAGE_FEE", amount, terms.currency(),
                        chargeDate, chargeDate.plusDays(terms.graceDays()), terms.lateInterestMonthlyPercent(),
                        "Almacenamiento " + cycle, null, cycle.toString(), "STORAGE:" + sellerId + ":" + cycle,
                        unitDays + " unidades-día × " + rate.stripTrailingZeros().toPlainString()), actor.userId(),
                        actor.displayName(), now);
                if (posted) {
                    storageCreated++;
                    storageTotal = storageTotal.add(amount);
                }
            }
        }
        return new FeeRunDto(cycle.toString(), created, existing, withoutFee, total, notYet, storageCreated, storageTotal);
    }

    /** Vigencia que cobra el ciclo: la que está en curso en su propio día de cobro. */
    private Optional<InForce> chargingAssignment(long companyId, long sellerId, YearMonth cycle) {
        for (LocalDate candidate : List.of(cycle.atDay(1), cycle.atEndOfMonth())) {
            Optional<InForce> inForce = AgreedTerms.on(assignments, companyId, sellerId, candidate);
            if (inForce.isPresent() && inForce.get().terms().modality().chargesMonthlyFee()
                    && inForce.get().terms().monthlyFee().signum() > 0
                    && FeeCycle.chargeDate(cycle, inForce.get().terms().feeBilling()).equals(candidate)) {
                return inForce;
            }
        }
        return Optional.empty();
    }

    private SellerRow seller(RequestActor actor, long sellerId, boolean forUpdate) {
        SellerRow seller = sellers.find(actor.companyId(), sellerId, forUpdate)
                .orElseThrow(() -> new NotFoundException("No se encontró el vendedor " + sellerId + "."));
        MarketplaceRuleViolation.check("EXTERNAL".equals(seller.profile().kind().name()),
                "El vendedor propio no tiene estado de cuenta con la plataforma.");
        return seller;
    }

    private String currencyOf(RequestActor actor, long sellerId) {
        List<LedgerRow> rows = ledger.entries(actor.companyId(), sellerId);
        if (!rows.isEmpty()) {
            return rows.get(0).currency();
        }
        return AgreedTerms.on(assignments, actor.companyId(), sellerId, LocalDate.now(clock))
                .map(t -> t.terms().currency()).orElse("COP");
    }

    private LocalDate dateOf(LocalDate value) {
        LocalDate today = LocalDate.now(clock);
        LocalDate date = value == null ? today : value;
        MarketplaceRuleViolation.check(!date.isAfter(today), "La fecha no puede ser futura.");
        MarketplaceRuleViolation.check(!date.isBefore(today.minusYears(2)), "La fecha es demasiado antigua.");
        return date;
    }

    /** Clave del cliente (para no duplicar con doble clic) o una nueva si no envía. */
    private static String key(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String clean = value.trim();
        MarketplaceRuleViolation.check(clean.matches("[A-Za-z0-9-]{8,64}"), "Clave de operación no válida.");
        return clean;
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        return text.length() > max ? text.substring(0, max) : text;
    }
}
