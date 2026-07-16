package com.bancolombia.sipro.validations.api;

import com.bancolombia.sipro.validations.application.dto.RangoFechaCorteResponse;
import com.bancolombia.sipro.validations.application.dto.VentanaCargaResponse;
import com.bancolombia.sipro.validations.domain.service.ParametroUnicoService;
import com.bancolombia.sipro.validations.domain.service.RangoFechaCorteService;
import com.bancolombia.sipro.validations.domain.service.VentanaCargaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Controller para endpoints de configuración del sistema.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final RangoFechaCorteService rangoFechaCorteService;
    private final VentanaCargaService ventanaCargaService;
    private final ParametroUnicoService parametroUnicoService;

    public ConfigController(RangoFechaCorteService rangoFechaCorteService,
                             VentanaCargaService ventanaCargaService,
                             ParametroUnicoService parametroUnicoService) {
        this.rangoFechaCorteService = rangoFechaCorteService;
        this.ventanaCargaService = ventanaCargaService;
        this.parametroUnicoService = parametroUnicoService;
    }

    /**
     * Obtiene la configuración del rango de fechas de corte permitidas.
     * Este endpoint es consumido por el frontend para habilitar/deshabilitar
     * meses en el calendario del selector de fecha de corte.
     * 
     * @return RangoFechaCorteResponse con los parámetros y valores calculados
     */
    @GetMapping("/rango-fecha-corte")
    public ResponseEntity<RangoFechaCorteResponse> getRangoFechaCorte() {
        RangoFechaCorteResponse response = rangoFechaCorteService.obtenerRangoFechaCorte();
        return ResponseEntity.ok(response);
    }

    /**
     * Valida si la fecha/hora actual está dentro de la ventana de carga
     * para un periodo de valoración dado (fecha de corte).
     *
     * @param fechaCorte Fecha de corte en formato yyyy-MM-dd (último día del mes)
     * @return VentanaCargaResponse con el resultado
     */
    @GetMapping("/ventana-carga")
    public ResponseEntity<VentanaCargaResponse> validarVentanaCarga(
            @RequestParam("fechaCorte") String fechaCorte) {
        LocalDate periodo;
        try {
            periodo = LocalDate.parse(fechaCorte);
        } catch (DateTimeParseException e) {
            VentanaCargaResponse error = new VentanaCargaResponse();
            error.setDentroDeVentana(false);
            error.setMensaje("Formato de fecha inválido. Use yyyy-MM-dd.");
            return ResponseEntity.badRequest().body(error);
        }

        VentanaCargaResponse response = ventanaCargaService.validarVentana(periodo);
        return ResponseEntity.ok(response);
    }

    /**
     * Recarga en memoria los parámetros configurables sin reiniciar el servicio.
     */
    @PostMapping("/parametros-unicos/recargar")
    public ResponseEntity<Map<String, Object>> recargarParametrosUnicos() {
        parametroUnicoService.recargar();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "mensaje", "Parámetros únicos recargados correctamente",
                "cantidad", parametroUnicoService.cantidadParametros()));
    }
}
