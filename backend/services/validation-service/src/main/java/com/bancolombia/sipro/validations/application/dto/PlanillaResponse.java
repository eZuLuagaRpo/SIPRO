package com.bancolombia.sipro.validations.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Respuesta estándar para mostrar una planilla en listados y detalle.
 */
public class PlanillaResponse {

    private Long id;
    private String nombreResponsable;
    private String producto;
    private String segmento;
    private LocalDate fechaCorte;
    private String descripcion;
    private String estado;
    private String nombreArchivo;
    private Long pesoArchivo;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaAprobacion;
    private Integer numeroFilas;
    private Long idLider;
    private String correoLider;
    private Boolean activo;
    private Boolean sinDatos;

    public PlanillaResponse() {
    }

    // Getters y Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombreResponsable() {
        return nombreResponsable;
    }

    public void setNombreResponsable(String nombreResponsable) {
        this.nombreResponsable = nombreResponsable;
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

    public LocalDate getFechaCorte() {
        return fechaCorte;
    }

    public void setFechaCorte(LocalDate fechaCorte) {
        this.fechaCorte = fechaCorte;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
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

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getFechaAprobacion() {
        return fechaAprobacion;
    }

    public void setFechaAprobacion(LocalDateTime fechaAprobacion) {
        this.fechaAprobacion = fechaAprobacion;
    }

    public Integer getNumeroFilas() {
        return numeroFilas;
    }

    public void setNumeroFilas(Integer numeroFilas) {
        this.numeroFilas = numeroFilas;
    }

    public Long getIdLider() {
        return idLider;
    }

    public void setIdLider(Long idLider) {
        this.idLider = idLider;
    }

    public String getCorreoLider() {
        return correoLider;
    }

    public void setCorreoLider(String correoLider) {
        this.correoLider = correoLider;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public Boolean getSinDatos() {
        return sinDatos;
    }

    public void setSinDatos(Boolean sinDatos) {
        this.sinDatos = sinDatos;
    }
}
