package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Representa cada fila ya consolidada que alimenta resúmenes y comparaciones del periodo.
 */
@Entity
@Table(name = "sipro_detalle_consolidado_registros", schema = "public")
public class SiproDetalleConsolidadoRegistro implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_consolidado_registro")
    private Long idConsolidadoRegistro;

    @Column(name = "id_consolidacion", nullable = false)
    private Long idConsolidacion;

    @Column(name = "id_consolidacion_archivo", nullable = false)
    private Long idConsolidacionArchivo;

    @Column(name = "id_carga_planilla", nullable = false)
    private Long idCargaPlanilla;

    @Column(name = "tipo_id", length = 50)
    private String tipoId;

    @Column(name = "id_producto_origen")
    private Long idProductoOrigen;

    @Column(name = "producto_origen", length = 255)
    private String productoOrigen;

    @Column(name = "id_segmento")
    private Integer idSegmento;

    @Column(name = "segmento", length = 255)
    private String segmento;

    @Column(name = "fecha_corte")
    private LocalDate fechaCorte;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "id_usuario_cargador")
    private Long idUsuarioCargador;

    @Column(name = "usuario_cargador", length = 255)
    private String usuarioCargador;

    @Column(name = "id_usuario_aprobador")
    private Long idUsuarioAprobador;

    @Column(name = "usuario_aprobador", length = 255)
    private String usuarioAprobador;

    @Column(name = "nit")
    private Long nit;

    @Column(name = "oficina")
    private Long oficina;

    @Column(name = "documento")
    private Long documento;

    @Column(name = "moneda")
    private Integer moneda;

    @Column(name = "modalidad", length = 255)
    private String modalidad;

    @Column(name = "anoiniobl")
    private Integer anoiniobl;

    @Column(name = "mesiniobl")
    private Integer mesiniobl;

    @Column(name = "diainiobl")
    private Integer diainiobl;

    @Column(name = "anovcto")
    private Integer anovcto;

    @Column(name = "mesvcto")
    private Integer mesvcto;

    @Column(name = "diavcto")
    private Integer diavcto;

    @Column(name = "anovctofin")
    private Integer anovctofin;

    @Column(name = "mesvctofin")
    private Integer mesvctofin;

    @Column(name = "diavctofin")
    private Integer diavctofin;

    @Column(name = "ctapuc")
    private Long ctapuc;

    @Column(name = "vlriniobl")
    private BigDecimal vlriniobl;

    @Column(name = "saldo")
    private BigDecimal saldo;

    @Column(name = "sdootrctas")
    private BigDecimal sdootrctas;

    @Column(name = "intereses")
    private BigDecimal intereses;

    @Column(name = "sdovencido")
    private BigDecimal sdovencido;

    @Column(name = "intctasord")
    private BigDecimal intctasord;

    @Column(name = "usuario", length = 255)
    private String usuario;

    @Column(name = "producto", length = 255)
    private String producto;

    @Column(name = "clasificacion")
    private Short clasificacion;

    public Long getIdConsolidadoRegistro() {
        return idConsolidadoRegistro;
    }

    public void setIdConsolidadoRegistro(Long idConsolidadoRegistro) {
        this.idConsolidadoRegistro = idConsolidadoRegistro;
    }

    public Long getIdConsolidacion() {
        return idConsolidacion;
    }

    public void setIdConsolidacion(Long idConsolidacion) {
        this.idConsolidacion = idConsolidacion;
    }

    public Long getIdConsolidacionArchivo() {
        return idConsolidacionArchivo;
    }

    public void setIdConsolidacionArchivo(Long idConsolidacionArchivo) {
        this.idConsolidacionArchivo = idConsolidacionArchivo;
    }

    public Long getIdCargaPlanilla() {
        return idCargaPlanilla;
    }

    public void setIdCargaPlanilla(Long idCargaPlanilla) {
        this.idCargaPlanilla = idCargaPlanilla;
    }

    public String getTipoId() {
        return tipoId;
    }

    public void setTipoId(String tipoId) {
        this.tipoId = tipoId;
    }

    public Long getIdProductoOrigen() {
        return idProductoOrigen;
    }

    public void setIdProductoOrigen(Long idProductoOrigen) {
        this.idProductoOrigen = idProductoOrigen;
    }

    public String getProductoOrigen() {
        return productoOrigen;
    }

    public void setProductoOrigen(String productoOrigen) {
        this.productoOrigen = productoOrigen;
    }

    public Integer getIdSegmento() {
        return idSegmento;
    }

    public void setIdSegmento(Integer idSegmento) {
        this.idSegmento = idSegmento;
    }

    public String getSegmento() {
        return segmento;
    }

    public void setSegmento(String segmento) {
        this.segmento = segmento;
    }

    public LocalDate getFechaCorte() {
        return fechaCorte;
    }

    public void setFechaCorte(LocalDate fechaCorte) {
        this.fechaCorte = fechaCorte;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Long getIdUsuarioCargador() {
        return idUsuarioCargador;
    }

    public void setIdUsuarioCargador(Long idUsuarioCargador) {
        this.idUsuarioCargador = idUsuarioCargador;
    }

    public String getUsuarioCargador() {
        return usuarioCargador;
    }

    public void setUsuarioCargador(String usuarioCargador) {
        this.usuarioCargador = usuarioCargador;
    }

    public Long getIdUsuarioAprobador() {
        return idUsuarioAprobador;
    }

    public void setIdUsuarioAprobador(Long idUsuarioAprobador) {
        this.idUsuarioAprobador = idUsuarioAprobador;
    }

    public String getUsuarioAprobador() {
        return usuarioAprobador;
    }

    public void setUsuarioAprobador(String usuarioAprobador) {
        this.usuarioAprobador = usuarioAprobador;
    }

    public Long getNit() {
        return nit;
    }

    public void setNit(Long nit) {
        this.nit = nit;
    }

    public Long getOficina() {
        return oficina;
    }

    public void setOficina(Long oficina) {
        this.oficina = oficina;
    }

    public Long getDocumento() {
        return documento;
    }

    public void setDocumento(Long documento) {
        this.documento = documento;
    }

    public Integer getMoneda() {
        return moneda;
    }

    public void setMoneda(Integer moneda) {
        this.moneda = moneda;
    }

    public String getModalidad() {
        return modalidad;
    }

    public void setModalidad(String modalidad) {
        this.modalidad = modalidad;
    }

    public Integer getAnoiniobl() {
        return anoiniobl;
    }

    public void setAnoiniobl(Integer anoiniobl) {
        this.anoiniobl = anoiniobl;
    }

    public Integer getMesiniobl() {
        return mesiniobl;
    }

    public void setMesiniobl(Integer mesiniobl) {
        this.mesiniobl = mesiniobl;
    }

    public Integer getDiainiobl() {
        return diainiobl;
    }

    public void setDiainiobl(Integer diainiobl) {
        this.diainiobl = diainiobl;
    }

    public Integer getAnovcto() {
        return anovcto;
    }

    public void setAnovcto(Integer anovcto) {
        this.anovcto = anovcto;
    }

    public Integer getMesvcto() {
        return mesvcto;
    }

    public void setMesvcto(Integer mesvcto) {
        this.mesvcto = mesvcto;
    }

    public Integer getDiavcto() {
        return diavcto;
    }

    public void setDiavcto(Integer diavcto) {
        this.diavcto = diavcto;
    }

    public Integer getAnovctofin() {
        return anovctofin;
    }

    public void setAnovctofin(Integer anovctofin) {
        this.anovctofin = anovctofin;
    }

    public Integer getMesvctofin() {
        return mesvctofin;
    }

    public void setMesvctofin(Integer mesvctofin) {
        this.mesvctofin = mesvctofin;
    }

    public Integer getDiavctofin() {
        return diavctofin;
    }

    public void setDiavctofin(Integer diavctofin) {
        this.diavctofin = diavctofin;
    }

    public Long getCtapuc() {
        return ctapuc;
    }

    public void setCtapuc(Long ctapuc) {
        this.ctapuc = ctapuc;
    }

    public BigDecimal getVlriniobl() {
        return vlriniobl;
    }

    public void setVlriniobl(BigDecimal vlriniobl) {
        this.vlriniobl = vlriniobl;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }

    public BigDecimal getSdootrctas() {
        return sdootrctas;
    }

    public void setSdootrctas(BigDecimal sdootrctas) {
        this.sdootrctas = sdootrctas;
    }

    public BigDecimal getIntereses() {
        return intereses;
    }

    public void setIntereses(BigDecimal intereses) {
        this.intereses = intereses;
    }

    public BigDecimal getSdovencido() {
        return sdovencido;
    }

    public void setSdovencido(BigDecimal sdovencido) {
        this.sdovencido = sdovencido;
    }

    public BigDecimal getIntctasord() {
        return intctasord;
    }

    public void setIntctasord(BigDecimal intctasord) {
        this.intctasord = intctasord;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getProducto() {
        return producto;
    }

    public void setProducto(String producto) {
        this.producto = producto;
    }

    public Short getClasificacion() {
        return clasificacion;
    }

    public void setClasificacion(Short clasificacion) {
        this.clasificacion = clasificacion;
    }
}