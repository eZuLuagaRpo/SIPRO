package com.bancolombia.sipro.validations.application.dto;

import java.time.OffsetDateTime;

/**
 * Resume el estado actual de una consolidación manual lanzada por periodo.
 */
public class ConsolidacionManualStatusResponse {

    private String periodo;
    private String estado;
    private String mensaje;
    private boolean terminal;
    private boolean exito;
    private Integer cantidadArchivosConsolidados;
    private Integer cantidadRegistrosConsolidados;
    private OffsetDateTime fechaHoraInicio;
    private OffsetDateTime fechaHoraFin;
    private String mensajeError;

    public String getPeriodo() {
        return periodo;
    }

    public void setPeriodo(String periodo) {
        this.periodo = periodo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public void setTerminal(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isExito() {
        return exito;
    }

    public void setExito(boolean exito) {
        this.exito = exito;
    }

    public Integer getCantidadArchivosConsolidados() {
        return cantidadArchivosConsolidados;
    }

    public void setCantidadArchivosConsolidados(Integer cantidadArchivosConsolidados) {
        this.cantidadArchivosConsolidados = cantidadArchivosConsolidados;
    }

    public Integer getCantidadRegistrosConsolidados() {
        return cantidadRegistrosConsolidados;
    }

    public void setCantidadRegistrosConsolidados(Integer cantidadRegistrosConsolidados) {
        this.cantidadRegistrosConsolidados = cantidadRegistrosConsolidados;
    }

    public OffsetDateTime getFechaHoraInicio() {
        return fechaHoraInicio;
    }

    public void setFechaHoraInicio(OffsetDateTime fechaHoraInicio) {
        this.fechaHoraInicio = fechaHoraInicio;
    }

    public OffsetDateTime getFechaHoraFin() {
        return fechaHoraFin;
    }

    public void setFechaHoraFin(OffsetDateTime fechaHoraFin) {
        this.fechaHoraFin = fechaHoraFin;
    }

    public String getMensajeError() {
        return mensajeError;
    }

    public void setMensajeError(String mensajeError) {
        this.mensajeError = mensajeError;
    }
}