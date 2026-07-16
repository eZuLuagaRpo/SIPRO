package com.bancolombia.sipro.validations.application.dto;

/**
 * Respuesta del endpoint de validación de ventana de carga.
 * Informa al frontend si la fecha de corte seleccionada está dentro
 * de la ventana de carga permitida.
 */
public class VentanaCargaResponse {

    /** true si hoy está dentro de la ventana de carga para el periodo solicitado */
    private boolean dentroDeVentana;

    /** Tipo de ventana: "REGLA" = calculada por regla general, "EXCEPCION" = sobreescrita por excepción */
    private String tipoVentana;

    /** Fecha/hora de apertura de la ventana (ISO-8601: yyyy-MM-dd'T'HH:mm:ss) */
    private String fechaHoraApertura;

    /** Fecha/hora de cierre de la ventana (ISO-8601: yyyy-MM-dd'T'HH:mm:ss) */
    private String fechaHoraCierre;

    /** Mensaje descriptivo para mostrar al usuario */
    private String mensaje;

    /** Motivo de la excepción (solo cuando tipoVentana = "EXCEPCION") */
    private String motivoExcepcion;

    public VentanaCargaResponse() {
    }

    // Getters y Setters

    public boolean isDentroDeVentana() { return dentroDeVentana; }
    public void setDentroDeVentana(boolean dentroDeVentana) { this.dentroDeVentana = dentroDeVentana; }

    public String getTipoVentana() { return tipoVentana; }
    public void setTipoVentana(String tipoVentana) { this.tipoVentana = tipoVentana; }

    public String getFechaHoraApertura() { return fechaHoraApertura; }
    public void setFechaHoraApertura(String fechaHoraApertura) { this.fechaHoraApertura = fechaHoraApertura; }

    public String getFechaHoraCierre() { return fechaHoraCierre; }
    public void setFechaHoraCierre(String fechaHoraCierre) { this.fechaHoraCierre = fechaHoraCierre; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public String getMotivoExcepcion() { return motivoExcepcion; }
    public void setMotivoExcepcion(String motivoExcepcion) { this.motivoExcepcion = motivoExcepcion; }
}
