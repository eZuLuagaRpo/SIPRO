package com.bancolombia.sipro.validations.application.dto;

import java.time.LocalDateTime;

/**
 * DTO con el resumen de cargas de un usuario, agrupado por estado.
 */
public class ResumenCargasResponse {

    private long pendientes;
    private long aprobados;
    private long rechazados;
    private LocalDateTime ultimaCarga;

    public ResumenCargasResponse() {
    }

    public ResumenCargasResponse(long pendientes, long aprobados, long rechazados, LocalDateTime ultimaCarga) {
        this.pendientes = pendientes;
        this.aprobados = aprobados;
        this.rechazados = rechazados;
        this.ultimaCarga = ultimaCarga;
    }

    public long getPendientes() {
        return pendientes;
    }

    public void setPendientes(long pendientes) {
        this.pendientes = pendientes;
    }

    public long getAprobados() {
        return aprobados;
    }

    public void setAprobados(long aprobados) {
        this.aprobados = aprobados;
    }

    public long getRechazados() {
        return rechazados;
    }

    public void setRechazados(long rechazados) {
        this.rechazados = rechazados;
    }

    public LocalDateTime getUltimaCarga() {
        return ultimaCarga;
    }

    public void setUltimaCarga(LocalDateTime ultimaCarga) {
        this.ultimaCarga = ultimaCarga;
    }
}
