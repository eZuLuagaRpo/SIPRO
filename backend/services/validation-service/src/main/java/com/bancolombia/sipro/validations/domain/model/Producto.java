package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Catálogo de productos disponibles para cargar y validar en SIPRO.
 */
@Entity
@Table(name = "productos", schema = "public")
public class Producto implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "productos_seq")
    @SequenceGenerator(name = "productos_seq", sequenceName = "productos_id_producto_seq", allocationSize = 1)
    @Column(name = "id_producto")
    private Long idProducto;

    @Column(name = "titulo")
    private String titulo;

    @Column(name = "id_segmento")
    private Long idSegmento;

    @Column(name = "activo")
    private Integer activo;

    @Column(name = "creado_en")
    private java.time.LocalDateTime creadoEn;

    @Column(name = "nombre_archivo_permitido")
    private String nombreArchivoPermitido;

    @Column(name = "nombre_control_permitido")
    private String nombreControlPermitido;

    // Getters y Setters
    public Long getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(Long idProducto) {
        this.idProducto = idProducto;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public Long getIdSegmento() {
        return idSegmento;
    }

    public void setIdSegmento(Long idSegmento) {
        this.idSegmento = idSegmento;
    }

    public Integer getActivo() {
        return activo;
    }

    public void setActivo(Integer activo) {
        this.activo = activo;
    }

    public java.time.LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(java.time.LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public String getNombreArchivoPermitido() {
        return nombreArchivoPermitido;
    }

    public void setNombreArchivoPermitido(String nombreArchivoPermitido) {
        this.nombreArchivoPermitido = nombreArchivoPermitido;
    }

    public String getNombreControlPermitido() {
        return nombreControlPermitido;
    }

    public void setNombreControlPermitido(String nombreControlPermitido) {
        this.nombreControlPermitido = nombreControlPermitido;
    }
}
