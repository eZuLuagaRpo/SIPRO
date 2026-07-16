package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Entidad que representa la tabla de catálogo de roles y permisos del sistema SIPRO.
 * Cada rol define un conjunto de permisos (cargar, aprobar, visualizar, etc.).
 */
@Entity
@Table(name = "sipro_roles_permisos")
public class SiproRolesPermisos implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Integer idRol;

    @Column(name = "rol", length = 100)
    private String rol;

    @Column(name = "grupo_ad", length = 100)
    private String grupoAd;

    @Column(name = "perfil", length = 50)
    private String perfil;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    @Column(name = "visualizar")
    private Short visualizar;

    @Column(name = "cargar_archivos")
    private Short cargarArchivos;

    @Column(name = "diligenciar_formulario")
    private Short diligenciarFormulario;

    @Column(name = "solicitar_aprobacion")
    private Short solicitarAprobacion;

    @Column(name = "monitoreo_auditoria")
    private Short monitoreoAuditoria;

    @Column(name = "aprobar")
    private Short aprobar;

    @Column(name = "modificar_parametros")
    private Short modificarParametros;

    @Column(name = "exportar_reportes")
    private Short exportarReportes;

    // Constructores
    public SiproRolesPermisos() {
    }

    // Métodos de consulta de permisos
    public boolean puedeCargar() {
        return cargarArchivos != null && cargarArchivos == 1;
    }

    public boolean puedeAprobar() {
        return aprobar != null && aprobar == 1;
    }

    public boolean puedeSolicitarAprobacion() {
        return solicitarAprobacion != null && solicitarAprobacion == 1;
    }

    public boolean puedeVisualizar() {
        return visualizar != null && visualizar == 1;
    }

    // Getters y Setters
    public Integer getIdRol() {
        return idRol;
    }

    public void setIdRol(Integer idRol) {
        this.idRol = idRol;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getGrupoAd() {
        return grupoAd;
    }

    public void setGrupoAd(String grupoAd) {
        this.grupoAd = grupoAd;
    }

    public String getPerfil() {
        return perfil;
    }

    public void setPerfil(String perfil) {
        this.perfil = perfil;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Short getVisualizar() {
        return visualizar;
    }

    public void setVisualizar(Short visualizar) {
        this.visualizar = visualizar;
    }

    public Short getCargarArchivos() {
        return cargarArchivos;
    }

    public void setCargarArchivos(Short cargarArchivos) {
        this.cargarArchivos = cargarArchivos;
    }

    public Short getDiligenciarFormulario() {
        return diligenciarFormulario;
    }

    public void setDiligenciarFormulario(Short diligenciarFormulario) {
        this.diligenciarFormulario = diligenciarFormulario;
    }

    public Short getSolicitarAprobacion() {
        return solicitarAprobacion;
    }

    public void setSolicitarAprobacion(Short solicitarAprobacion) {
        this.solicitarAprobacion = solicitarAprobacion;
    }

    public Short getMonitoreoAuditoria() {
        return monitoreoAuditoria;
    }

    public void setMonitoreoAuditoria(Short monitoreoAuditoria) {
        this.monitoreoAuditoria = monitoreoAuditoria;
    }

    public Short getAprobar() {
        return aprobar;
    }

    public void setAprobar(Short aprobar) {
        this.aprobar = aprobar;
    }

    public Short getModificarParametros() {
        return modificarParametros;
    }

    public void setModificarParametros(Short modificarParametros) {
        this.modificarParametros = modificarParametros;
    }

    public Short getExportarReportes() {
        return exportarReportes;
    }

    public void setExportarReportes(Short exportarReportes) {
        this.exportarReportes = exportarReportes;
    }

    @Override
    public String toString() {
        return "SiproRolesPermisos{" +
                "idRol=" + idRol +
                ", rol='" + rol + '\'' +
                ", perfil='" + perfil + '\'' +
                '}';
    }
}
