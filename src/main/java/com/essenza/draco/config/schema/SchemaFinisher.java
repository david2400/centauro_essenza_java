package com.essenza.draco.config.schema;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import com.essenza.draco.config.schema.SchemaConstraints.ForeignKey;
import com.essenza.draco.config.schema.SchemaConstraints.NullableColumn;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Completa el esquema después de que Hibernate lo crea o actualiza ({@code ddl-auto}), con lo que JPA no hace:
 *
 * <ol>
 * <li>Llaves foráneas de {@link SchemaConstraints} que falten (se buscan por columna y tabla destino, no por nombre,
 * para no duplicar una que Hibernate ya creó con otro nombre).</li>
 * <li>Restricciones CHECK de las entidades ({@code @Table(check = ...)}) que falten en tablas existentes, o cuya lista de
 * valores cambió (p. ej. un tipo nuevo en un enum): {@code update} solo las crea con la tabla.</li>
 * <li>Columnas que pasaron a admitir NULL.</li>
 * </ol>
 *
 * <p>Solo agrega o amplía; nunca borra tablas, columnas ni datos. Si una llave no se puede crear por datos huérfanos,
 * o una CHECK por filas que no la cumplen, lo deja en el log y sigue: el arranque no falla por esto.
 *
 * <p>Con {@code ddl-auto=update} o {@code create} aplica los cambios; con {@code validate} o {@code none}
 * (producción) solo los informa. {@code essenza.schema.finisher.mode=apply|report|off} lo fuerza. Solo MySQL 8 y
 * MariaDB (en MySQL 5.7, sin CHECK, se omiten las CHECK).
 */
@Component
public class SchemaFinisher {

