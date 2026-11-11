package com.essenza.draco.config.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.essenza.draco.config.schema.SchemaConstraints.ForeignKey;
import com.essenza.draco.config.schema.SchemaConstraints.NullableColumn;
import com.essenza.draco.config.schema.SchemaFinisher.CheckSpec;
import com.essenza.draco.config.schema.SchemaFinisher.Mode;
import com.essenza.draco.config.schema.SchemaFinisher.Report;

/**
 * Contra una base de pruebas real (MySQL 8 o MariaDB, nombre terminado en {@code _test}); crea y borra sus propias
 * tablas {@code sf_*}.
 */
@EnabledIfEnvironmentVariable(named = "STOREFRONT_IT_DB_URL", matches = "jdbc:.*_test(\\?.*)?")
class SchemaFinisherIT {

    private static final List<ForeignKey> FKS = List.of(
            new ForeignKey("fk_sf_child_parent", "sf_child", "parent_id", "sf_parent", "id_parent"),
            new ForeignKey("fk_sf_child_other", "sf_child", "other_id", "sf_parent", "id_parent"),
            new ForeignKey("fk_sf_missing_table", "sf_nope", "parent_id", "sf_parent", "id_parent"));
    private static final List<CheckSpec> CHECKS = List.of(
            new CheckSpec("sf_child", "ck_sf_child_kind", "kind IN ('A', 'B', 'C')"),
            new CheckSpec("sf_child", "ck_sf_child_amount", "amount >= 0"));
    private static final List<NullableColumn> NULLABLE = List.of(new NullableColumn("sf_child", "note"));

    private DriverManagerDataSource ds;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        ds = new DriverManagerDataSource(System.getenv("STOREFRONT_IT_DB_URL"), System.getenv("MARKETPLACE_IT_DB_USER"),
                System.getenv().getOrDefault("MARKETPLACE_IT_DB_PASSWORD", ""));
        jdbc = new JdbcTemplate(ds);
        drop();
        jdbc.execute("CREATE TABLE sf_parent (id_parent BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE sf_child (id BIGINT PRIMARY KEY, parent_id BIGINT NULL, other_id BIGINT NULL, "
                + "kind VARCHAR(5) NOT NULL, amount DECIMAL(10,2) NOT NULL DEFAULT 0, note VARCHAR(20) NOT NULL DEFAULT 'x', "
                + "CONSTRAINT ck_sf_child_kind CHECK (kind IN ('A', 'B')))");
        jdbc.update("INSERT INTO sf_parent VALUES (1)");
        jdbc.update("INSERT INTO sf_child (id, parent_id, other_id, kind) VALUES (1, 1, 99, 'A')"); // other_id huérfano
    }

    @AfterEach
    void drop() {
        jdbc.execute("DROP TABLE IF EXISTS sf_child");
        jdbc.execute("DROP TABLE IF EXISTS sf_parent");
    }

    @Test
    void addsWhatIsMissingAndSkipsOrphansWithoutFailing() throws Exception {
        Report report = new SchemaFinisher(ds, Mode.APPLY, "none").finish(FKS, CHECKS, NULLABLE);

        assertThat(fkCount("sf_child", "parent_id")).isEqualTo(1);
        assertThat(fkCount("sf_child", "other_id")).isZero();
        assertThat(report.skipped()).anySatisfy(s -> assertThat(s).startsWith("fk_sf_child_other: 1 filas"));
        // CHECK ampliada: ahora acepta C y sigue rechazando D.
        jdbc.update("INSERT INTO sf_child (id, kind) VALUES (2, 'C')");
        assertThat(rejects("INSERT INTO sf_child (id, kind) VALUES (3, 'D')")).isTrue();
        // CHECK nueva en tabla existente.
        assertThat(rejects("INSERT INTO sf_child (id, kind, amount) VALUES (4, 'A', -1)")).isTrue();
        // Columna opcional, conservando su valor por defecto.
        jdbc.update("INSERT INTO sf_child (id, kind, note) VALUES (5, 'A', NULL)");
        jdbc.update("INSERT INTO sf_child (id, kind) VALUES (6, 'A')");
        assertThat(jdbc.queryForObject("SELECT note FROM sf_child WHERE id = 6", String.class)).isEqualTo("x");
        assertThat(report.applied()).hasSize(4);
    }

    @Test
    void aSecondRunChangesNothing() throws Exception {
        SchemaFinisher finisher = new SchemaFinisher(ds, Mode.APPLY, "none");
        finisher.finish(FKS, CHECKS, NULLABLE);

        Report again = finisher.finish(FKS, CHECKS, NULLABLE);

        assertThat(again.applied()).isEmpty();
        assertThat(again.pending()).isEmpty();
        assertThat(again.skipped()).allSatisfy(s -> assertThat(s).startsWith("fk_sf_child_other")); // el huérfano sigue
    }

    @Test
    void reportModeOnlyTells() throws Exception {
        Report report = new SchemaFinisher(ds, Mode.REPORT, "none").finish(FKS, CHECKS, NULLABLE);

        assertThat(report.applied()).isEmpty();
        assertThat(report.pending()).hasSize(4);
        assertThat(fkCount("sf_child", "parent_id")).isZero();
        jdbc.update("INSERT INTO sf_child (id, kind, amount) VALUES (7, 'A', -1)"); // sin CHECK nueva todavía
    }

    @Test
    void aStricterCheckThatRowsBreakKeepsThePreviousOne() throws Exception {
        List<CheckSpec> stricter = List.of(new CheckSpec("sf_child", "ck_sf_child_kind", "kind IN ('B')"));

        Report report = new SchemaFinisher(ds, Mode.APPLY, "none").finish(List.of(), stricter, List.of());

        assertThat(report.skipped()).singleElement().satisfies(s -> assertThat(s).startsWith("CHECK ck_sf_child_kind"));
        assertThat(rejects("INSERT INTO sf_child (id, kind) VALUES (8, 'Z')")).isTrue(); // la anterior sigue
    }

    private int fkCount(String table, String column) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = ? AND COLUMN_NAME = ? AND REFERENCED_TABLE_NAME IS NOT NULL", Integer.class, table, column);
    }

    private boolean rejects(String sql) {
        try {
            jdbc.update(sql);
            return false;
        } catch (RuntimeException ex) {
            return true;
        }
    }
}
