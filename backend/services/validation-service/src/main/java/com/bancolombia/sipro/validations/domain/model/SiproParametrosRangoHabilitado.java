package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Entity que representa la tabla sipro_parametros_rango_habilitado.
 * Contiene la configuración del rango de meses permitidos para el selector de fecha de corte.
 */
@Entity
@Table(name = "sipro_parametros_rango_habilitado", schema = "public")
public class SiproParametrosRangoHabilitado implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "regla_id")
    private Long reglaId;

    @Column(name = "codigo", nullable = false, length = 50)
    private String codigo;

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    @Column(name = "meses_pasado", nullable = false)
    private Short mesesPasado;

    @Column(name = "meses_futuro", nullable = false)
    private Short mesesFuturo;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // Constructors
    public SiproParametrosRangoHabilitado() {
    }

    // Getters and Setters
    public Long getReglaId() {
        return reglaId;
    }

    public void setReglaId(Long reglaId) {
        this.reglaId = reglaId;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Short getMesesPasado() {
        return mesesPasado;
    }

    public void setMesesPasado(Short mesesPasado) {
        this.mesesPasado = mesesPasado;
    }

    public Short getMesesFuturo() {
        return mesesFuturo;
    }

    public void setMesesFuturo(Short mesesFuturo) {
        this.mesesFuturo = mesesFuturo;
    }

    public LocalDate getVigenteDesde() {
        return vigenteDesde;
    }

    public void setVigenteDesde(LocalDate vigenteDesde) {
        this.vigenteDesde = vigenteDesde;
    }

    public LocalDate getVigenteHasta() {
        return vigenteHasta;
    }

    public void setVigenteHasta(LocalDate vigenteHasta) {
        this.vigenteHasta = vigenteHasta;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
