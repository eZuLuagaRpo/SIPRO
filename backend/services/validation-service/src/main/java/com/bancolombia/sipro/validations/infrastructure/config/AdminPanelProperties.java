package com.bancolombia.sipro.validations.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Agrupa la parametrización operativa del panel de administración.
 */
@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminPanelProperties {

    private final Sql sql = new Sql();
    private final Logs logs = new Logs();

    public Sql getSql() {
        return sql;
    }

    public Logs getLogs() {
        return logs;
    }

    public static class Sql {
        private int maxSelectRows = 200;
        private boolean requireWhereOnSelect = false;
        private boolean requireWhereOnUpdate = true;
        private List<String> enabledOperations = new ArrayList<>(List.of("SELECT", "UPDATE", "INSERT"));
        private List<String> allowedTables = new ArrayList<>(List.of(
                "data_validation_rule",
                "productos",
                "segmentos",
                "sipro_lz_catalogo_tablas",
                "sipro_lz_ingestion_run",
                "sipro_lz_ingestion_run_default",
                "sipro_lz_mdm_datos_generales_clientes",
                "sipro_lz_mdm_datos_generales_clientes_default",
                "sipro_lz_mdm_datos_generales_clientes_stg",
                "sipro_lz_mdm_datos_generales_clientes_stg_default",
                "sipro_parametros_columnas",
                "sipro_parametros_columnas_creffsos",
                "sipro_parametros_excepcionventanacarga",
                "sipro_parametros_homologacion_colgaap",
                "sipro_parametros_rango_habilitado",
                "sipro_parametros_reglaventanacarga",
                "sipro_parametros_tablas_lz",
                "sipro_parametros_unico",
                "sipro_resumen_por_moneda"
        ));
        private List<String> forbiddenTokens = new ArrayList<>(List.of(
                "delete",
                "truncate",
                "drop",
                "alter",
                "create",
                "grant",
                "revoke",
                "comment",
                "copy"
        ));

        public int getMaxSelectRows() {
            return maxSelectRows;
        }

        public void setMaxSelectRows(int maxSelectRows) {
            this.maxSelectRows = maxSelectRows;
        }

        public boolean isRequireWhereOnSelect() {
            return requireWhereOnSelect;
        }

        public void setRequireWhereOnSelect(boolean requireWhereOnSelect) {
            this.requireWhereOnSelect = requireWhereOnSelect;
        }

        public boolean isRequireWhereOnUpdate() {
            return requireWhereOnUpdate;
        }

        public void setRequireWhereOnUpdate(boolean requireWhereOnUpdate) {
            this.requireWhereOnUpdate = requireWhereOnUpdate;
        }

        public List<String> getEnabledOperations() {
            return enabledOperations;
        }

        public void setEnabledOperations(List<String> enabledOperations) {
            this.enabledOperations = enabledOperations != null ? enabledOperations : new ArrayList<>();
        }

        public List<String> getAllowedTables() {
            return allowedTables;
        }

        public void setAllowedTables(List<String> allowedTables) {
            this.allowedTables = allowedTables != null ? allowedTables : new ArrayList<>();
        }

        public List<String> getForbiddenTokens() {
            return forbiddenTokens;
        }

        public void setForbiddenTokens(List<String> forbiddenTokens) {
            this.forbiddenTokens = forbiddenTokens != null ? forbiddenTokens : new ArrayList<>();
        }

        public int getEffectiveMaxSelectRows() {
            return Math.max(1, maxSelectRows);
        }

        public Set<String> getEnabledOperationsNormalized() {
            return normalizeToSet(enabledOperations);
        }

        public Set<String> getAllowedTablesNormalized() {
            return normalizeToSet(allowedTables);
        }

        public List<String> getForbiddenTokensNormalized() {
            return forbiddenTokens.stream()
                    .filter(token -> token != null && !token.isBlank())
                    .map(token -> token.trim().toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();
        }
    }

    public static class Logs {
        private boolean streamingEnabled = true;
        private boolean downloadEnabled = true;
        private int maxBufferedEntries = 1000;
        private int defaultQueryLimit = 200;
        private int maxQueryLimit = 500;

        public boolean isStreamingEnabled() {
            return streamingEnabled;
        }

        public void setStreamingEnabled(boolean streamingEnabled) {
            this.streamingEnabled = streamingEnabled;
        }

        public boolean isDownloadEnabled() {
            return downloadEnabled;
        }

        public void setDownloadEnabled(boolean downloadEnabled) {
            this.downloadEnabled = downloadEnabled;
        }

        public int getMaxBufferedEntries() {
            return maxBufferedEntries;
        }

        public void setMaxBufferedEntries(int maxBufferedEntries) {
            this.maxBufferedEntries = maxBufferedEntries;
        }

        public int getDefaultQueryLimit() {
            return defaultQueryLimit;
        }

        public void setDefaultQueryLimit(int defaultQueryLimit) {
            this.defaultQueryLimit = defaultQueryLimit;
        }

        public int getMaxQueryLimit() {
            return maxQueryLimit;
        }

        public void setMaxQueryLimit(int maxQueryLimit) {
            this.maxQueryLimit = maxQueryLimit;
        }

        public int getEffectiveMaxBufferedEntries() {
            return Math.max(100, maxBufferedEntries);
        }

        public int getEffectiveDefaultQueryLimit() {
            return Math.max(1, defaultQueryLimit);
        }

        public int getEffectiveMaxQueryLimit() {
            return Math.max(getEffectiveDefaultQueryLimit(), maxQueryLimit);
        }
    }

    private static Set<String> normalizeToSet(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));
    }
}