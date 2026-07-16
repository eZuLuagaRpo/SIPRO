package com.bancolombia.sipro.validations.application.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LzIngestionUseCaseTest {

    @Test
    void shouldTranslateStoredPostgresDateExpressionsToImpala() {
        String sql = """
            SELECT *
            FROM resultados_fcr.fcr_mdm_datos_generales_clientes t
            WHERE year = EXTRACT(YEAR FROM NOW() - INTERVAL '10 days')
              AND month = EXTRACT(MONTH FROM NOW() - INTERVAL '10 days')
              AND day = EXTRACT(DAY FROM NOW() - INTERVAL '10 days')
              AND t.year = EXTRACT(YEAR FROM NOW())
              AND t.month = EXTRACT(MONTH FROM NOW())
              AND t.day = EXTRACT(DAY FROM NOW())
            """;

        String translated = LzIngestionUseCase.adaptPostgresDateExpressionsForImpala(sql);

        assertTrue(translated.contains("year(date_sub(now(), 10))"));
        assertTrue(translated.contains("month(date_sub(now(), 10))"));
        assertTrue(translated.contains("day(date_sub(now(), 10))"));
        assertTrue(translated.contains("t.year = year(now())"));
        assertTrue(translated.contains("t.month = month(now())"));
        assertTrue(translated.contains("t.day = day(now())"));
        assertFalse(translated.contains("INTERVAL '10 days'"));
        assertFalse(translated.contains("EXTRACT(YEAR FROM NOW())"));
    }
}