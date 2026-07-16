package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * Entidad ligera mapeada a sipro_lz_mdm_datos_generales_clientes.
 * Se usa exclusivamente para validar existencia de clientes (NIT) en LZ.
 */
@Entity
@Table(name = "sipro_lz_mdm_datos_generales_clientes")
@IdClass(SiproLzMdmCliente.PK.class)
public class SiproLzMdmCliente {

    @Id
    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Id
    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Id
    @Column(name = "ingestion_run_id", nullable = false)
    private Long ingestionRunId;

    @Id
    @Column(name = "llave_mdm", nullable = false)
    private String llaveMdm;

    @Column(name = "numeroid_externo")
    private String numeroidExterno;

    @Column(name = "estado_cliente")
    private String estadoCliente;

    // ── Getters / Setters ──

    public Integer getPeriodYear() { return periodYear; }
    public void setPeriodYear(Integer periodYear) { this.periodYear = periodYear; }

    public Integer getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(Integer periodMonth) { this.periodMonth = periodMonth; }

    public Long getIngestionRunId() { return ingestionRunId; }
    public void setIngestionRunId(Long ingestionRunId) { this.ingestionRunId = ingestionRunId; }

    public String getLlaveMdm() { return llaveMdm; }
    public void setLlaveMdm(String llaveMdm) { this.llaveMdm = llaveMdm; }

    public String getNumeroidExterno() { return numeroidExterno; }
    public void setNumeroidExterno(String numeroidExterno) { this.numeroidExterno = numeroidExterno; }

    public String getEstadoCliente() { return estadoCliente; }
    public void setEstadoCliente(String estadoCliente) { this.estadoCliente = estadoCliente; }

    // ── Composite PK ──

    /**
     * Clave compuesta por periodo, corrida de ingesta y llave MDM del cliente.
     */
    public static class PK implements Serializable {
        private Integer periodYear;
        private Integer periodMonth;
        private Long ingestionRunId;
        private String llaveMdm;

        public PK() {}

        public PK(Integer periodYear, Integer periodMonth, Long ingestionRunId, String llaveMdm) {
            this.periodYear = periodYear;
            this.periodMonth = periodMonth;
            this.ingestionRunId = ingestionRunId;
            this.llaveMdm = llaveMdm;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PK pk = (PK) o;
            return Objects.equals(periodYear, pk.periodYear)
                && Objects.equals(periodMonth, pk.periodMonth)
                && Objects.equals(ingestionRunId, pk.ingestionRunId)
                && Objects.equals(llaveMdm, pk.llaveMdm);
        }

        @Override
        public int hashCode() {
            return Objects.hash(periodYear, periodMonth, ingestionRunId, llaveMdm);
        }
    }
}