    private static final Logger log = LoggerFactory.getLogger(SchemaFinisher.class);
    private static final Pattern LITERAL = Pattern.compile("'((?:[^']|'')*)'");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]+");

    public enum Mode { APPLY, REPORT, OFF }

    /** Una CHECK declarada en una entidad. */
    public record CheckSpec(String table, String name, String clause) {
    }

    /** Lo que se hizo (o se haría, en modo informe) en una corrida. */
    public record Report(List<String> applied, List<String> pending, List<String> skipped) {

        boolean isEmpty() {
            return applied.isEmpty() && pending.isEmpty() && skipped.isEmpty();
        }
    }

    private final DataSource dataSource;
    private final Mode mode;
    private final String basePackage;
    private final AtomicBoolean done = new AtomicBoolean();

    public SchemaFinisher(DataSource dataSource,
            @Value("${essenza.schema.finisher.mode:}") String mode,
            @Value("${spring.jpa.hibernate.ddl-auto:none}") String ddlAuto) {
        this(dataSource, resolve(mode, ddlAuto), "com.essenza.draco");
    }

    SchemaFinisher(DataSource dataSource, Mode mode, String basePackage) {
        this.dataSource = dataSource;
        this.mode = mode;
        this.basePackage = basePackage;
    }

    static Mode resolve(String mode, String ddlAuto) {
        if (mode != null && !mode.isBlank()) {
            return Mode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        }
        String ddl = ddlAuto == null ? "" : ddlAuto.trim().toLowerCase(Locale.ROOT);
        return ddl.equals("update") || ddl.startsWith("create") ? Mode.APPLY : Mode.REPORT;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onStart() {
        if (mode == Mode.OFF || !done.compareAndSet(false, true)) {
            return;
        }
        try {
            Report report = finish(SchemaConstraints.foreignKeys(), checksOf(entityClasses()), SchemaConstraints.nullableColumns());
            if (report.isEmpty()) {
                log.info("Esquema completo: llaves foráneas, CHECK y columnas opcionales al día.");
                return;
            }
            report.applied().forEach(s -> log.info("Esquema: {}", s));
            report.pending().forEach(s -> log.warn("Esquema pendiente (modo {}): {}", mode, s));
            report.skipped().forEach(s -> log.warn("Esquema omitido: {}", s));
        } catch (RuntimeException | SQLException ex) {
            log.warn("No se pudo completar el esquema: {}", ex.getMessage());
        }
    }

    /** Una corrida idempotente sobre la base del {@link DataSource}. */
    Report finish(List<ForeignKey> foreignKeys, List<CheckSpec> checks, List<NullableColumn> nullable) throws SQLException {
        List<String> applied = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            DatabaseMetaData meta = connection.getMetaData();
            String product = meta.getDatabaseProductName().toLowerCase(Locale.ROOT);
            if (!product.contains("mysql") && !product.contains("mariadb")) {
                skipped.add("base " + meta.getDatabaseProductName() + ": solo se completa MySQL o MariaDB");
                return new Report(applied, pending, skipped);
            }
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        for (NullableColumn column : nullable) {
            relaxNotNull(jdbc, column, applied, pending, skipped);
        }
        for (ForeignKey fk : foreignKeys) {
            addForeignKey(jdbc, fk, applied, pending, skipped);
        }
        if (hasCheckCatalog(jdbc)) {
            for (CheckSpec check : checks) {
                syncCheck(jdbc, check, applied, pending, skipped);
            }
        } else if (!checks.isEmpty()) {
            skipped.add("la base no tiene information_schema.CHECK_CONSTRAINTS (MySQL 5.7): no aplica restricciones CHECK");
        }
        return new Report(List.copyOf(applied), List.copyOf(pending), List.copyOf(skipped));
    }

    private void relaxNotNull(JdbcTemplate jdbc, NullableColumn c, List<String> applied, List<String> pending,
            List<String> skipped) {
        List<java.util.Map<String, Object>> rows = jdbc.queryForList("SELECT IS_NULLABLE, COLUMN_TYPE, COLUMN_DEFAULT, EXTRA "
                + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                c.table(), c.column());
        if (rows.isEmpty() || !"NO".equals(rows.get(0).get("IS_NULLABLE"))) {
            return;
        }
        String extra = String.valueOf(rows.get(0).get("EXTRA")).toLowerCase(Locale.ROOT);
        if (extra.contains("auto_increment") || extra.contains("generated")) {
            skipped.add(c.table() + "." + c.column() + ": columna generada o autoincremental, no se cambia a NULL");
            return;
        }
        String type = String.valueOf(rows.get(0).get("COLUMN_TYPE"));
        Object def = rows.get(0).get("COLUMN_DEFAULT");
        String sql = "ALTER TABLE " + id(c.table()) + " MODIFY " + id(c.column()) + " " + type + " NULL"
                + (def == null || "NULL".equalsIgnoreCase(String.valueOf(def)) ? "" : " DEFAULT " + literal(def));
        run(jdbc, sql, c.table() + "." + c.column() + " admite NULL", applied, pending, skipped);
    }

    private void addForeignKey(JdbcTemplate jdbc, ForeignKey fk, List<String> applied, List<String> pending,
            List<String> skipped) {
        if (!columnExists(jdbc, fk.table(), fk.column()) || !columnExists(jdbc, fk.refTable(), fk.refColumn())) {
            return; // tabla de un módulo que esta base no tiene
        }
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? AND REFERENCED_TABLE_NAME = ?",
                Integer.class, fk.table(), fk.column(), fk.refTable());
        if (existing != null && existing > 0) {
            return;
        }
        Integer nameTaken = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = ?", Integer.class, fk.name());
        if (nameTaken != null && nameTaken > 0) {
            skipped.add(fk.name() + ": el nombre ya lo usa otra restricción");
            return;
        }
        Long orphans = jdbc.queryForObject("SELECT COUNT(*) FROM " + id(fk.table()) + " c LEFT JOIN " + id(fk.refTable())
                + " r ON r." + id(fk.refColumn()) + " = c." + id(fk.column()) + " WHERE c." + id(fk.column())
                + " IS NOT NULL AND r." + id(fk.refColumn()) + " IS NULL", Long.class);
        if (orphans != null && orphans > 0) {
            skipped.add(fk.name() + ": " + orphans + " filas de " + fk.table() + "." + fk.column() + " apuntan a "
                    + fk.refTable() + " inexistentes; corrígelas y reinicia");
            return;
        }
        run(jdbc, "ALTER TABLE " + id(fk.table()) + " ADD CONSTRAINT " + id(fk.name()) + " FOREIGN KEY (" + id(fk.column())
                + ") REFERENCES " + id(fk.refTable()) + " (" + id(fk.refColumn()) + ")",
                "llave " + fk.name() + " (" + fk.table() + "." + fk.column() + " → " + fk.refTable() + ")", applied, pending,
                skipped);
    }

    private void syncCheck(JdbcTemplate jdbc, CheckSpec check, List<String> applied, List<String> pending,
            List<String> skipped) {
        if (!tableExists(jdbc, check.table())) {
            return;
        }
        List<String> current = jdbc.queryForList("SELECT cc.CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS cc "
                + "WHERE cc.CONSTRAINT_SCHEMA = DATABASE() AND cc.CONSTRAINT_NAME = ?", String.class, check.name());
        String add = "ALTER TABLE " + id(check.table()) + " ADD CONSTRAINT " + id(check.name()) + " CHECK (" + check.clause() + ")";
        if (current.isEmpty() || current.get(0) == null) {
            run(jdbc, add, "CHECK " + check.name() + " en " + check.table(), applied, pending, skipped);
            return;
        }
        Set<String> wanted = literals(check.clause());
        Set<String> have = literals(current.get(0));
        if (wanted.isEmpty() || wanted.equals(have)) {
            return; // igual, o no comparable sin literales: no se toca
        }
        String what = "CHECK " + check.name() + " en " + check.table() + " (valores " + have + " → " + wanted + ")";
        if (mode != Mode.APPLY) {
            pending.add(what);
            return;
        }
        try {
            jdbc.execute("ALTER TABLE " + id(check.table()) + " DROP CONSTRAINT " + id(check.name()));
        } catch (RuntimeException ex) {
            skipped.add(what + ": " + rootMessage(ex));
            return;
        }
        try {
            jdbc.execute(add);
            applied.add(what);
        } catch (RuntimeException ex) {
            // Filas que no cumplen la nueva regla: se deja la anterior para no quedar sin CHECK.
            String restore = "ALTER TABLE " + id(check.table()) + " ADD CONSTRAINT " + id(check.name()) + " CHECK ("
                    + unescape(current.get(0)) + ")";
            try {
                jdbc.execute(restore);
            } catch (RuntimeException ignored) {
                // la CHECK anterior tampoco se pudo restaurar; queda en el aviso
            }
            skipped.add(what + ": " + rootMessage(ex));
        }
    }

    private void run(JdbcTemplate jdbc, String sql, String what, List<String> applied, List<String> pending,
            List<String> skipped) {
        if (mode != Mode.APPLY) {
            pending.add(what + " — " + sql);
            return;
        }
        try {
            jdbc.execute(sql);
            applied.add(what);
        } catch (RuntimeException ex) {
            skipped.add(what + ": " + rootMessage(ex));
        }
    }

    private static boolean hasCheckCatalog(JdbcTemplate jdbc) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = "
                + "'information_schema' AND TABLE_NAME = 'CHECK_CONSTRAINTS'", Integer.class);
        return n != null && n > 0;
    }

    private static boolean tableExists(JdbcTemplate jdbc, String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = ?", Integer.class, table);
        return n != null && n > 0;
    }

    private static boolean columnExists(JdbcTemplate jdbc, String table, String column) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = ? AND COLUMN_NAME = ?", Integer.class, table, column);
        return n != null && n > 0;
    }

    /** Valores entre comillas de una CHECK, sin importar cómo la reescribe la base (_utf8mb4'X', mayúsculas, espacios). */
    static Set<String> literals(String clause) {
        Set<String> values = new TreeSet<>();
        Matcher m = LITERAL.matcher(unescape(clause));
        while (m.find()) {
            values.add(m.group(1).replace("''", "'"));
        }
        return values;
    }

    /** MySQL 8 entrega la cláusula con las comillas escapadas ({@code _utf8mb4\\'X\\'}). */
    static String unescape(String clause) {
        return clause == null ? "" : clause.replace("\\'", "'");
    }

    /** CHECK declaradas en las entidades ({@code @Table(check = ...)}). */
    static List<CheckSpec> checksOf(Collection<Class<?>> entities) {
        List<CheckSpec> checks = new ArrayList<>();
        for (Class<?> type : entities) {
            Table table = type.getAnnotation(Table.class);
            if (table == null || table.name().isBlank()) {
                continue;
            }
            for (CheckConstraint check : table.check()) {
                if (!check.name().isBlank()) {
                    checks.add(new CheckSpec(table.name(), check.name(), check.constraint()));
                }
            }
        }
        checks.sort(java.util.Comparator.comparing(CheckSpec::table).thenComparing(CheckSpec::name));
        return checks;
    }

    private List<Class<?>> entityClasses() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> types = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
            try {
                types.add(Class.forName(candidate.getBeanClassName(), false, getClass().getClassLoader()));
            } catch (ClassNotFoundException ex) {
                log.debug("Entidad no cargada {}: {}", candidate.getBeanClassName(), ex.getMessage());
            }
        }
        return types;
    }

    /** Nombres de las listas de este paquete: solo letras, números y guion bajo. */
    private static String id(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Nombre de esquema no válido: " + name);
        }
        return "`" + name + "`";
    }

    private static String literal(Object value) {
        String text = String.valueOf(value);
        if (text.matches("-?\\d+(\\.\\d+)?") || text.equalsIgnoreCase("CURRENT_TIMESTAMP")) {
            return text;
        }
        if (text.startsWith("'") && text.endsWith("'")) {
            return text; // MariaDB ya lo entrega entre comillas
        }
        return "'" + text.replace("'", "''") + "'";
    }

    private static String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
