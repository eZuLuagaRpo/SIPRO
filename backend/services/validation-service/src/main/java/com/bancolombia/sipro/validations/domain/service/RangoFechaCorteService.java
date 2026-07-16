package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.application.dto.RangoFechaCorteResponse;
import com.bancolombia.sipro.validations.domain.model.SiproParametrosRangoHabilitado;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproParametrosRangoHabilitadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.Optional;

/**
 * Servicio para gestionar la configuración de rango de fechas de corte.
 */
@Service
public class RangoFechaCorteService {

    private static final Logger logger = LoggerFactory.getLogger(RangoFechaCorteService.class);

    // Valores por defecto si no hay configuración en BD ni en parámetros únicos
    private static final short DEFAULT_MESES_PASADO = 1;
    private static final short DEFAULT_MESES_FUTURO = 2;

    private final SiproParametrosRangoHabilitadoRepository repository;

    public RangoFechaCorteService(SiproParametrosRangoHabilitadoRepository repository) {
        this.repository = repository;
    }

    /**
     * Obtiene la configuración del rango de fechas de corte permitidas.
     * Si no existe configuración activa, retorna valores por defecto.
     */
    public RangoFechaCorteResponse obtenerRangoFechaCorte() {
        Optional<SiproParametrosRangoHabilitado> configOpt = repository.findActiveConfigFechaCorte();

        if (configOpt.isPresent()) {
            SiproParametrosRangoHabilitado config = configOpt.get();
            logger.info("Configuración de rango fecha corte encontrada: meses_pasado={}, meses_futuro={}",
                    config.getMesesPasado(), config.getMesesFuturo());
            
            return buildResponse(config.getMesesPasado(), config.getMesesFuturo());
        } else {
            logger.warn("No se encontró configuración activa para FECHA_CORTE, usando valores por defecto: " +
                    "meses_pasado={}, meses_futuro={}", DEFAULT_MESES_PASADO, DEFAULT_MESES_FUTURO);
            
            return buildResponse(DEFAULT_MESES_PASADO, DEFAULT_MESES_FUTURO);
        }
    }

    /**
     * Construye el response con el rango calculado basado en el mes actual.
     */
    private RangoFechaCorteResponse buildResponse(short mesesPasado, short mesesFuturo) {
        YearMonth actual = YearMonth.now();
        YearMonth minimo = actual.minusMonths(mesesPasado);
        YearMonth maximo = actual.plusMonths(mesesFuturo);

        RangoFechaCorteResponse response = new RangoFechaCorteResponse();
        response.setMesesPasado(mesesPasado);
        response.setMesesFuturo(mesesFuturo);
        response.setMesActual(actual.getMonthValue());
        response.setAnioActual(actual.getYear());
        response.setMesMinimo(minimo.getMonthValue());
        response.setAnioMinimo(minimo.getYear());
        response.setMesMaximo(maximo.getMonthValue());
        response.setAnioMaximo(maximo.getYear());

        return response;
    }
}
