package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Audita cada rechazo aplicado a una planilla y el motivo registrado.
 */
@Entity
@Table(name = "sipro_detalle_rechazos_planilla", schema = "public")
public class SiproDetalleRechazosPlanilla implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_carga_planilla")
    private Long idCargaPlanilla;

    @Column(name = "id_usuario_rechazo")
    private Long idUsuarioRechazo;

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    @Column(name = "etapa_rechazo", length = 100)
    private String etapaRechazo;

    @Column(name = "usuario_rechazo", length = 255)
    private String usuarioRechazo;

    @Column(name = "fecha_rechazo", insertable = false, updatable = false)
    private LocalDateTime fechaRechazo;

    // Constructors
    public SiproDetalleRechazosPlanilla() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIdCargaPlanilla() {
        return idCargaPlanilla;
    }

    public void setIdCargaPlanilla(Long idCargaPlanilla) {
        this.idCargaPlanilla = idCargaPlanilla;
    }

    public Long getIdUsuarioRechazo() {
        return idUsuarioRechazo;
    }

    public void setIdUsuarioRechazo(Long idUsuarioRechazo) {
        this.idUsuarioRechazo = idUsuarioRechazo;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public String getEtapaRechazo() {
        return etapaRechazo;
    }

    public void setEtapaRechazo(String etapaRechazo) {
        this.etapaRechazo = etapaRechazo;
    }

    public String getUsuarioRechazo() {
        return usuarioRechazo;
    }

    public void setUsuarioRechazo(String usuarioRechazo) {
        this.usuarioRechazo = usuarioRechazo;
    }

    public LocalDateTime getFechaRechazo() {
        return fechaRechazo;
    }

    public void setFechaRechazo(LocalDateTime fechaRechazo) {
        this.fechaRechazo = fechaRechazo;
    }
}
