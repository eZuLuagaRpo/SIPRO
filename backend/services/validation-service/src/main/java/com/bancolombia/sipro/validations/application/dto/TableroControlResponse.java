package com.bancolombia.sipro.validations.application.dto;

import java.util.List;

/**
 * Respuesta del tablero de control con el estado de planillas por producto y segmento para un periodo.
 */
public class TableroControlResponse {

    private List<PeriodoAnualDto> periodosDisponibles;
    private int anioSeleccionado;
    private int mesSeleccionado;
    private String periodoEtiqueta;
    private List<TableroFilaDto> filas;

    public TableroControlResponse() { }

    public TableroControlResponse(List<PeriodoAnualDto> periodosDisponibles, int anioSeleccionado,
            int mesSeleccionado, String periodoEtiqueta, List<TableroFilaDto> filas) {
        this.periodosDisponibles = periodosDisponibles;
        this.anioSeleccionado = anioSeleccionado;
        this.mesSeleccionado = mesSeleccionado;
        this.periodoEtiqueta = periodoEtiqueta;
        this.filas = filas;
    }

    public List<PeriodoAnualDto> getPeriodosDisponibles() { return periodosDisponibles; }
    public void setPeriodosDisponibles(List<PeriodoAnualDto> periodosDisponibles) { this.periodosDisponibles = periodosDisponibles; }

    public int getAnioSeleccionado() { return anioSeleccionado; }
    public void setAnioSeleccionado(int anioSeleccionado) { this.anioSeleccionado = anioSeleccionado; }

    public int getMesSeleccionado() { return mesSeleccionado; }
    public void setMesSeleccionado(int mesSeleccionado) { this.mesSeleccionado = mesSeleccionado; }

    public String getPeriodoEtiqueta() { return periodoEtiqueta; }
    public void setPeriodoEtiqueta(String periodoEtiqueta) { this.periodoEtiqueta = periodoEtiqueta; }

    public List<TableroFilaDto> getFilas() { return filas; }
    public void setFilas(List<TableroFilaDto> filas) { this.filas = filas; }

    // =================== CLASES INTERNAS ===================

    /**
     * Fila del tablero con los estados de carga y aprobación por segmento para un producto.
     * Estados de cargado: ARCHIVO_CARGADO | SIN_DATOS | PENDIENTE_CARGA | NO_APLICA
     * Estados de aprobado: ARCHIVO_APROBADO | APROBACION_SIN_DATOS | PENDIENTE_APROBACION | ARCHIVO_RECHAZADO | RECHAZO_SIN_DATOS | NO_APLICA | null
     */
    public static class TableroFilaDto {
        private Long idProducto;
        private String nombreProducto;
        private String estadoCargadoColgaap;
        private String estadoAprobadoColgaap;
        private String estadoCargadoFullIfrs;
        private String estadoAprobadoFullIfrs;

        public TableroFilaDto() { }

        public Long getIdProducto() { return idProducto; }
        public void setIdProducto(Long idProducto) { this.idProducto = idProducto; }

        public String getNombreProducto() { return nombreProducto; }
        public void setNombreProducto(String nombreProducto) { this.nombreProducto = nombreProducto; }

        public String getEstadoCargadoColgaap() { return estadoCargadoColgaap; }
        public void setEstadoCargadoColgaap(String estadoCargadoColgaap) { this.estadoCargadoColgaap = estadoCargadoColgaap; }

        public String getEstadoAprobadoColgaap() { return estadoAprobadoColgaap; }
        public void setEstadoAprobadoColgaap(String estadoAprobadoColgaap) { this.estadoAprobadoColgaap = estadoAprobadoColgaap; }

        public String getEstadoCargadoFullIfrs() { return estadoCargadoFullIfrs; }
        public void setEstadoCargadoFullIfrs(String estadoCargadoFullIfrs) { this.estadoCargadoFullIfrs = estadoCargadoFullIfrs; }

        public String getEstadoAprobadoFullIfrs() { return estadoAprobadoFullIfrs; }
        public void setEstadoAprobadoFullIfrs(String estadoAprobadoFullIfrs) { this.estadoAprobadoFullIfrs = estadoAprobadoFullIfrs; }
    }

    /**
     * Año con sus meses disponibles para el selector de periodo del tablero.
     * Estructura idéntica a ConsolidacionPeriodoAnual en el frontend.
     */
    public static class PeriodoAnualDto {
        private int anio;
        private List<MesDisponibleDto> meses;

        public PeriodoAnualDto() { }

        public PeriodoAnualDto(int anio, List<MesDisponibleDto> meses) {
            this.anio = anio;
            this.meses = meses;
        }

        public int getAnio() { return anio; }
        public void setAnio(int anio) { this.anio = anio; }

        public List<MesDisponibleDto> getMeses() { return meses; }
        public void setMeses(List<MesDisponibleDto> meses) { this.meses = meses; }
    }

    /**
     * Mes navegable en el selector del tablero de control.
     * Estructura idéntica a ConsolidacionMesDisponible en el frontend.
     */
    public static class MesDisponibleDto {
        private int numero;
        private String etiqueta;
        private String abreviatura;
        private String periodo;

        public MesDisponibleDto() { }

        public MesDisponibleDto(int numero, String etiqueta, String abreviatura, String periodo) {
            this.numero = numero;
            this.etiqueta = etiqueta;
            this.abreviatura = abreviatura;
            this.periodo = periodo;
        }

        public int getNumero() { return numero; }
        public void setNumero(int numero) { this.numero = numero; }

        public String getEtiqueta() { return etiqueta; }
        public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }

        public String getAbreviatura() { return abreviatura; }
        public void setAbreviatura(String abreviatura) { this.abreviatura = abreviatura; }

        public String getPeriodo() { return periodo; }
        public void setPeriodo(String periodo) { this.periodo = periodo; }
    }
}