package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Relaciona cada archivo aprobado que participa en una consolidación por periodo.
 */
@Entity
@Table(name = "sipro_detalle_consolidacion_archivos", schema = "public")
public class SiproDetalleConsolidacionArchivo implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_consolidacion_archivo")
    private Long idConsolidacionArchivo;

    @Column(name = "id_consolidacion", nullable = false)
    private Long idConsolidacion;

    @Column(name = "id_carga_planilla", nullable = false)
    private Long idCargaPlanilla;

    @Column(name = "id_validacion_archivo")
    private Integer idValidacionArchivo;

    @Column(name = "archivo_uid", columnDefinition = "TEXT")
    private String archivoUid;

    @Column(name = "nombre_archivo", length = 500)
    private String nombreArchivo;

    @Column(name = "ruta_archivo", length = 500)
    private String rutaArchivo;

    @Column(name = "fecha_corte")
    private LocalDate fechaCorte;

    @Column(name = "id_producto_origen")
    private Long idProductoOrigen;

    @Column(name = "producto_origen", length = 255)
    private String productoOrigen;

    @Column(name = "id_segmento")
    private Integer idSegmento;

    @Column(name = "segmento", length = 255)
    private String segmento;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "id_usuario_cargador")
    private Long idUsuarioCargador;

    @Column(name = "usuario_cargador", length = 255)
    private String usuarioCargador;

    @Column(name = "id_usuario_aprobador")
    private Long idUsuarioAprobador;

    @Column(name = "usuario_aprobador", length = 255)
    private String usuarioAprobador;

    @Column(name = "cantidad_registros_archivo")
    private Integer cantidadRegistrosArchivo;

    @Column(name = "fecha_validacion")
    private LocalDateTime fechaValidacion;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    public Long getIdConsolidacionArchivo() {
        return idConsolidacionArchivo;
    }

    public void setIdConsolidacionArchivo(Long idConsolidacionArchivo) {
        this.idConsolidacionArchivo = idConsolidacionArchivo;
    }

    public Long getIdConsolidacion() {
        return idConsolidacion;
    }

    public void setIdConsolidacion(Long idConsolidacion) {
        this.idConsolidacion = idConsolidacion;
    }

    public Long getIdCargaPlanilla() {
        return idCargaPlanilla;
    }

    public void setIdCargaPlanilla(Long idCargaPlanilla) {
        this.idCargaPlanilla = idCargaPlanilla;
    }

    public Integer getIdValidacionArchivo() {
        return idValidacionArchivo;
    }

    public void setIdValidacionArchivo(Integer idValidacionArchivo) {
        this.idValidacionArchivo = idValidacionArchivo;
    }

    public String getArchivoUid() {
        return archivoUid;
    }

    public void setArchivoUid(String archivoUid) {
        this.archivoUid = archivoUid;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getRutaArchivo() {
        return rutaArchivo;
    }

    public void setRutaArchivo(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }

    public LocalDate getFechaCorte() {
        return fechaCorte;
    }

    public void setFechaCorte(LocalDate fechaCorte) {
        this.fechaCorte = fechaCorte;
    }

    public Long getIdProductoOrigen() {
        return idProductoOrigen;
    }

    public void setIdProductoOrigen(Long idProductoOrigen) {
        this.idProductoOrigen = idProductoOrigen;
    }

    public String getProductoOrigen() {
        return productoOrigen;
    }

    public void setProductoOrigen(String productoOrigen) {
        this.productoOrigen = productoOrigen;
    }

    public Integer getIdSegmento() {
        return idSegmento;
    }

    public void setIdSegmento(Integer idSegmento) {
        this.idSegmento = idSegmento;
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

    public Long getIdUsuarioCargador() {
        return idUsuarioCargador;
    }

    public void setIdUsuarioCargador(Long idUsuarioCargador) {
        this.idUsuarioCargador = idUsuarioCargador;
    }

    public String getUsuarioCargador() {
        return usuarioCargador;
    }

    public void setUsuarioCargador(String usuarioCargador) {
        this.usuarioCargador = usuarioCargador;
    }

    public Long getIdUsuarioAprobador() {
        return idUsuarioAprobador;
    }

    public void setIdUsuarioAprobador(Long idUsuarioAprobador) {
        this.idUsuarioAprobador = idUsuarioAprobador;
    }

    public String getUsuarioAprobador() {
        return usuarioAprobador;
    }

    public void setUsuarioAprobador(String usuarioAprobador) {
        this.usuarioAprobador = usuarioAprobador;
    }

    public Integer getCantidadRegistrosArchivo() {
        return cantidadRegistrosArchivo;
    }

    public void setCantidadRegistrosArchivo(Integer cantidadRegistrosArchivo) {
        this.cantidadRegistrosArchivo = cantidadRegistrosArchivo;
    }

    public LocalDateTime getFechaValidacion() {
        return fechaValidacion;
    }

    public void setFechaValidacion(LocalDateTime fechaValidacion) {
        this.fechaValidacion = fechaValidacion;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(OffsetDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }
}