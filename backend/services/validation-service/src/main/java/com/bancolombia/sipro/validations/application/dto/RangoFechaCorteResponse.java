package com.bancolombia.sipro.validations.application.dto;

/**
 * DTO Response para la configuración del rango de fechas de corte permitidas.
 * Contiene tanto los parámetros configurados como los valores calculados.
 */
public class RangoFechaCorteResponse {

    // Parámetros configurados
    private short mesesPasado;
    private short mesesFuturo;

    // Valores calculados basados en fecha actual
    private int mesActual;
    private int anioActual;
    private int mesMinimo;
    private int anioMinimo;
    private int mesMaximo;
    private int anioMaximo;

    public RangoFechaCorteResponse() {
    }

    // Getters and Setters
    public short getMesesPasado() {
        return mesesPasado;
    }

    public void setMesesPasado(short mesesPasado) {
        this.mesesPasado = mesesPasado;
    }

    public short getMesesFuturo() {
        return mesesFuturo;
    }

    public void setMesesFuturo(short mesesFuturo) {
        this.mesesFuturo = mesesFuturo;
    }

    public int getMesActual() {
        return mesActual;
    }

    public void setMesActual(int mesActual) {
        this.mesActual = mesActual;
    }

    public int getAnioActual() {
        return anioActual;
    }

    public void setAnioActual(int anioActual) {
        this.anioActual = anioActual;
    }

    public int getMesMinimo() {
        return mesMinimo;
    }

    public void setMesMinimo(int mesMinimo) {
        this.mesMinimo = mesMinimo;
    }

    public int getAnioMinimo() {
        return anioMinimo;
    }

    public void setAnioMinimo(int anioMinimo) {
        this.anioMinimo = anioMinimo;
    }

    public int getMesMaximo() {
        return mesMaximo;
    }

    public void setMesMaximo(int mesMaximo) {
        this.mesMaximo = mesMaximo;
    }

    public int getAnioMaximo() {
        return anioMaximo;
    }

    public void setAnioMaximo(int anioMaximo) {
        this.anioMaximo = anioMaximo;
    }
}
