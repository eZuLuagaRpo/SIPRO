package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Resume los resultados de validación o consolidación agrupados por tipo de moneda.
 */
@Entity
@Table(name = "sipro_resumen_por_moneda", schema = "public")
public class SiproResumenPorMoneda implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_validacion", nullable = false)
    private Long idValidacion;

    @Column(name = "tipo_moneda")
    private String tipoMoneda;

    @Column(name = "codigo_moneda")
    private String codigoMoneda;

    @Column(name = "cantidad_registros")
    private Long cantidadRegistros;

    @Column(name = "suma_vlr_inicial_obligacion")
    private BigDecimal sumaVlrInicialObligacion;

    @Column(name = "fecha_calculo")
    private LocalDateTime fechaCalculo;

    public SiproResumenPorMoneda() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIdValidacion() {
        return idValidacion;
    }

    public void setIdValidacion(Long idValidacion) {
        this.idValidacion = idValidacion;
    }

    public String getTipoMoneda() {
        return tipoMoneda;
    }

    public void setTipoMoneda(String tipoMoneda) {
        this.tipoMoneda = tipoMoneda;
    }

    public String getCodigoMoneda() {
        return codigoMoneda;
    }

    public void setCodigoMoneda(String codigoMoneda) {
        this.codigoMoneda = codigoMoneda;
    }

    public Long getCantidadRegistros() {
        return cantidadRegistros;
    }

    public void setCantidadRegistros(Long cantidadRegistros) {
        this.cantidadRegistros = cantidadRegistros;
    }

    public BigDecimal getSumaVlrInicialObligacion() {
        return sumaVlrInicialObligacion;
    }

    public void setSumaVlrInicialObligacion(BigDecimal sumaVlrInicialObligacion) {
        this.sumaVlrInicialObligacion = sumaVlrInicialObligacion;
    }

    public LocalDateTime getFechaCalculo() {
        return fechaCalculo;
    }

    public void setFechaCalculo(LocalDateTime fechaCalculo) {
        this.fechaCalculo = fechaCalculo;
    }
}
