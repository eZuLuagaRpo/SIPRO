package com.bancolombia.sipro.validations.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

/**
 * Configuración externalizada para LZ (Landing Zone - Impala On-Prem).
 * Permite parametrizar esquemas y tablas sin modificar código Java.
 * 
 * Basado en:
 * - Lineamientos EUC Nivel 2 - Conexión a LZ
 * - Estructura de VSC_EUC00015_MASTER_INFORMES (repo referencia)
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.lz")
public class LzProperties {
    
    private String schema;
    private Map<String, String> tables;

    /**
     * Obtiene el nombre completo de una tabla (schema.table).
     * Valida que la tabla esté configurada en application.yml antes de construir el nombre.
     * 
     * @param tableKey Clave de la tabla (ej: "precalculo", "cdp", "control")
     * @return Nombre completo para usar en SQL (ej: "resultados_otras_fnes_corp.anexo2_precalculo_c032")
     */
    public String getFullTableName(String tableKey) {
        if (tables == null || !tables.containsKey(tableKey)) {
            throw new IllegalArgumentException("Tabla '" + tableKey + "' no configurada en application.yml");
        }
        return schema + "." + tables.get(tableKey);
    }
}
