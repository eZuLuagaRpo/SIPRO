package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Cabecera que resume una ejecución de consolidación manual o automática por periodo.
 */
@Entity
@Table(name = "sipro_detalle_consolidaciones_planillas", schema = "public")
public class SiproDetalleConsolidacionesPlanillas implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_consolidacion")
    private Long idConsolidacion;

    @Column(name = "periodo_valoracion", nullable = false)
    private LocalDate periodoValoracion;

    @Column(name = "fecha_hora_inicio")
    private OffsetDateTime fechaHoraInicio;

    @Column(name = "fecha_hora_fin")
    private OffsetDateTime fechaHoraFin;

    @Column(name = "duracion_minutos")
    private BigDecimal duracionMinutos;

    @Column(name = "estado_consolidacion", nullable = false)
    private String estadoConsolidacion;

    @Column(name = "cantidad_archivos_consolidados", nullable = false)
    private Integer cantidadArchivosConsolidados = 0;

    @Column(name = "cantidad_registros_consolidados", nullable = false)
    private Integer cantidadRegistrosConsolidados = 0;

    @Column(name = "nombres_archivos_consolidados", columnDefinition = "TEXT")
    private String nombresArchivosConsolidados;

    @Column(name = "fuente_ventana")
    private String fuenteVentana;

    @Column(name = "id_regla_ventana")
    private Long idReglaVentana;

    @Column(name = "periodo_excepcion_ventana")
    private LocalDate periodoExcepcionVentana;

    @Column(name = "fecha_hora_apertura_efectiva")
    private OffsetDateTime fechaHoraAperturaEfectiva;

    @Column(name = "fecha_hora_cierre_efectiva")
    private OffsetDateTime fechaHoraCierreEfectiva;

    @Column(name = "creado_por_id", nullable = false)
    private Long creadoPorId;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "modificado_por_id")
    private Long modificadoPorId;

    @Column(name = "modificado_en", nullable = false)
    private OffsetDateTime modificadoEn;

    @Column(name = "observacion", columnDefinition = "TEXT")
    private String observacion;

    @Column(name = "mensaje_error", columnDefinition = "TEXT")
    private String mensajeError;

    public Long getIdConsolidacion() {
        return idConsolidacion;
    }

    public void setIdConsolidacion(Long idConsolidacion) {
        this.idConsolidacion = idConsolidacion;
    }

    public LocalDate getPeriodoValoracion() {
        return periodoValoracion;
    }

    public void setPeriodoValoracion(LocalDate periodoValoracion) {
        this.periodoValoracion = periodoValoracion;
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

    public BigDecimal getDuracionMinutos() {
        return duracionMinutos;
    }

    public void setDuracionMinutos(BigDecimal duracionMinutos) {
        this.duracionMinutos = duracionMinutos;
    }

    public String getEstadoConsolidacion() {
        return estadoConsolidacion;
    }

    public void setEstadoConsolidacion(String estadoConsolidacion) {
        this.estadoConsolidacion = estadoConsolidacion;
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

    public String getNombresArchivosConsolidados() {
        return nombresArchivosConsolidados;
    }

    public void setNombresArchivosConsolidados(String nombresArchivosConsolidados) {
        this.nombresArchivosConsolidados = nombresArchivosConsolidados;
    }

    public String getFuenteVentana() {
        return fuenteVentana;
    }

    public void setFuenteVentana(String fuenteVentana) {
        this.fuenteVentana = fuenteVentana;
    }

    public Long getIdReglaVentana() {
        return idReglaVentana;
    }

    public void setIdReglaVentana(Long idReglaVentana) {
        this.idReglaVentana = idReglaVentana;
    }

    public LocalDate getPeriodoExcepcionVentana() {
        return periodoExcepcionVentana;
    }

    public void setPeriodoExcepcionVentana(LocalDate periodoExcepcionVentana) {
        this.periodoExcepcionVentana = periodoExcepcionVentana;
    }

    public OffsetDateTime getFechaHoraAperturaEfectiva() {
        return fechaHoraAperturaEfectiva;
    }

    public void setFechaHoraAperturaEfectiva(OffsetDateTime fechaHoraAperturaEfectiva) {
        this.fechaHoraAperturaEfectiva = fechaHoraAperturaEfectiva;
    }

    public OffsetDateTime getFechaHoraCierreEfectiva() {
        return fechaHoraCierreEfectiva;
    }

    public void setFechaHoraCierreEfectiva(OffsetDateTime fechaHoraCierreEfectiva) {
        this.fechaHoraCierreEfectiva = fechaHoraCierreEfectiva;
    }

    public Long getCreadoPorId() {
        return creadoPorId;
    }

    public void setCreadoPorId(Long creadoPorId) {
        this.creadoPorId = creadoPorId;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(OffsetDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public Long getModificadoPorId() {
        return modificadoPorId;
    }

    public void setModificadoPorId(Long modificadoPorId) {
        this.modificadoPorId = modificadoPorId;
    }

    public OffsetDateTime getModificadoEn() {
        return modificadoEn;
    }

    public void setModificadoEn(OffsetDateTime modificadoEn) {
        this.modificadoEn = modificadoEn;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public String getMensajeError() {
        return mensajeError;
    }

    public void setMensajeError(String mensajeError) {
        this.mensajeError = mensajeError;
    }
}