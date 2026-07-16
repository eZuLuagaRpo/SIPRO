package com.bancolombia.sipro.validations.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agrupa parámetros de configuración usados durante el procesamiento de archivos Excel.
 */
@Component
@ConfigurationProperties(prefix = "app.excel")
public class ExcelProcessingProperties {

    private int poiByteArrayMaxOverride = 250_000_000;

    public int getPoiByteArrayMaxOverride() {
        return poiByteArrayMaxOverride;
    }

    public void setPoiByteArrayMaxOverride(int poiByteArrayMaxOverride) {
        this.poiByteArrayMaxOverride = poiByteArrayMaxOverride;
    }
}