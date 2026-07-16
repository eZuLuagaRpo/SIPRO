package com.bancolombia.sipro.validations.api;

import com.bancolombia.sipro.validations.infrastructure.repository.LzRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador REST para probar la conexión a LZ (Landing Zone - Impala).
 * Usa LzJdbcService (conexion dinamica) en lugar de pool.
 */
@RestController
@RequestMapping("/api/lz/test")
public class LzTestController {

    private static final Logger logger = LoggerFactory.getLogger(LzTestController.class);

    @Autowired
    private LzRepository lzRepository;

    /**
     * Healthcheck: Verifica si se puede leer una tabla específica.
     * GET /api/lz/test/check/{tableKey}
     *
     * Ejemplo:
     * - /api/lz/test/check/mdm
     */
    @GetMapping("/check/{tableKey}")
    public ResponseEntity<Map<String, Object>> checkTable(@PathVariable String tableKey) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Iniciando test de conectividad para tabla: {}", tableKey);
            boolean canRead = lzRepository.checkTableReadable(tableKey);
            
            response.put("status", canRead ? "SUCCESS" : "FAILED");
            response.put("tableKey", tableKey);
            response.put("readable", canRead);
            response.put("message", canRead 
                ? "Conexión exitosa. La tabla es accesible." 
                : "No se pudo acceder a la tabla. Verifica permisos y conexión.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error en test de conectividad: {}", e.getMessage(), e);
            response.put("status", "ERROR");
            response.put("tableKey", tableKey);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Consulta de datos de muestra (máximo 10 registros).
     * GET /api/lz/test/sample/{tableKey}
     *
     * Ejemplo:
     * - /api/lz/test/sample/mdm
     */
    @GetMapping("/sample/{tableKey}")
    public ResponseEntity<Map<String, Object>> getSample(@PathVariable String tableKey) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Consultando muestra de datos para tabla: {}", tableKey);
            List<Map<String, Object>> data = lzRepository.getSampleData(tableKey);
            
            response.put("status", "SUCCESS");
            response.put("tableKey", tableKey);
            response.put("recordCount", data.size());
            response.put("data", data);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error consultando datos de muestra: {}", e.getMessage(), e);
            response.put("status", "ERROR");
            response.put("tableKey", tableKey);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Test completo: Verifica conectividad a todas las tablas LZ configuradas.
     * GET /api/lz/test/all
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> testAll() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Boolean> results = new HashMap<>();

        String[] tables = {"mdm"};
        int successCount = 0;
        
        for (String tableKey : tables) {
            try {
                boolean canRead = lzRepository.checkTableReadable(tableKey);
                results.put(tableKey, canRead);
                if (canRead) successCount++;
            } catch (Exception e) {
                logger.error("Error verificando tabla {}: {}", tableKey, e.getMessage());
                results.put(tableKey, false);
            }
        }
        
        response.put("status", successCount == tables.length ? "SUCCESS" : "PARTIAL");
        response.put("totalTables", tables.length);
        response.put("successCount", successCount);
        response.put("results", results);
        
        return ResponseEntity.ok(response);
    }
}
