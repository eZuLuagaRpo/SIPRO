package com.bancolombia.sipro.validations.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO del resumen consolidado por periodo para la vista operativa de resumen.
 */
public class ConsolidacionResumenResponse {

    private List<PeriodoAnual> periodosDisponibles = new ArrayList<>();
    private int anioSeleccionado;
    private int mesSeleccionado;
    private String periodoEtiqueta;
    private boolean hayDatos;
    private String estadoConsolidacion;
    private int cantidadArchivosConsolidados;
    private long cantidadRegistrosConsolidados;
    private long cantidadRegistrosArchivoControl;
    private BigDecimal totalVlrIniObl;
    private long registrosObservados;
    private int productosObservados;
    private OffsetDateTime fechaActualizacion;
    private AlertaResumen alerta;
    private CreffosArchivoResumen creffos;
    private boolean tieneDiferenciasConciliacion;
    private int metricasConDiferencia;
    private List<ComparacionMetricaResumen> metricasComparacion = new ArrayList<>();
    private List<ProductoResumen> productos = new ArrayList<>();

    public List<PeriodoAnual> getPeriodosDisponibles() {
        return periodosDisponibles;
    }

    public void setPeriodosDisponibles(List<PeriodoAnual> periodosDisponibles) {
        this.periodosDisponibles = periodosDisponibles;
    }

    public int getAnioSeleccionado() {
        return anioSeleccionado;
    }

    public void setAnioSeleccionado(int anioSeleccionado) {
        this.anioSeleccionado = anioSeleccionado;
    }

    public int getMesSeleccionado() {
        return mesSeleccionado;
    }

    public void setMesSeleccionado(int mesSeleccionado) {
        this.mesSeleccionado = mesSeleccionado;
    }

    public String getPeriodoEtiqueta() {
        return periodoEtiqueta;
    }

    public void setPeriodoEtiqueta(String periodoEtiqueta) {
        this.periodoEtiqueta = periodoEtiqueta;
    }

    public boolean isHayDatos() {
        return hayDatos;
    }

    public void setHayDatos(boolean hayDatos) {
        this.hayDatos = hayDatos;
    }

    public String getEstadoConsolidacion() {
        return estadoConsolidacion;
    }

    public void setEstadoConsolidacion(String estadoConsolidacion) {
        this.estadoConsolidacion = estadoConsolidacion;
    }

    public int getCantidadArchivosConsolidados() {
        return cantidadArchivosConsolidados;
    }

    public void setCantidadArchivosConsolidados(int cantidadArchivosConsolidados) {
        this.cantidadArchivosConsolidados = cantidadArchivosConsolidados;
    }

    public long getCantidadRegistrosConsolidados() {
        return cantidadRegistrosConsolidados;
    }

    public void setCantidadRegistrosConsolidados(long cantidadRegistrosConsolidados) {
        this.cantidadRegistrosConsolidados = cantidadRegistrosConsolidados;
    }

    public long getCantidadRegistrosArchivoControl() {
        return cantidadRegistrosArchivoControl;
    }

    public void setCantidadRegistrosArchivoControl(long cantidadRegistrosArchivoControl) {
        this.cantidadRegistrosArchivoControl = cantidadRegistrosArchivoControl;
    }

    public BigDecimal getTotalVlrIniObl() {
        return totalVlrIniObl;
    }

    public void setTotalVlrIniObl(BigDecimal totalVlrIniObl) {
        this.totalVlrIniObl = totalVlrIniObl;
    }

    public long getRegistrosObservados() {
        return registrosObservados;
    }

    public void setRegistrosObservados(long registrosObservados) {
        this.registrosObservados = registrosObservados;
    }

    public int getProductosObservados() {
        return productosObservados;
    }

