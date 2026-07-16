package com.bancolombia.sipro.validations.application.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Contrato principal del panel de administrador.
 */
public record AdminDashboardResponse(
        List<PeriodoDisponible> periodosDisponibles,
        String periodoSeleccionado,
        ConsolidacionManualStatusResponse estadoConsolidacion,
        EstadoPeriodoConsolidacion estadoPeriodo,
        List<ArchivoEstado> archivosAConsolidar,
        List<ArchivoEstado> archivosNoBloqueantes,
        List<ArchivoEstado> archivosConsolidados,
        DiagnosticoDisponibilidad diagnosticoDisponibilidad,
        List<HistoricoConsolidacion> historico,
        ConfiguracionPanel configuracion) {

    public record EstadoPeriodoConsolidacion(
            String estadoVentana,
            String fuenteVentana,
            OffsetDateTime fechaHoraCierreVentana,
            OffsetDateTime inicioRangoConsolidacion,
            OffsetDateTime finRangoConsolidacion,
            String motivoExcepcion,
            String mensajeEstado,
            String mensajeDisponibilidad,
            boolean puedeEjecutarManual,
            boolean consolidacionEnCurso,
            boolean ventanaIgnoradaPorConfiguracion,
            boolean sobrescribeConsolidacionExistente,
            OffsetDateTime fechaUltimaConsolidacionExitosa,
            Integer cantidadArchivosUltimaConsolidacion,
            Integer cantidadRegistrosUltimaConsolidacion,
            String rutaArchivoCreffos,
            Integer cantidadPlanillasPendientes,
            Integer cantidadPlanillasRechazadas,
            String mensajeAdvertenciaOperativa) {
    }

    public record PeriodoDisponible(
            String valor,
            int anio,
            int mes,
            String etiqueta,
            boolean consolidado) {
    }

    public record ArchivoEstado(
            Long id,
            String nombreArchivo,
            String producto,
            Long pesoBytes,
            Integer cantidadRegistros,
            String estado,
            String detalleEstado,
            String rutaArchivo,
            Boolean noReportaDatos,
            String descripcionSinDatos,
            String nombreVisual) {
    }

    public record DiagnosticoDisponibilidad(
            Integer totalArchivosStorageAprobados,
            Integer totalArchivosElegibles,
            List<ArchivoEstado> archivosDescartados) {
    }

    public record HistoricoConsolidacion(
            Long idConsolidacion,
            String periodo,
            String estado,
            OffsetDateTime fechaHoraInicio,
            OffsetDateTime fechaHoraFin,
            Integer cantidadArchivos,
            Integer cantidadRegistros,
            String detalleSalida,
            String observacion,
            String mensajeError) {
    }

    public record ConfiguracionPanel(
            SqlConfig sql,
            LogsConfig logs) {
    }

    public record SqlConfig(
            List<String> operacionesHabilitadas,
            List<String> tablasPermitidas,
            int maxFilasSelect,
            boolean requiereWhereSelect,
            boolean requiereWhereUpdate) {
    }

    public record LogsConfig(
            boolean streamingHabilitado,
            boolean descargaHabilitada,
            int limiteConsultaPorDefecto,
            int maximoConsulta) {
    }
}