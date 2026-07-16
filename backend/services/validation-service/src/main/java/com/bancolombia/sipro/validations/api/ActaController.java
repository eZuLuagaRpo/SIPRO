package com.bancolombia.sipro.validations.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone una respuesta simple de acta mientras se define la generación final.
 */
@RestController
@RequestMapping(path = "/acta", produces = MediaType.APPLICATION_JSON_VALUE)
public class ActaController {

    /**
     * Devuelve un acta básica de ejemplo para pruebas de integración.
     */
    @GetMapping
    public Map<String, Object> getActa() {
        return Map.of(
                "generatedAt", Instant.now().toString(),
                "status", "PLACEHOLDER",
                "format", "json",
                "note", "Acta de validación (placeholder)"
        );
    }
}
