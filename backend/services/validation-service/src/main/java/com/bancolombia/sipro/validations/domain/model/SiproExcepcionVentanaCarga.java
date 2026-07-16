package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Entity que representa la tabla sipro_parametros_excepcionventanacarga.
 * Almacena excepciones por periodo que sobreescriben la regla general
 * de ventana de carga. Los campos override son opcionales (excepciones parciales).
 */
@Entity
@Table(name = "sipro_parametros_excepcionventanacarga", schema = "public")
public class SiproExcepcionVentanaCarga implements Serializable {

    @Id
    @Column(name = "periodo_valoracion", nullable = false)
    private LocalDate periodoValoracion;

    @Column(name = "fecha_apertura_override")
    private LocalDate fechaAperturaOverride;

    @Column(name = "hora_apertura_override")
    private LocalTime horaAperturaOverride;

    @Column(name = "fecha_cierre_override")
    private LocalDate fechaCierreOverride;

    @Column(name = "hora_cierre_override")
    private LocalTime horaCierreOverride;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "creado_por_id", nullable = false)
    private Long creadoPorId;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "modificado_por_id", nullable = false)
    private Long modificadoPorId;

    @Column(name = "modificado_en", nullable = false)
    private OffsetDateTime modificadoEn;

    public SiproExcepcionVentanaCarga() {
    }

    // Getters y Setters

    public LocalDate getPeriodoValoracion() { return periodoValoracion; }
    public void setPeriodoValoracion(LocalDate periodoValoracion) { this.periodoValoracion = periodoValoracion; }

    public LocalDate getFechaAperturaOverride() { return fechaAperturaOverride; }
    public void setFechaAperturaOverride(LocalDate fechaAperturaOverride) { this.fechaAperturaOverride = fechaAperturaOverride; }

    public LocalTime getHoraAperturaOverride() { return horaAperturaOverride; }
    public void setHoraAperturaOverride(LocalTime horaAperturaOverride) { this.horaAperturaOverride = horaAperturaOverride; }

    public LocalDate getFechaCierreOverride() { return fechaCierreOverride; }
    public void setFechaCierreOverride(LocalDate fechaCierreOverride) { this.fechaCierreOverride = fechaCierreOverride; }

    public LocalTime getHoraCierreOverride() { return horaCierreOverride; }
    public void setHoraCierreOverride(LocalTime horaCierreOverride) { this.horaCierreOverride = horaCierreOverride; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public Long getCreadoPorId() { return creadoPorId; }
    public void setCreadoPorId(Long creadoPorId) { this.creadoPorId = creadoPorId; }

    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(OffsetDateTime creadoEn) { this.creadoEn = creadoEn; }

    public Long getModificadoPorId() { return modificadoPorId; }
    public void setModificadoPorId(Long modificadoPorId) { this.modificadoPorId = modificadoPorId; }

    public OffsetDateTime getModificadoEn() { return modificadoEn; }
    public void setModificadoEn(OffsetDateTime modificadoEn) { this.modificadoEn = modificadoEn; }
}
