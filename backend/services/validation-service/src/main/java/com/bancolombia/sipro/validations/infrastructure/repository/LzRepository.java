package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.service.ParametroUnicoService;
import com.bancolombia.sipro.validations.infrastructure.config.LzProperties;
import com.bancolombia.sipro.validations.infrastructure.lz.LzJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Repositorio de SOLO LECTURA para LZ (Landing Zone - Impala On-Prem).
 * 
 * Usa LzJdbcService para conexiones dinamicas por-peticion,
 * siguiendo el patron de LzMinimalTest.java ("Conectar a LZ").
 * 
 * IMPORTANTE: 
 * - NO implementa executeUpdate, DDL, DML (DROP/CREATE/INSERT)
 * - Conexion efimera por peticion (no pool)
 * - Tablas parametrizadas (sin hardcode)
 * - Limites de filas controlados via lz.ingestion.max-rows (no hardcoded)
 */
@Repository
public class LzRepository {

    private static final int DEFAULT_LZ_INGESTION_MAX_ROWS = 0;
    private static final int DEFAULT_FALLBACK_LIMIT = 10;
    
    private static final Logger logger = LoggerFactory.getLogger(LzRepository.class);
    private final LzJdbcService lzJdbcService;
    private final LzProperties lzProperties;
    private final ParametroUnicoService parametroUnicoService;

    public LzRepository(LzJdbcService lzJdbcService,
                        LzProperties lzProperties,
                        ParametroUnicoService parametroUnicoService) {
        this.lzJdbcService = lzJdbcService;
        this.lzProperties = lzProperties;
        this.parametroUnicoService = parametroUnicoService;
    }

    /**
     * Valida permisos de lectura sobre una tabla específica.
     * Usa COUNT(*) escalar para no transferir filas.
     * 
     * @param tableKey Clave de la tabla configurada (ej: "mdm")
     * @return true si se puede leer, false si hay error de permisos/conexión
     */
    public boolean checkTableReadable(String tableKey) {
        try {
            String fullTable = lzProperties.getFullTableName(tableKey);
            String sql = "SELECT COUNT(*) FROM " + fullTable + " LIMIT 1";
            
            logger.debug("Verificando acceso (escalar) a LZ: {}", fullTable);
            long count = lzJdbcService.executeScalar(sql);
            
            return count >= 0; // si no lanza excepcion, es readable
        } catch (Exception e) {
            logger.error("No se pudo leer la tabla {}: {}", tableKey, e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene muestra de datos de una tabla.
     * El limite de filas viene de lz.ingestion.max-rows (application-*.yml).
     * En DEV: 100, en PDN: configurable.
     * 
     * @param tableKey Clave de la tabla configurada
     * @return Lista de registros como mapa (columna -> valor)
     */
    public List<Map<String, Object>> getSampleData(String tableKey) {
        try {
            String fullTable = lzProperties.getFullTableName(tableKey);
            int maxRows = parametroUnicoService.getInt("LZ_INGESTION_MAX_ROWS", DEFAULT_LZ_INGESTION_MAX_ROWS);
            int fallbackLimit = parametroUnicoService.getInt("FALLBACK_LIMIT", DEFAULT_FALLBACK_LIMIT);
            int limit = maxRows > 0 ? maxRows : fallbackLimit;
            String sql = "SELECT * FROM " + fullTable + " LIMIT " + limit;
            
            logger.info("Consultando muestra de {} (limit={})", fullTable, limit);
            List<Map<String, Object>> resultado = lzJdbcService.executeQuery(sql);
            
            logger.info("Consulta exitosa. Registros: {}", resultado.size());
            return resultado;
        } catch (Exception e) {
            logger.error("Error consultando {}: {}", tableKey, e.getMessage());
            throw new RuntimeException("Error leyendo " + tableKey + ": " + e.getMessage(), e);
        }
    }
}
