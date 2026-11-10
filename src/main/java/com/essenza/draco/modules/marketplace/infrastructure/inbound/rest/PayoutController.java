package com.essenza.draco.modules.marketplace.infrastructure.inbound.rest;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.BankAccountDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.BankDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.ExportFile;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.FormatDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.FormatPreviewDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.PreviewDtoRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveFormatRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveBankRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.BatchDetailDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.BatchSummaryDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.CreateBatchRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.PayRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.PreviewDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.ReasonRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.SaveBankAccountRequest;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.BankMatchDto;
import com.essenza.draco.modules.marketplace.application.dto.PayoutDtos.ProviderBankDto;
import com.essenza.draco.modules.marketplace.application.service.PayoutDispatchService;
import com.essenza.draco.modules.marketplace.application.service.PayoutService;
import com.essenza.draco.shared.common.domain.dto.PageResponse;
import com.essenza.draco.shared.common.web.PageableFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Pagos a vendedores por lote y sus cuentas bancarias. Empresa y permisos desde el token. */
@RestController
@RequestMapping("/marketplace/payouts")
@Tag(name = "Payouts", description = "Liquidaciones a vendedores: lo cobrado por la tienda, retención, lotes y cuentas bancarias")
public class PayoutController {

    private static final Set<String> SORTABLE = Set.of("createdAt");

    private final PayoutService payouts;
    private final PayoutDispatchService dispatch;

    public PayoutController(PayoutService payouts, PayoutDispatchService dispatch) {
        this.payouts = payouts;
        this.dispatch = dispatch;
    }

    @Operation(summary = "Preview", description = "Vendedores a los que la tienda les debe: debido, retenido, pagable y cuenta. COMISIONES.VER.")
    @GetMapping("/preview")
    public PreviewDto preview() {
        return payouts.preview();
    }

    @Operation(summary = "Search", description = "Lotes (status: OPEN, CLOSED o CANCELLED). COMISIONES.VER.")
    @GetMapping("/search")
    public PageResponse<BatchSummaryDto> search(@RequestParam(value = "status", required = false) String status,
                                                @RequestParam(value = "page", required = false) Integer page,
                                                @RequestParam(value = "size", required = false) Integer size) {
        return PageResponse.from(payouts.search(status, PageableFactory.of(page, size, null, SORTABLE,
                Sort.by(Sort.Order.desc("createdAt")))));
    }

