package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * Entity que representa la tabla sipro_parametros_reglaventanacarga.
 * Contiene la regla general para calcular la ventana de apertura/cierre
 * de carga de información por periodo de valoración.
 */
@Entity
@Table(name = "sipro_parametros_reglaventanacarga", schema = "public")
public class SiproReglaVentanaCarga implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "regla_id")
    private Long reglaId;

    @Column(name = "offset_dias_apertura", nullable = false)
    private Integer offsetDiasApertura;

    @Column(name = "hora_apertura", nullable = false)
    private LocalTime horaApertura;

    @Column(name = "offset_dias_cierre", nullable = false)
    private Integer offsetDiasCierre;

    @Column(name = "hora_cierre", nullable = false)
    private LocalTime horaCierre;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    @Column(name = "activa", nullable = false)
    private Boolean activa;

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "modificado_en", nullable = false)
    private OffsetDateTime modificadoEn;

    @Column(name = "creado_por_id", nullable = false)
    private Long creadoPorId;

    @Column(name = "modificado_por_id")
    private Long modificadoPorId;

    @Column(name = "motivo_cambio")
    private String motivoCambio;

    public SiproReglaVentanaCarga() {
    }

    // Getters y Setters

    public Long getReglaId() { return reglaId; }
    public void setReglaId(Long reglaId) { this.reglaId = reglaId; }

    public Integer getOffsetDiasApertura() { return offsetDiasApertura; }
    public void setOffsetDiasApertura(Integer offsetDiasApertura) { this.offsetDiasApertura = offsetDiasApertura; }

    public LocalTime getHoraApertura() { return horaApertura; }
    public void setHoraApertura(LocalTime horaApertura) { this.horaApertura = horaApertura; }

    public Integer getOffsetDiasCierre() { return offsetDiasCierre; }
    public void setOffsetDiasCierre(Integer offsetDiasCierre) { this.offsetDiasCierre = offsetDiasCierre; }

    public LocalTime getHoraCierre() { return horaCierre; }
    public void setHoraCierre(LocalTime horaCierre) { this.horaCierre = horaCierre; }

    public LocalDate getVigenteDesde() { return vigenteDesde; }
    public void setVigenteDesde(LocalDate vigenteDesde) { this.vigenteDesde = vigenteDesde; }

    public LocalDate getVigenteHasta() { return vigenteHasta; }
    public void setVigenteHasta(LocalDate vigenteHasta) { this.vigenteHasta = vigenteHasta; }

    public Boolean getActiva() { return activa; }
    public void setActiva(Boolean activa) { this.activa = activa; }

    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(OffsetDateTime creadoEn) { this.creadoEn = creadoEn; }

    public OffsetDateTime getModificadoEn() { return modificadoEn; }
    public void setModificadoEn(OffsetDateTime modificadoEn) { this.modificadoEn = modificadoEn; }

    public Long getCreadoPorId() { return creadoPorId; }
    public void setCreadoPorId(Long creadoPorId) { this.creadoPorId = creadoPorId; }

    public Long getModificadoPorId() { return modificadoPorId; }
    public void setModificadoPorId(Long modificadoPorId) { this.modificadoPorId = modificadoPorId; }

    public String getMotivoCambio() { return motivoCambio; }
    public void setMotivoCambio(String motivoCambio) { this.motivoCambio = motivoCambio; }
}
