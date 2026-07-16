package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Catalogo de tablas LZ registradas en el sistema.
 *
 * Cada entrada representa una tabla de Impala (LZ) y sus correspondientes
 * tablas destino en PostgreSQL (final + staging).
 *
 * Tabla BD: sipro_lz_catalogo_tablas
 *
 * Columnas:
 *   id_tabla           SERIAL PK
 *   tabla_origen       TEXT NOT NULL   — nombre completo en Impala (schema.tabla)
 *   tabla_destino_pg   TEXT           — tabla final en Postgres (auto-populated si NULL)
 *   tabla_staging_pg   TEXT           — tabla staging en Postgres (auto-populated si NULL)
 *   activo             BOOLEAN        — si la tabla esta habilitada para ingesta
 *   fecha_creacion     TIMESTAMP
 *   fecha_modificacion TIMESTAMP
 */
@Entity
@Table(name = "sipro_lz_catalogo_tablas")
public class SiproLzCatalogoTablas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tabla")
    private Integer idTabla;

    @Column(name = "tabla_origen", nullable = false, columnDefinition = "text")
    private String tablaOrigen;

    /** Nombre de la tabla final en Postgres. Si NULL en BD, se deriva por convencion. */
    @Column(name = "tabla_destino_pg", columnDefinition = "text")
    private String tablaDestinoPg;

    /** Nombre de la tabla staging en Postgres. Si NULL en BD, se deriva por convencion. */
    @Column(name = "tabla_staging_pg", columnDefinition = "text")
    private String tablaStagingPg;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    // ── Metodos derivados ──────────────────────────────────────────────────

    /**
     * Nombre normalizado de tabla_origen (lowercase, trimmed).
     * Util para busquedas case-insensitive.
     */
    @Transient
    public String getTablaOrigenNorm() {
        return tablaOrigen == null ? null : tablaOrigen.trim().toLowerCase();
    }

    /**
     * Devuelve tabla_destino_pg si esta configurada en BD.
     * Si es NULL, deriva por convencion: sipro_lz_ + parte despues del ultimo punto.
     */
    public String resolveTablaDestinoPg() {
        if (tablaDestinoPg != null && !tablaDestinoPg.isBlank()) {
            return tablaDestinoPg.trim();
        }
        return derivarNombreTabla(tablaOrigen, false);
    }

    /**
     * Devuelve tabla_staging_pg si esta configurada en BD.
     * Si es NULL, deriva por convencion: sipro_lz_ + parte despues del ultimo punto + _stg.
     */
    public String resolveTablaStagingPg() {
        if (tablaStagingPg != null && !tablaStagingPg.isBlank()) {
            return tablaStagingPg.trim();
        }
        return derivarNombreTabla(tablaOrigen, true);
    }

    /**
     * Convencion de nombres: schema.tabla_nombre → sipro_lz_tabla_nombre[_stg]
     * Solo se usa como fallback si tabla_destino_pg/tabla_staging_pg son NULL.
     */
    private static String derivarNombreTabla(String tablaOrigen, boolean staging) {
        if (tablaOrigen == null) return null;
        String name = tablaOrigen.trim().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        String base = "sipro_lz_" + name;
        return staging ? base + "_stg" : base;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Integer getIdTabla() { return idTabla; }
    public void setIdTabla(Integer idTabla) { this.idTabla = idTabla; }

    public String getTablaOrigen() { return tablaOrigen; }
    public void setTablaOrigen(String tablaOrigen) { this.tablaOrigen = tablaOrigen; }

    public String getTablaDestinoPg() { return tablaDestinoPg; }
    public void setTablaDestinoPg(String tablaDestinoPg) { this.tablaDestinoPg = tablaDestinoPg; }

    public String getTablaStagingPg() { return tablaStagingPg; }
    public void setTablaStagingPg(String tablaStagingPg) { this.tablaStagingPg = tablaStagingPg; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDateTime getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(LocalDateTime fechaModificacion) { this.fechaModificacion = fechaModificacion; }
}
