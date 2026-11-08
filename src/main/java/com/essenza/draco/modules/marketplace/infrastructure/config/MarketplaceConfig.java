package com.essenza.draco.modules.marketplace.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Beans del marketplace. El JDBC usa el mismo DataSource que JPA, así que participa de las
 * mismas transacciones ({@code @Transactional}).
 */
@Configuration
public class MarketplaceConfig {

    /** Fechas de negocio (vigencias, ciclos) en la zona de la operación. */
    @Bean
    public Clock marketplaceClock(@Value("${essenza.marketplace.zone:America/Bogota}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    @Bean
    public NamedParameterJdbcTemplate marketplaceJdbc(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }
}
