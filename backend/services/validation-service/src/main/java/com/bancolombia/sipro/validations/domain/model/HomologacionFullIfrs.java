package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sipro_parametros_homologacion_full_ifrs", schema = "public")
public class HomologacionFullIfrs {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "homologacion_full_ifrs_gen")
    @SequenceGenerator(name = "homologacion_full_ifrs_gen",
            sequenceName = "sipro_parametros_homologacion_full_ifrs_id_homologacion_full_ifrs_seq",
            allocationSize = 1)
    @Column(name = "id_homologacion_full_ifrs")
    private Long idHomologacionFullIfrs;

    @Column(name = "cuenta_planilla", length = 50, nullable = false)
    private String cuentaPlanilla;

    @Column(name = "cuenta_sap", length = 10, nullable = false)
    private String cuentaSap;

    @Column(name = "estado")
    private Integer estado;

    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "creado_por", length = 100)
    private String creadoPor;

    public Long getIdHomologacionFullIfrs() { return idHomologacionFullIfrs; }
    public void setIdHomologacionFullIfrs(Long v) { this.idHomologacionFullIfrs = v; }

    public String getCuentaPlanilla() { return cuentaPlanilla; }
    public void setCuentaPlanilla(String v) { this.cuentaPlanilla = v; }

    public String getCuentaSap() { return cuentaSap; }
    public void setCuentaSap(String v) { this.cuentaSap = v; }

    public Integer getEstado() { return estado; }
    public void setEstado(Integer v) { this.estado = v; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime v) { this.creadoEn = v; }

    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String v) { this.creadoPor = v; }
}