    public void setProductosObservados(int productosObservados) {
        this.productosObservados = productosObservados;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(OffsetDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public AlertaResumen getAlerta() {
        return alerta;
    }

    public void setAlerta(AlertaResumen alerta) {
        this.alerta = alerta;
    }

    public CreffosArchivoResumen getCreffos() {
        return creffos;
    }

    public void setCreffos(CreffosArchivoResumen creffos) {
        this.creffos = creffos;
    }

    public boolean isTieneDiferenciasConciliacion() {
        return tieneDiferenciasConciliacion;
    }

    public void setTieneDiferenciasConciliacion(boolean tieneDiferenciasConciliacion) {
        this.tieneDiferenciasConciliacion = tieneDiferenciasConciliacion;
    }

    public int getMetricasConDiferencia() {
        return metricasConDiferencia;
    }

    public void setMetricasConDiferencia(int metricasConDiferencia) {
        this.metricasConDiferencia = metricasConDiferencia;
    }

    public List<ComparacionMetricaResumen> getMetricasComparacion() {
        return metricasComparacion;
    }

    public void setMetricasComparacion(List<ComparacionMetricaResumen> metricasComparacion) {
        this.metricasComparacion = metricasComparacion;
    }

    public List<ProductoResumen> getProductos() {
        return productos;
    }

    public void setProductos(List<ProductoResumen> productos) {
        this.productos = productos;
    }

    public static class PeriodoAnual {
        private int anio;
        private List<MesDisponible> meses = new ArrayList<>();

        public PeriodoAnual() {
        }

        public PeriodoAnual(int anio, List<MesDisponible> meses) {
            this.anio = anio;
            this.meses = meses;
        }

        public int getAnio() {
            return anio;
        }

        public void setAnio(int anio) {
            this.anio = anio;
        }

        public List<MesDisponible> getMeses() {
            return meses;
        }

        public void setMeses(List<MesDisponible> meses) {
            this.meses = meses;
        }
    }

    public static class MesDisponible {
        private int numero;
        private String etiqueta;
        private String abreviatura;
        private String periodo;

        public MesDisponible() {
        }

        public MesDisponible(int numero, String etiqueta, String abreviatura, String periodo) {
            this.numero = numero;
            this.etiqueta = etiqueta;
            this.abreviatura = abreviatura;
            this.periodo = periodo;
        }

        public int getNumero() {
            return numero;
        }

        public void setNumero(int numero) {
            this.numero = numero;
        }

        public String getEtiqueta() {
            return etiqueta;
        }

        public void setEtiqueta(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String getAbreviatura() {
            return abreviatura;
        }

        public void setAbreviatura(String abreviatura) {
            this.abreviatura = abreviatura;
        }

        public String getPeriodo() {
            return periodo;
        }

        public void setPeriodo(String periodo) {
            this.periodo = periodo;
        }
    }

    public static class AlertaResumen {
        private String tipo;
        private String titulo;
        private String mensaje;

        public AlertaResumen() {
        }

        public AlertaResumen(String tipo, String titulo, String mensaje) {
            this.tipo = tipo;
            this.titulo = titulo;
            this.mensaje = mensaje;
        }

        public String getTipo() {
            return tipo;
        }

        public void setTipo(String tipo) {
            this.tipo = tipo;
        }

        public String getTitulo() {
            return titulo;
        }

        public void setTitulo(String titulo) {
            this.titulo = titulo;
        }

        public String getMensaje() {
            return mensaje;
        }

        public void setMensaje(String mensaje) {
            this.mensaje = mensaje;
        }
    }

    public static class CreffosArchivoResumen {
        private boolean encontrado;
        private String nombreArchivo;
        private String formato;
        private String estado;
        private String origenLectura;
        private String ubicacion;
        private int cantidadColumnasEsperadas;
        private int cantidadColumnasArchivo;
        private long cantidadRegistros;
        private BigDecimal totalVlrIniObl;
        private String detalle;

        public CreffosArchivoResumen() {
        }

        public CreffosArchivoResumen(boolean encontrado,
                                     String nombreArchivo,
                                     String formato,
                                     String estado,
                                     String origenLectura,
                                     String ubicacion,
                                     int cantidadColumnasEsperadas,
                                     int cantidadColumnasArchivo,
                                     long cantidadRegistros,
                                     BigDecimal totalVlrIniObl,
                                     String detalle) {
            this.encontrado = encontrado;
            this.nombreArchivo = nombreArchivo;
            this.formato = formato;
            this.estado = estado;
            this.origenLectura = origenLectura;
            this.ubicacion = ubicacion;
            this.cantidadColumnasEsperadas = cantidadColumnasEsperadas;
            this.cantidadColumnasArchivo = cantidadColumnasArchivo;
            this.cantidadRegistros = cantidadRegistros;
            this.totalVlrIniObl = totalVlrIniObl;
            this.detalle = detalle;
        }

        public boolean isEncontrado() {
            return encontrado;
        }

        public void setEncontrado(boolean encontrado) {
            this.encontrado = encontrado;
        }

        public String getNombreArchivo() {
            return nombreArchivo;
        }

        public void setNombreArchivo(String nombreArchivo) {
            this.nombreArchivo = nombreArchivo;
        }

        public String getFormato() {
            return formato;
        }

        public void setFormato(String formato) {
            this.formato = formato;
        }

        public String getEstado() {
            return estado;
        }

        public void setEstado(String estado) {
            this.estado = estado;
        }

        public String getOrigenLectura() {
            return origenLectura;
        }

        public void setOrigenLectura(String origenLectura) {
            this.origenLectura = origenLectura;
        }

        public String getUbicacion() {
            return ubicacion;
        }

        public void setUbicacion(String ubicacion) {
            this.ubicacion = ubicacion;
        }

        public int getCantidadColumnasEsperadas() {
            return cantidadColumnasEsperadas;
        }

        public void setCantidadColumnasEsperadas(int cantidadColumnasEsperadas) {
            this.cantidadColumnasEsperadas = cantidadColumnasEsperadas;
        }

        public int getCantidadColumnasArchivo() {
            return cantidadColumnasArchivo;
        }

        public void setCantidadColumnasArchivo(int cantidadColumnasArchivo) {
            this.cantidadColumnasArchivo = cantidadColumnasArchivo;
        }

        public long getCantidadRegistros() {
            return cantidadRegistros;
        }

        public void setCantidadRegistros(long cantidadRegistros) {
            this.cantidadRegistros = cantidadRegistros;
        }

        public BigDecimal getTotalVlrIniObl() {
            return totalVlrIniObl;
        }

        public void setTotalVlrIniObl(BigDecimal totalVlrIniObl) {
            this.totalVlrIniObl = totalVlrIniObl;
        }

        public String getDetalle() {
            return detalle;
        }

        public void setDetalle(String detalle) {
            this.detalle = detalle;
        }
    }

    public static class ComparacionMetricaResumen {
        private String codigo;
        private String etiqueta;
        private String tipoValor;
        private BigDecimal valorPostgres;
        private BigDecimal valorCreffos;
        private BigDecimal diferencia;
        private boolean coincide;

        public ComparacionMetricaResumen() {
        }

        public ComparacionMetricaResumen(String codigo,
                                         String etiqueta,
                                         String tipoValor,
                                         BigDecimal valorPostgres,
                                         BigDecimal valorCreffos,
                                         BigDecimal diferencia,
                                         boolean coincide) {
            this.codigo = codigo;
            this.etiqueta = etiqueta;
            this.tipoValor = tipoValor;
            this.valorPostgres = valorPostgres;
            this.valorCreffos = valorCreffos;
            this.diferencia = diferencia;
            this.coincide = coincide;
        }

        public String getCodigo() {
            return codigo;
        }

        public void setCodigo(String codigo) {
            this.codigo = codigo;
        }

        public String getEtiqueta() {
            return etiqueta;
        }

        public void setEtiqueta(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String getTipoValor() {
            return tipoValor;
        }

        public void setTipoValor(String tipoValor) {
            this.tipoValor = tipoValor;
        }

        public BigDecimal getValorPostgres() {
            return valorPostgres;
        }

        public void setValorPostgres(BigDecimal valorPostgres) {
            this.valorPostgres = valorPostgres;
        }

        public BigDecimal getValorCreffos() {
            return valorCreffos;
        }

        public void setValorCreffos(BigDecimal valorCreffos) {
            this.valorCreffos = valorCreffos;
        }

        public BigDecimal getDiferencia() {
            return diferencia;
        }

        public void setDiferencia(BigDecimal diferencia) {
            this.diferencia = diferencia;
        }

        public boolean isCoincide() {
            return coincide;
        }

        public void setCoincide(boolean coincide) {
            this.coincide = coincide;
        }
    }

    public static class ProductoResumen {
        private Long idProducto;
        private String nombreProducto;
        private long cantidadRegistros;
        private BigDecimal totalVlrIniObl;
        private long cantidadRegistrosFullIfrs;
        private long registrosObservados;
        private boolean tieneDiscrepancias;

        public ProductoResumen() {
        }

        public ProductoResumen(Long idProducto, String nombreProducto, long cantidadRegistros,
                               BigDecimal totalVlrIniObl, long cantidadRegistrosFullIfrs,
                               long registrosObservados, boolean tieneDiscrepancias) {
            this.idProducto = idProducto;
            this.nombreProducto = nombreProducto;
            this.cantidadRegistros = cantidadRegistros;
            this.totalVlrIniObl = totalVlrIniObl;
            this.cantidadRegistrosFullIfrs = cantidadRegistrosFullIfrs;
            this.registrosObservados = registrosObservados;
            this.tieneDiscrepancias = tieneDiscrepancias;
        }

        public Long getIdProducto() {
            return idProducto;
        }

        public void setIdProducto(Long idProducto) {
            this.idProducto = idProducto;
        }

        public String getNombreProducto() {
            return nombreProducto;
        }

        public void setNombreProducto(String nombreProducto) {
            this.nombreProducto = nombreProducto;
        }

        public long getCantidadRegistros() {
            return cantidadRegistros;
        }

        public void setCantidadRegistros(long cantidadRegistros) {
            this.cantidadRegistros = cantidadRegistros;
        }

        public BigDecimal getTotalVlrIniObl() {
            return totalVlrIniObl;
        }

        public void setTotalVlrIniObl(BigDecimal totalVlrIniObl) {
            this.totalVlrIniObl = totalVlrIniObl;
        }

        public long getCantidadRegistrosFullIfrs() {
            return cantidadRegistrosFullIfrs;
        }

        public void setCantidadRegistrosFullIfrs(long cantidadRegistrosFullIfrs) {
            this.cantidadRegistrosFullIfrs = cantidadRegistrosFullIfrs;
        }

        public long getRegistrosObservados() {
            return registrosObservados;
        }

        public void setRegistrosObservados(long registrosObservados) {
            this.registrosObservados = registrosObservados;
        }

        public boolean isTieneDiscrepancias() {
            return tieneDiscrepancias;
        }

        public void setTieneDiscrepancias(boolean tieneDiscrepancias) {
            this.tieneDiscrepancias = tieneDiscrepancias;
        }
    }
}