    @Operation(summary = "Create", description = "Lote con los vendedores elegidos (monto vacío = todo lo pagable). COMISIONES.GESTIONAR.")
    @PostMapping
    public ResponseEntity<BatchDetailDto> create(@RequestBody CreateBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payouts.create(request));
    }

    @Operation(summary = "Get", description = "Lote con sus pagos y acciones. COMISIONES.VER (cuentas enmascaradas sin GESTIONAR).")
    @GetMapping("/{id}")
    public BatchDetailDto get(@PathVariable long id) {
        return payouts.get(id);
    }

    @Operation(summary = "Pay all", description = "Marca pagados todos los pendientes (asienta SETTLEMENT). COMISIONES.GESTIONAR.")
    @PostMapping("/{id}/pay")
    public BatchDetailDto payAll(@PathVariable long id, @RequestBody PayRequest request) {
        return payouts.payAll(id, request);
    }

    @Operation(summary = "Pay item", description = "Marca pagado un pago del lote. COMISIONES.GESTIONAR.")
    @PostMapping("/{id}/items/{itemId}/pay")
    public BatchDetailDto pay(@PathVariable long id, @PathVariable long itemId, @RequestBody PayRequest request) {
        return payouts.pay(id, itemId, request);
    }

    @Operation(summary = "Reject item", description = "Pago rechazado o retirado del lote (no asienta nada). COMISIONES.GESTIONAR.")
    @PostMapping("/{id}/items/{itemId}/reject")
    public BatchDetailDto reject(@PathVariable long id, @PathVariable long itemId, @RequestBody ReasonRequest request) {
        return payouts.reject(id, itemId, request);
    }

    @Operation(summary = "Cancel", description = "Anula el lote si aún no tiene pagos hechos. COMISIONES.GESTIONAR.")
    @PostMapping("/{id}/cancel")
    public BatchDetailDto cancel(@PathVariable long id, @RequestBody ReasonRequest request) {
        return payouts.cancel(id, request);
    }

    @Operation(summary = "Export", description = "Archivo del lote para el banco pagador. format: id del formato (vacío o 0 = "
            + "genérico CSV); date: fecha de aplicación (vacía = hoy). COMISIONES.GESTIONAR.")
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable long id, @RequestParam(value = "format", required = false) Long format,
                                         @RequestParam(value = "date", required = false) java.time.LocalDate date) {
        ExportFile file = payouts.export(id, format, date);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(new MediaType("text", "csv".equals(file.extension()) ? "csv" : "plain",
                        java.nio.charset.Charset.forName(file.encoding())))
                .body(file.content());
    }

    @Operation(summary = "Formats", description = "Formatos del archivo de pagos: genérico + configurados. all=true incluye inactivos "
            + "(COMISIONES.GESTIONAR). COMISIONES.VER.")
    @GetMapping("/formats")
    public java.util.List<FormatDto> formats(@RequestParam(value = "all", required = false, defaultValue = "false") boolean all) {
        return payouts.formats(all);
    }

    @Operation(summary = "Create format", description = "Formato del banco pagador (delimitado o ancho fijo). COMISIONES.GESTIONAR.")
    @PostMapping("/formats")
    public ResponseEntity<FormatDto> createFormat(@RequestBody SaveFormatRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payouts.saveFormat(null, request));
    }

    @Operation(summary = "Update format", description = "COMISIONES.GESTIONAR.")
    @PutMapping("/formats/{formatId}")
    public FormatDto updateFormat(@PathVariable long formatId, @RequestBody SaveFormatRequest request) {
        return payouts.saveFormat(formatId, request);
    }

    @Operation(summary = "Preview format", description = "Muestra del archivo con pagos de ejemplo. COMISIONES.GESTIONAR.")
    @PostMapping("/formats/preview")
    public FormatPreviewDto previewFormat(@RequestBody PreviewDtoRequest request) {
        return payouts.previewFormat(request == null ? null : request.layout());
    }

    @Operation(summary = "Banks", description = "Catálogo de bancos, bancos digitales, billeteras y cooperativas. all=true incluye "
            + "las inactivas (COMISIONES.GESTIONAR). COMISIONES.VER.")
    @GetMapping("/banks")
    public java.util.List<BankDto> banks(@RequestParam(value = "all", required = false, defaultValue = "false") boolean all) {
        return payouts.banks(all);
    }

    @Operation(summary = "Create bank", description = "Agrega una entidad al catálogo. COMISIONES.GESTIONAR.")
    @PostMapping("/banks")
    public ResponseEntity<BankDto> createBank(@RequestBody SaveBankRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payouts.createBank(request));
    }

    @Operation(summary = "Update bank", description = "Nombre, tipo, código de banco, orden y activa. COMISIONES.GESTIONAR.")
    @PutMapping("/banks/{bankId}")
    public BankDto updateBank(@PathVariable long bankId, @RequestBody SaveBankRequest request) {
        return payouts.updateBank(bankId, request);
    }

    @Operation(summary = "Bank account", description = "Cuenta para pagarle al vendedor (204 si no tiene). COMISIONES.VER, enmascarada sin GESTIONAR.")
    @GetMapping("/sellers/{sellerId}/bank-account")
    public ResponseEntity<BankAccountDto> bankAccount(@PathVariable long sellerId) {
        BankAccountDto account = payouts.bankAccount(sellerId);
        return account == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(account);
    }

    @Operation(summary = "Save bank account", description = "Crea o reemplaza la cuenta del vendedor. COMISIONES.GESTIONAR.")
    @PutMapping("/sellers/{sellerId}/bank-account")
    public BankAccountDto saveBankAccount(@PathVariable long sellerId, @RequestBody SaveBankAccountRequest request) {
        return payouts.saveBankAccount(sellerId, request);
    }

    @Operation(summary = "Dispatch", description = "Envía los pagos pendientes del lote a la dispersión automática (Wompi Pagos a "
            + "terceros; sandbox salvo habilitación explícita). COMISIONES.GESTIONAR.")
    @PostMapping("/{id}/dispatch")
    public BatchDetailDto dispatch(@PathVariable long id) {
        return dispatch.dispatch(id);
    }

    @Operation(summary = "Sync", description = "Consulta en el proveedor el resultado de cada pago del lote: aprobado asienta la "
            + "liquidación; fallido queda rechazado. COMISIONES.GESTIONAR.")
    @PostMapping("/{id}/sync")
    public BatchDetailDto sync(@PathVariable long id) {
        return dispatch.sync(id);
    }

    @Operation(summary = "Provider banks", description = "Entidades del proveedor de dispersión y su entidad ligada. COMISIONES.GESTIONAR.")
    @GetMapping("/banks/provider")
    public java.util.List<ProviderBankDto> providerBanks() {
        return dispatch.providerBanks();
    }

    @Operation(summary = "Match provider banks", description = "Liga por código o nombre las entidades sin id del proveedor. "
            + "COMISIONES.GESTIONAR.")
    @PostMapping("/banks/provider/match")
    public BankMatchDto matchProviderBanks() {
        return dispatch.matchProviderBanks();
    }
}
