package com.essenza.draco.modules.billing.infrastructure.outbound.repositories;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.essenza.draco.modules.billing.application.output.repository.InvoiceSourceStore;

/** Origen de las facturas de servicios (JDBC, misma transacción que la factura). */
@Repository
public class InvoiceSourceJdbcStore implements InvoiceSourceStore {

    private final NamedParameterJdbcTemplate jdbc;

    public InvoiceSourceJdbcStore(NamedParameterJdbcTemplate marketplaceJdbc) {
        this.jdbc = marketplaceJdbc;
    }

    @Override
    public void link(long invoiceId, String sourceType, String sourceKey) {
        jdbc.update("INSERT INTO invoice_sources (invoice_id, source_type, source_key) VALUES (:id, :type, :key)",
                new MapSqlParameterSource("id", invoiceId).addValue("type", sourceType).addValue("key", sourceKey));
    }

    @Override
    public List<Long> invoicesOf(String sourceType, String sourceKey) {
        return jdbc.queryForList("""
                SELECT invoice_id FROM invoice_sources WHERE source_type = :type AND source_key = :key ORDER BY invoice_id DESC""",
                new MapSqlParameterSource("type", sourceType).addValue("key", sourceKey), Long.class);
    }
}
