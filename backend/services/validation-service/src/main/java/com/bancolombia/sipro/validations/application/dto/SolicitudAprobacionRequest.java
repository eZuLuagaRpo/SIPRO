package com.bancolombia.sipro.validations.application.dto;

import java.time.LocalDate;

/**
 * Datos que acompañan la solicitud de aprobación de una planilla.
 */
public class SolicitudAprobacionRequest {

    private String usuario;
    private Long idProducto;
    private Long idSegmento;
    private String producto;
    private String segmento;
    private String descripcion;
    private LocalDate fechaCorte;
    private String nombreArchivo;
    private Long pesoArchivo;
    private String validacionLoteId;
    private Boolean sinDatos;

    public SolicitudAprobacionRequest() {
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public Long getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(Long idProducto) {
        this.idProducto = idProducto;
    }

    public Long getIdSegmento() {
        return idSegmento;
    }

    public void setIdSegmento(Long idSegmento) {
        this.idSegmento = idSegmento;
    }

    public String getProducto() {
        return producto;
    }

    public void setProducto(String producto) {
        this.producto = producto;
    }

    public String getSegmento() {
        return segmento;
    }

    public void setSegmento(String segmento) {
        this.segmento = segmento;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public LocalDate getFechaCorte() {
        return fechaCorte;
    }

    public void setFechaCorte(LocalDate fechaCorte) {
        this.fechaCorte = fechaCorte;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public Long getPesoArchivo() {
        return pesoArchivo;
    }

    public void setPesoArchivo(Long pesoArchivo) {
        this.pesoArchivo = pesoArchivo;
    }

    public String getValidacionLoteId() {
        return validacionLoteId;
    }

    public void setValidacionLoteId(String validacionLoteId) {
        this.validacionLoteId = validacionLoteId;
    }

    public Boolean getSinDatos() {
        return sinDatos;
    }

    public void setSinDatos(Boolean sinDatos) {
        this.sinDatos = sinDatos;
    }
}
