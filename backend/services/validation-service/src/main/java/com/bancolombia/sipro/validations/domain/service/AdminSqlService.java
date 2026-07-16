package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.AdminSqlExecuteRequest;
import com.bancolombia.sipro.validations.application.dto.AdminSqlExecuteResponse;
import com.bancolombia.sipro.validations.infrastructure.config.AdminPanelProperties;
import com.bancolombia.sipro.validations.infrastructure.security.SiproAuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consola SQL restringida para administración operativa.
 */
@Service
public class AdminSqlService {

    private static final Logger logger = LoggerFactory.getLogger(AdminSqlService.class);

    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?i)\\b(from|join|update|into)\\s+([a-zA-Z_][\\w.\"]*)");

    private final AdminPanelProperties adminPanelProperties;
    private final JdbcTemplate jdbcTemplate;

    public AdminSqlService(AdminPanelProperties adminPanelProperties, JdbcTemplate jdbcTemplate) {
        this.adminPanelProperties = adminPanelProperties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public AdminSqlExecuteResponse ejecutarLectura(AdminSqlExecuteRequest request, SiproAuthenticatedUser principal) {
        SqlOperation operation = resolverOperacion(request);
        if (operation != SqlOperation.SELECT) {
            throw new IllegalArgumentException("La operación solicitada no corresponde a una consulta de lectura.");
        }

        ValidatedSql validated = validar(request, operation);
        String effectiveSql = agregarLimiteSiHaceFalta(validated.sql());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(effectiveSql);
        List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());

        logger.info("ADMIN_SQL_SELECT usuario={} email={} tablas={} sql={} filas={} truncado={}",
                principal.idUsuario(), principal.email(), validated.tables(), effectiveSql, rows.size(),
                !effectiveSql.equals(validated.sql()));

        return new AdminSqlExecuteResponse(
                true,
                operation.apiName,
                rows.isEmpty()
                        ? "Consulta ejecutada sin registros para mostrar."
                        : "Consulta ejecutada correctamente.",
                null,
                columns,
                rows,
                effectiveSql,
                !effectiveSql.equals(validated.sql()));
    }

    @Transactional
    public AdminSqlExecuteResponse ejecutarEscritura(AdminSqlExecuteRequest request, SiproAuthenticatedUser principal) {
        SqlOperation operation = resolverOperacion(request);
        if (operation == SqlOperation.SELECT) {
            throw new IllegalArgumentException("Usa el flujo de lectura para consultas SELECT.");
        }

        if (operation == SqlOperation.INSERT_OVERWRITE) {
            throw new IllegalArgumentException(
                    "INSERT OVERWRITE no está soportado en PostgreSQL desde este panel. Usa INSERT INTO o UPDATE.");
        }

        ValidatedSql validated = validar(request, operation);
        int affectedRows = jdbcTemplate.update(validated.sql());

        logger.info("ADMIN_SQL_WRITE usuario={} email={} operacion={} tablas={} filasAfectadas={} justificacion={} sql={}",
                principal.idUsuario(), principal.email(), operation.apiName, validated.tables(), affectedRows,
                request.justificacion(), validated.sql());

        return new AdminSqlExecuteResponse(
                true,
                operation.apiName,
                "Operación ejecutada correctamente.",
                affectedRows,
                List.of(),
                List.of(),
                validated.sql(),
                false);
    }

    private ValidatedSql validar(AdminSqlExecuteRequest request, SqlOperation operation) {
        String sql = normalizarSql(request != null ? request.sql() : null);
        validarSintaxisSegura(sql);

        if (!sql.toLowerCase(Locale.ROOT).startsWith(operation.sqlPrefix)) {
            throw new IllegalArgumentException(
                    "La sentencia no coincide con la operación seleccionada: " + operation.apiName + ".");
        }

        validarOperacionHabilitada(operation);

        if (operation == SqlOperation.SELECT
                && adminPanelProperties.getSql().isRequireWhereOnSelect()
                && !contieneWhere(sql)) {
            throw new IllegalArgumentException("Esta operación requiere cláusula WHERE obligatoria.");
        }

        if (operation.requiresWhere && !contieneWhere(sql)) {
            throw new IllegalArgumentException("Esta operación requiere cláusula WHERE obligatoria.");
        }

        if (operation.requiresJustification
                && (request == null || request.justificacion() == null || request.justificacion().isBlank())) {
            throw new IllegalArgumentException("Debes diligenciar una justificación para operaciones que modifican datos.");
        }

        Set<String> tables = extraerTablas(sql);
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("No fue posible identificar la tabla objetivo de la consulta.");
        }

        Set<String> unauthorizedTables = new TreeSet<>();
        for (String table : tables) {
            if (!adminPanelProperties.getSql().getAllowedTablesNormalized().contains(table)) {
                unauthorizedTables.add(table);
            }
        }

        if (!unauthorizedTables.isEmpty()) {
            throw new IllegalArgumentException(
                    "Consulta fuera de alcance. Solo se permiten tablas maestras y métricas del panel admin. "
                            + "Tablas no permitidas: " + String.join(", ", unauthorizedTables));
        }

        return new ValidatedSql(sql, tables);
    }

    private void validarOperacionHabilitada(SqlOperation operation) {
        if (!adminPanelProperties.getSql().getEnabledOperationsNormalized()
                .contains(operation.apiName.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("La operación " + operation.apiName + " no está habilitada en este ambiente.");
        }
    }

    private SqlOperation resolverOperacion(AdminSqlExecuteRequest request) {
        String explicitOperation = request != null && request.tipoOperacion() != null
                ? request.tipoOperacion().trim().toUpperCase(Locale.ROOT)
                : "";

        if (!explicitOperation.isBlank()) {
            for (SqlOperation operation : SqlOperation.values()) {
                if (operation.apiName.equals(explicitOperation)) {
                    return operation;
                }
            }
        }

        String sql = request != null ? normalizarSql(request.sql()).toUpperCase(Locale.ROOT) : "";
        for (SqlOperation operation : SqlOperation.values()) {
            if (sql.startsWith(operation.sqlPrefix.toUpperCase(Locale.ROOT))) {
                return operation;
            }
        }

        throw new IllegalArgumentException("Tipo de operación no soportado. Usa SELECT, UPDATE o INSERT.");
    }

    private String normalizarSql(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new IllegalArgumentException("Debes ingresar una sentencia SQL.");
        }

        String normalized = rawSql.trim();
        while (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private void validarSintaxisSegura(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT);
        if (normalized.contains(";") || normalized.contains("--") || normalized.contains("/*") || normalized.contains("*/")) {
            throw new IllegalArgumentException("La consola solo acepta una sentencia por ejecución y sin comentarios embebidos.");
        }

        List<String> forbiddenTokens = List.of(
                " delete ", " truncate ", " drop ", " alter ", " create ", " grant ", " revoke ", " comment ", " copy ");

        List<String> configuredForbiddenTokens = adminPanelProperties.getSql().getForbiddenTokensNormalized();

        if (!configuredForbiddenTokens.isEmpty()) {
            forbiddenTokens = configuredForbiddenTokens.stream()
                    .map(token -> " " + token + " ")
                    .toList();
        }

        for (String token : forbiddenTokens) {
            if ((" " + normalized + " ").contains(token)) {
                throw new IllegalArgumentException("La consola admin no permite operaciones DDL o destructivas.");
            }
        }
    }

    private boolean contieneWhere(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT);
        return normalized.contains(" where ");
    }

    private String agregarLimiteSiHaceFalta(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT);
        if (normalized.contains(" limit ")) {
            return sql;
        }
        return sql + " LIMIT " + adminPanelProperties.getSql().getEffectiveMaxSelectRows();
    }

    private Set<String> extraerTablas(String sql) {
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        Set<String> tables = new LinkedHashSet<>();
        while (matcher.find()) {
            String tableToken = matcher.group(2);
            if (tableToken == null || tableToken.isBlank()) {
                continue;
            }
            tables.add(normalizarTabla(tableToken));
        }
        return tables;
    }

    private String normalizarTabla(String tableToken) {
        String cleaned = tableToken.replace("\"", "").trim();
        int dotIndex = cleaned.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < cleaned.length() - 1) {
            cleaned = cleaned.substring(dotIndex + 1);
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private record ValidatedSql(String sql, Set<String> tables) {
    }

    private enum SqlOperation {
        SELECT("SELECT", "select", false, false),
        UPDATE("UPDATE", "update", true, true),
        INSERT("INSERT", "insert into", false, true),
        INSERT_OVERWRITE("INSERT_OVERWRITE", "insert overwrite", false, true);

        private final String apiName;
        private final String sqlPrefix;
        private final boolean requiresWhere;
        private final boolean requiresJustification;

        SqlOperation(String apiName, String sqlPrefix, boolean requiresWhere, boolean requiresJustification) {
            this.apiName = apiName;
            this.sqlPrefix = sqlPrefix;
            this.requiresWhere = requiresWhere;
            this.requiresJustification = requiresJustification;
        }
    }
}