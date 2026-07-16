package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.CreffosColumnDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Lee la parametrización activa de columnas CREFFSOS y resuelve catálogos auxiliares.
 */
@Repository
public class CreffosParametroColumnasRepository {

    private static final Logger logger = LoggerFactory.getLogger(CreffosParametroColumnasRepository.class);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final String SELECT_DEFINITIONS = """
            SELECT
                nombre_columna,
                orden,
                tipo_dato_salida,
                longitud_maxima,
                precision_numerica,
                escala_numerica,
                formato_salida,
                origen_dato,
                tabla_origen,
                columna_origen,
                alias_origen,
                valor_constante,
                funcion_java,
                expresion_sql,
                parametros_json,
                obligatorio,
                permite_nulo,
                incluir_salida,
                estado,
                descripcion,
                observaciones
            FROM public.sipro_parametros_columnas_creffsos
            WHERE estado = 1
              AND incluir_salida = true
            ORDER BY orden ASC
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CreffosParametroColumnasRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                              ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Carga la definición activa y ordenada de columnas para la salida CREFFSOS.
     */
    public List<CreffosColumnDefinition> findActiveDefinitions() {
        return jdbcTemplate.getJdbcTemplate().query(SELECT_DEFINITIONS, (rs, rowNum) -> new CreffosColumnDefinition(
                rs.getString("nombre_columna"),
                rs.getInt("orden"),
                rs.getString("tipo_dato_salida"),
                (Integer) rs.getObject("longitud_maxima"),
                (Integer) rs.getObject("precision_numerica"),
                (Integer) rs.getObject("escala_numerica"),
                rs.getString("formato_salida"),
                rs.getString("origen_dato"),
                rs.getString("tabla_origen"),
                rs.getString("columna_origen"),
                rs.getString("alias_origen"),
                rs.getString("valor_constante"),
                rs.getString("funcion_java"),
                rs.getString("expresion_sql"),
                parseJson(rs.getString("parametros_json")),
                rs.getBoolean("obligatorio"),
                rs.getBoolean("permite_nulo"),
                rs.getBoolean("incluir_salida"),
                rs.getShort("estado"),
                rs.getString("descripcion"),
                rs.getString("observaciones")
        ));
    }

    /**
     * Resuelve valores de lookup para las llaves solicitadas en una tabla configurada.
     */
    public Map<String, String> findLookupValues(String rawTableName,
                                                String rawLookupKey,
                                                String rawLookupValue,
                                                Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }

        QualifiedTable table = QualifiedTable.parse(rawTableName);
        if (!tableExists(table.schema(), table.table())) {
            logger.warn("Tabla de lookup {}.{} no existe. Se omite resolución paramétrica.",
                    table.schema(), table.table());
            return Map.of();
        }

        String lookupKey = quoteIdentifier(rawLookupKey);
        String lookupValue = quoteIdentifier(rawLookupValue);
        String qualifiedTable = table.qualifiedName();

        String sql = """
                SELECT CAST(%s AS text) AS lookup_key,
                       MAX(CAST(%s AS text)) AS lookup_value
                FROM %s
                WHERE CAST(%s AS text) IN (:keys)
                GROUP BY CAST(%s AS text)
                """.formatted(lookupKey, lookupValue, qualifiedTable, lookupKey, lookupKey);

        MapSqlParameterSource params = new MapSqlParameterSource("keys", keys);
        Map<String, String> values = new LinkedHashMap<>();
    jdbcTemplate.query(sql, params, (rs, rowNum) -> Map.entry(
        rs.getString("lookup_key"),
        rs.getString("lookup_value")
    )).forEach(entry -> values.put(entry.getKey(), entry.getValue()));
        return values;
    }

    private boolean tableExists(String schema, String table) {
        Boolean exists = jdbcTemplate.getJdbcTemplate().queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1
                            FROM information_schema.tables
                            WHERE table_schema = ? AND table_name = ?
                        )
                        """,
                Boolean.class,
                schema,
                table
        );
        return Boolean.TRUE.equals(exists);
    }

    private JsonNode parseJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception ex) {
            logger.warn("No se pudo parsear parametros_json de CREFFSOS: {}", ex.getMessage());
            return MissingNode.getInstance();
        }
    }

    private static String quoteIdentifier(String rawIdentifier) {
        String identifier = rawIdentifier == null ? "" : rawIdentifier.trim();
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Identificador SQL inválido para CREFFSOS: " + rawIdentifier);
        }
        return '"' + identifier.toLowerCase(Locale.ROOT) + '"';
    }

    private record QualifiedTable(String schema, String table) {
        static QualifiedTable parse(String rawValue) {
            String value = rawValue == null ? "" : rawValue.trim();
            if (value.isBlank()) {
                throw new IllegalArgumentException("La tabla de lookup CREFFSOS no puede estar vacía");
            }

            String[] parts = value.split("\\.", 2);
            if (parts.length == 1) {
                return new QualifiedTable("public", sanitize(parts[0]));
            }
            return new QualifiedTable(sanitize(parts[0]), sanitize(parts[1]));
        }

        String qualifiedName() {
            return quoteIdentifier(schema) + "." + quoteIdentifier(table);
        }

        private static String sanitize(String rawIdentifier) {
            String identifier = rawIdentifier == null ? "" : rawIdentifier.trim();
            if (!IDENTIFIER.matcher(identifier).matches()) {
                throw new IllegalArgumentException("Identificador de tabla CREFFSOS inválido: " + rawIdentifier);
            }
            return identifier.toLowerCase(Locale.ROOT);
        }
    }
}