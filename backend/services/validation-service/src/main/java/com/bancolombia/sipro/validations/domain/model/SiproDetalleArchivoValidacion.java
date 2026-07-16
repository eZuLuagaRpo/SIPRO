package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Guarda métricas técnicas y de calidad calculadas durante la validación de un archivo.
 */
@Entity
@Table(name = "sipro_detalle_archivo_validacion")
public class SiproDetalleArchivoValidacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_carga_planilla", nullable = false)
    private Long idCargaPlanilla;

    // Metadatos del archivo
    @Column(name = "numero_filas_datos", nullable = false)
    private Integer numeroFilasDatos;

    @Column(name = "numero_columnas", nullable = false)
    private Integer numeroColumnas;

    @Column(name = "numero_columnas_numericas")
    private Integer numeroColumnasNumericas;

    @Column(name = "numero_columnas_texto")
    private Integer numeroColumnasTexto;

    // Validaciones de calidad
    @Column(name = "total_valores_nulos")
    private Integer totalValoresNulos;

    @Column(name = "porcentaje_completitud")
    private BigDecimal porcentajeCompletitud;

    @Column(name = "tiene_encabezados")
    private Boolean tieneEncabezados;

    // Información de estructura
    @Column(name = "nombres_columnas", columnDefinition = "TEXT")
    private String nombresColumnas;

    @Column(name = "columnas_numericas", columnDefinition = "TEXT")
    private String columnasNumericas;

    @Column(name = "columnas_texto", columnDefinition = "TEXT")
    private String columnasTexto;

    // Validaciones de negocio
    @Column(name = "registros_validados")
    private Integer registrosValidados;

    @Column(name = "registros_rechazados")
    private Integer registrosRechazados;

    @Column(name = "errores_validacion", columnDefinition = "TEXT")
    private String erroresValidacion;

    // Archivo control Full IFRS (segmento 2)
    @Column(name = "cantidad_registros_control")
    private Integer cantidadRegistrosControl;

    // Rangos de datos financieros
    @Column(name = "saldo_minimo")
    private BigDecimal saldoMinimo;

    @Column(name = "saldo_maximo")
    private BigDecimal saldoMaximo;

    @Column(name = "saldo_total")
    private BigDecimal saldoTotal;

    @Column(name = "intereses_total")
    private BigDecimal interesesTotal;

    // Metadata técnica
    @Column(name = "tamano_memoria_bytes")
    private Long tamanoMemoriaBytes;

    @Column(name = "formato_fecha_detectado")
    private String formatoFechaDetectado;

    @Column(name = "charset_detectado")
    private String charsetDetectado;

    // Auditoría
    @Column(name = "fecha_validacion")
    private LocalDateTime fechaValidacion;

    @Column(name = "id_usuario_validacion")
    private Long idUsuarioValidacion;

    @Column(name = "usuario_validacion")
    private String usuarioValidacion;

    @Column(name = "estado_validacion")
    private String estadoValidacion;

    // Getters y Setters
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

    public Integer getNumeroFilasDatos() {
        return numeroFilasDatos;
    }

    public void setNumeroFilasDatos(Integer numeroFilasDatos) {
        this.numeroFilasDatos = numeroFilasDatos;
    }

    public Integer getNumeroColumnas() {
        return numeroColumnas;
    }

    public void setNumeroColumnas(Integer numeroColumnas) {
        this.numeroColumnas = numeroColumnas;
    }

    public Integer getNumeroColumnasNumericas() {
        return numeroColumnasNumericas;
    }

    public void setNumeroColumnasNumericas(Integer numeroColumnasNumericas) {
        this.numeroColumnasNumericas = numeroColumnasNumericas;
    }

    public Integer getNumeroColumnasTexto() {
        return numeroColumnasTexto;
    }

    public void setNumeroColumnasTexto(Integer numeroColumnasTexto) {
        this.numeroColumnasTexto = numeroColumnasTexto;
    }

    public Integer getTotalValoresNulos() {
        return totalValoresNulos;
    }

    public void setTotalValoresNulos(Integer totalValoresNulos) {
        this.totalValoresNulos = totalValoresNulos;
    }

    public BigDecimal getPorcentajeCompletitud() {
        return porcentajeCompletitud;
    }

    public void setPorcentajeCompletitud(BigDecimal porcentajeCompletitud) {
        this.porcentajeCompletitud = porcentajeCompletitud;
    }

    public Boolean getTieneEncabezados() {
        return tieneEncabezados;
    }

    public void setTieneEncabezados(Boolean tieneEncabezados) {
        this.tieneEncabezados = tieneEncabezados;
    }

    public String getNombresColumnas() {
        return nombresColumnas;
    }

    public void setNombresColumnas(String nombresColumnas) {
        this.nombresColumnas = nombresColumnas;
    }

    public String getColumnasNumericas() {
        return columnasNumericas;
    }

    public void setColumnasNumericas(String columnasNumericas) {
        this.columnasNumericas = columnasNumericas;
    }

    public String getColumnasTexto() {
        return columnasTexto;
    }

    public void setColumnasTexto(String columnasTexto) {
        this.columnasTexto = columnasTexto;
    }

    public Integer getRegistrosValidados() {
        return registrosValidados;
    }

    public void setRegistrosValidados(Integer registrosValidados) {
        this.registrosValidados = registrosValidados;
    }

    public Integer getRegistrosRechazados() {
        return registrosRechazados;
    }

    public void setRegistrosRechazados(Integer registrosRechazados) {
        this.registrosRechazados = registrosRechazados;
    }

    public String getErroresValidacion() {
        return erroresValidacion;
    }

    public void setErroresValidacion(String erroresValidacion) {
        this.erroresValidacion = erroresValidacion;
    }

    public BigDecimal getSaldoMinimo() {
        return saldoMinimo;
    }

    public void setSaldoMinimo(BigDecimal saldoMinimo) {
        this.saldoMinimo = saldoMinimo;
    }

    public BigDecimal getSaldoMaximo() {
        return saldoMaximo;
    }

    public void setSaldoMaximo(BigDecimal saldoMaximo) {
        this.saldoMaximo = saldoMaximo;
    }

    public BigDecimal getSaldoTotal() {
        return saldoTotal;
    }

    public void setSaldoTotal(BigDecimal saldoTotal) {
        this.saldoTotal = saldoTotal;
    }

    public BigDecimal getInteresesTotal() {
        return interesesTotal;
    }

    public void setInteresesTotal(BigDecimal interesesTotal) {
        this.interesesTotal = interesesTotal;
    }

    public Long getTamanoMemoriaBytes() {
        return tamanoMemoriaBytes;
    }

    public void setTamanoMemoriaBytes(Long tamanoMemoriaBytes) {
        this.tamanoMemoriaBytes = tamanoMemoriaBytes;
    }

    public String getFormatoFechaDetectado() {
        return formatoFechaDetectado;
    }

    public void setFormatoFechaDetectado(String formatoFechaDetectado) {
        this.formatoFechaDetectado = formatoFechaDetectado;
    }

    public String getCharsetDetectado() {
        return charsetDetectado;
    }

    public void setCharsetDetectado(String charsetDetectado) {
        this.charsetDetectado = charsetDetectado;
    }

    public LocalDateTime getFechaValidacion() {
        return fechaValidacion;
    }

    public void setFechaValidacion(LocalDateTime fechaValidacion) {
        this.fechaValidacion = fechaValidacion;
    }

    public Long getIdUsuarioValidacion() {
        return idUsuarioValidacion;
    }

    public void setIdUsuarioValidacion(Long idUsuarioValidacion) {
        this.idUsuarioValidacion = idUsuarioValidacion;
    }

    public String getUsuarioValidacion() {
        return usuarioValidacion;
    }

    public void setUsuarioValidacion(String usuarioValidacion) {
        this.usuarioValidacion = usuarioValidacion;
    }

    public String getEstadoValidacion() {
        return estadoValidacion;
    }

    public void setEstadoValidacion(String estadoValidacion) {
        this.estadoValidacion = estadoValidacion;
    }

    public Integer getCantidadRegistrosControl() {
        return cantidadRegistrosControl;
    }

    public void setCantidadRegistrosControl(Integer cantidadRegistrosControl) {
        this.cantidadRegistrosControl = cantidadRegistrosControl;
    }
}
