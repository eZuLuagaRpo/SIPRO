package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Entidad principal que representa una planilla cargada y su estado dentro del flujo.
 */
@Entity
@Table(name = "sipro_detalle_carga_planillas", schema = "public")
public class SiproDetalleCargaPlanillas implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_usuario_carga")
    private Long idUsuarioCarga;

    @Column(name = "id_lider")
    private Long idLider;

    @Column(name = "correo_usuario_carga")
    private String correoUsuarioCarga;

    @Column(name = "nombre_usuario_carga")
    private String nombreUsuarioCarga;

    @Column(name = "nombre_lider")
    private String nombreLider;

    @Column(name = "correo_lider")
    private String correoLider;

    @Column(name = "nombre_area")
    private String nombreArea;

    @Column(name = "id_producto")
    private Long idProducto;

    @Column(name = "producto")
    private String producto;

    @Column(name = "fecha_corte_informacion")
    private LocalDate fechaCorteInformacion;

    @Column(name = "descripcion_larga", columnDefinition = "TEXT")
    private String descripcionLarga;

    @Column(name = "nombre_archivo_fuente", length = 500)
    private String nombreArchivoFuente;

    @Column(name = "peso_archivo_fuente")
    private Long pesoArchivoFuente;

    @Column(name = "ruta_archivo_almacenamiento", length = 500)
    private String rutaArchivoAlmacenamiento;

    @Column(name = "ruta_archivo_control", length = 500)
    private String rutaArchivoControl;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "estado_planilla", length = 100)
    private String estadoPlanilla;

    @Column(name = "year")
    private Integer year;

    @Column(name = "month")
    private Integer month;

    @Column(name = "day")
    private Integer day;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "segmento")
    private String segmento;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "fecha_inactivacion")
    private LocalDateTime fechaInactivacion;

    @Column(name = "archivo_uid", columnDefinition = "TEXT")
    private String archivoUid;

    @Column(name = "no_reporta_datos")
    private Boolean noReportaDatos = false;

    @Column(name = "id_usuario_aprobador")
    private Long idUsuarioAprobador;

    @Column(name = "usuario_aprobador")
    private String usuarioAprobador;

    @Column(name = "fecha_aprobacion")
    private OffsetDateTime fechaAprobacion;

    // Constructores, Getters y Setters

    public SiproDetalleCargaPlanillas() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIdUsuarioCarga() {
        return idUsuarioCarga;
    }

    public void setIdUsuarioCarga(Long idUsuarioCarga) {
        this.idUsuarioCarga = idUsuarioCarga;
    }

    public Long getIdLider() {
        return idLider;
    }

    public void setIdLider(Long idLider) {
        this.idLider = idLider;
    }

    public String getCorreoUsuarioCarga() {
        return correoUsuarioCarga;
    }

    public void setCorreoUsuarioCarga(String correoUsuarioCarga) {
        this.correoUsuarioCarga = correoUsuarioCarga;
    }

    public String getNombreUsuarioCarga() {
        return nombreUsuarioCarga;
    }

    public void setNombreUsuarioCarga(String nombreUsuarioCarga) {
        this.nombreUsuarioCarga = nombreUsuarioCarga;
    }

    public String getNombreLider() {
        return nombreLider;
    }

    public void setNombreLider(String nombreLider) {
        this.nombreLider = nombreLider;
    }

    public String getCorreoLider() {
        return correoLider;
    }

    public void setCorreoLider(String correoLider) {
        this.correoLider = correoLider;
    }

    public String getNombreArea() {
        return nombreArea;
    }

    public void setNombreArea(String nombreArea) {
        this.nombreArea = nombreArea;
    }

    public Long getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(Long idProducto) {
        this.idProducto = idProducto;
    }

    public String getProducto() {
        return producto;
    }

    public void setProducto(String producto) {
        this.producto = producto;
    }

    public LocalDate getFechaCorteInformacion() {
        return fechaCorteInformacion;
    }

    public void setFechaCorteInformacion(LocalDate fechaCorteInformacion) {
        this.fechaCorteInformacion = fechaCorteInformacion;
    }

    public String getDescripcionLarga() {
        return descripcionLarga;
    }

    public void setDescripcionLarga(String descripcionLarga) {
        this.descripcionLarga = descripcionLarga;
    }

    public String getNombreArchivoFuente() {
        return nombreArchivoFuente;
    }

    public void setNombreArchivoFuente(String nombreArchivoFuente) {
        this.nombreArchivoFuente = nombreArchivoFuente;
    }

    public Long getPesoArchivoFuente() {
        return pesoArchivoFuente;
    }

    public void setPesoArchivoFuente(Long pesoArchivoFuente) {
        this.pesoArchivoFuente = pesoArchivoFuente;
    }

    public String getRutaArchivoAlmacenamiento() {
        return rutaArchivoAlmacenamiento;
    }

    public void setRutaArchivoAlmacenamiento(String rutaArchivoAlmacenamiento) {
        this.rutaArchivoAlmacenamiento = rutaArchivoAlmacenamiento;
    }

    public String getRutaArchivoControl() {
        return rutaArchivoControl;
    }

    public void setRutaArchivoControl(String rutaArchivoControl) {
        this.rutaArchivoControl = rutaArchivoControl;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getEstadoPlanilla() {
        return estadoPlanilla;
    }

    public void setEstadoPlanilla(String estadoPlanilla) {
        this.estadoPlanilla = estadoPlanilla;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public Integer getDay() {
        return day;
    }

    public void setDay(Integer day) {
        this.day = day;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public String getSegmento() {
        return segmento;
    }

    public void setSegmento(String segmento) {
        this.segmento = segmento;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getFechaInactivacion() {
        return fechaInactivacion;
    }

    public void setFechaInactivacion(LocalDateTime fechaInactivacion) {
        this.fechaInactivacion = fechaInactivacion;
    }

    public String getArchivoUid() {
        return archivoUid;
    }

    public void setArchivoUid(String archivoUid) {
        this.archivoUid = archivoUid;
    }

    public Boolean getNoReportaDatos() {
        return noReportaDatos;
    }

    public void setNoReportaDatos(Boolean noReportaDatos) {
        this.noReportaDatos = noReportaDatos;
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

    public OffsetDateTime getFechaAprobacion() {
        return fechaAprobacion;
    }

    public void setFechaAprobacion(OffsetDateTime fechaAprobacion) {
        this.fechaAprobacion = fechaAprobacion;
    }
}
