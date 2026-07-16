package com.bancolombia.sipro.validations.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.apache.poi.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Ajusta límites de Apache POI para procesar archivos grandes según la configuración activa.
 */
@Configuration
public class ExcelPoiConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ExcelPoiConfiguration.class);

    private final ExcelProcessingProperties properties;

    public ExcelPoiConfiguration(ExcelProcessingProperties properties) {
        this.properties = properties;
    }

    /**
     * Aplica al arranque el límite máximo de arreglos en memoria usado por POI.
     */
    @PostConstruct
    public void configurePoiLimits() {
        if (properties.getPoiByteArrayMaxOverride() > 0) {
            IOUtils.setByteArrayMaxOverride(properties.getPoiByteArrayMaxOverride());
            logger.info("Apache POI byteArrayMaxOverride configurado en {} bytes", properties.getPoiByteArrayMaxOverride());
        }
    }
}