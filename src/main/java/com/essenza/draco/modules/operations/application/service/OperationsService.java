package com.essenza.draco.modules.operations.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.essenza.draco.modules.operations.application.dto.AlertDtos.AlertDto;
import com.essenza.draco.modules.operations.application.dto.AlertDtos.AlertsDto;
import com.essenza.draco.modules.operations.application.port.OperationsReadModel;
import com.essenza.draco.modules.operations.application.port.OperationsReadModel.AlertData;
import com.essenza.draco.modules.operations.domain.AlertCatalog;
import com.essenza.draco.modules.operations.domain.AlertCatalog.Definition;
import com.essenza.draco.modules.operations.domain.AlertCatalog.Severity;
import com.essenza.draco.modules.operations.domain.AlertCatalog.Thresholds;
import com.essenza.draco.shared.exceptions.ForbiddenException;
import com.essenza.draco.shared.security.ActorProvider;
import com.essenza.draco.shared.security.RequestActor;

/**
 * Centro de operaciones: lo que necesita atención hoy en cada módulo. Cada alerta se calcula solo
 * si el usuario puede ver ese módulo; si una consulta falla, esa alerta queda como no disponible
 * y las demás siguen.
 */
@Service
@Transactional(readOnly = true)
public class OperationsService {

    private static final Logger log = LoggerFactory.getLogger(OperationsService.class);
    static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private final OperationsReadModel readModel;
    private final ActorProvider actors;
    private final Thresholds thresholds;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public OperationsService(OperationsReadModel readModel, ActorProvider actors,
                             @Value("${essenza.operations.consignment-aged-days:90}") int consignmentAgedDays,
                             @Value("${essenza.operations.returns-stalled-days:5}") int returnsStalledDays,
                             @Value("${essenza.operations.invoices-pending-days:1}") int invoicesPendingDays) {
        this(readModel, actors, new Thresholds(consignmentAgedDays, returnsStalledDays, invoicesPendingDays), Clock.system(ZONE));
    }

    /** Para pruebas: umbrales y reloj fijos. */
    public OperationsService(OperationsReadModel readModel, ActorProvider actors, Thresholds thresholds, Clock clock) {
        this.readModel = readModel;
        this.actors = actors;
        this.thresholds = thresholds;
        this.clock = clock;
    }

    public AlertsDto alerts() {
        RequestActor actor = actors.current();
        LocalDate today = LocalDate.now(clock);
        List<AlertDto> alerts = new ArrayList<>();
        long critical = 0;
        long warning = 0;
        long info = 0;
        boolean anyVisible = false;
        for (Definition definition : AlertCatalog.ALL) {
            if (definition.permissions().stream().noneMatch(actor::has)) {
                continue;
            }
            anyVisible = true;
            AlertDto alert;
            try {
                AlertData data = readModel.compute(definition.key(), actor.companyId(), today, thresholds);
                alert = new AlertDto(definition.key(), definition.area().name(), definition.severity().name(), data.count(),
                        data.amount(), true, data.items());
            } catch (RuntimeException ex) {
                log.warn("Alerta {} no disponible: {}", definition.key(), ex.getMessage());
                alert = new AlertDto(definition.key(), definition.area().name(), definition.severity().name(), 0, null, false,
                        List.of());
            }
            if (alert.count() > 0) {
                if (definition.severity() == Severity.CRITICAL) {
                    critical += alert.count();
                } else if (definition.severity() == Severity.WARNING) {
                    warning += alert.count();
                } else {
                    info += alert.count();
                }
            }
            alerts.add(alert);
        }
        if (!anyVisible) {
            throw new ForbiddenException("No tienes acceso a ningún módulo del centro de operaciones.");
        }
        return new AlertsDto(clock.instant(), critical, warning, info, thresholds.consignmentAgedDays(),
                thresholds.returnsStalledDays(), alerts);
    }
}
