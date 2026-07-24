package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sipro_parametros_homologacion_colgaap", schema = "public")
public class HomologacionColgaap {

    @Id
    @Column(name = "cuenta_sap", length = 50)
    private String cuentaSap;

    @Column(name = "cuenta_bv", length = 50, nullable = false)
    private String cuentaBv;

    public String getCuentaSap() { return cuentaSap; }
    public void setCuentaSap(String cuentaSap) { this.cuentaSap = cuentaSap; }

    public String getCuentaBv() { return cuentaBv; }
    public void setCuentaBv(String cuentaBv) { this.cuentaBv = cuentaBv; }
}