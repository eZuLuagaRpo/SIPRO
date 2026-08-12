package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sipro_parametros_homologacion_colgaap", schema = "public")
public class HomologacionColgaap {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "homologacion_colgaap_gen")
    @SequenceGenerator(name = "homologacion_colgaap_gen",
            sequenceName = "sipro_parametros_homologacion_colga_id_homologacion_colgaap_seq",
            allocationSize = 1)
    @Column(name = "id_homologacion_colgaap")
    private Long idHomologacionColgaap;

    @Column(name = "cuenta_sap", length = 20, nullable = false)
    private String cuentaSap;

    @Column(name = "cuenta_bv", length = 20, nullable = false)
    private String cuentaBv;

    @Column(name = "estado")
    private Integer estado;

    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "creado_por", length = 100)
    private String creadoPor;

    public Long getIdHomologacionColgaap() { return idHomologacionColgaap; }
    public void setIdHomologacionColgaap(Long idHomologacionColgaap) { this.idHomologacionColgaap = idHomologacionColgaap; }

    public String getCuentaSap() { return cuentaSap; }
    public void setCuentaSap(String cuentaSap) { this.cuentaSap = cuentaSap; }

    public String getCuentaBv() { return cuentaBv; }
    public void setCuentaBv(String cuentaBv) { this.cuentaBv = cuentaBv; }

    public Integer getEstado() { return estado; }
    public void setEstado(Integer estado) { this.estado = estado; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    public String getCreadoPor() { return creadoPor; }
    public void setCreadoPor(String creadoPor) { this.creadoPor = creadoPor; }
}
