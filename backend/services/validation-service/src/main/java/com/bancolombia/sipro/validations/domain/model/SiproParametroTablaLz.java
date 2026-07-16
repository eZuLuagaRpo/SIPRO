package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Configuracion de parametros de ingesta LZ.
 *
 * Cada parametro define el SQL completo que debe ejecutarse en LZ, la version
 * vigente y su referencia obligatoria al catalogo de tablas.
 */
@Entity
@Table(name = "sipro_parametros_tablas_lz")
public class SiproParametroTablaLz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_parametro")
    private Integer idParametro;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "query_sql", nullable = false, columnDefinition = "text")
    private String querySql;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    /** FK al catalogo de tablas. Resuelve tabla_destino_pg y tabla_staging_pg. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_tabla", referencedColumnName = "id_tabla", nullable = false)
    private SiproLzCatalogoTablas catalogoTabla;

    // ── Tabla destino/staging resueltas desde catalogo ──────────────────────

    @Transient
    public Integer getIdTabla() {
        return catalogoTabla == null ? null : catalogoTabla.getIdTabla();
    }

    @Transient
    public String getTablaOrigen() {
        return catalogoTabla == null ? null : catalogoTabla.getTablaOrigen();
    }

    /**
     * Nombre de la tabla final en Postgres, resuelto desde el catalogo.
     */
    public String getTablaDestinoPg() {
        return catalogoTabla == null ? null : catalogoTabla.resolveTablaDestinoPg();
    }

    /**
     * Nombre de la tabla staging en Postgres, resuelto desde el catalogo.
     */
    public String getTablaStagingPg() {
        return catalogoTabla == null ? null : catalogoTabla.resolveTablaStagingPg();
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Integer getIdParametro() { return idParametro; }
    public void setIdParametro(Integer idParametro) { this.idParametro = idParametro; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getQuerySql() { return querySql; }
    public void setQuerySql(String querySql) { this.querySql = querySql; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDateTime getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(LocalDateTime fechaModificacion) { this.fechaModificacion = fechaModificacion; }

    public SiproLzCatalogoTablas getCatalogoTabla() { return catalogoTabla; }
    public void setCatalogoTabla(SiproLzCatalogoTablas catalogoTabla) { this.catalogoTabla = catalogoTabla; }
}
