package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Entidad RBAC que asocia un usuario con un rol específico sobre un producto.
 * Tabla puente: sipro_usuario_producto_rol
 * 
 * Cada registro representa un "pase de acceso":
 *   - QUIÉN: El Usuario (usuario_persona)
 *   - DÓNDE: El Producto (productos)
 *   - QUÉ HACE: El Rol (sipro_roles_permisos)
 */
@Entity
@Table(name = "sipro_usuario_producto_rol")
public class SiproUsuarioProductoRol implements Serializable {

    @EmbeddedId
    private SiproUsuarioProductoRolId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", insertable = false, updatable = false)
    private UsuarioPersona usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_producto", insertable = false, updatable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_rol", insertable = false, updatable = false)
    private SiproRolesPermisos rol;

    @Column(name = "orden_flujo", nullable = false)
    private Short ordenFlujo = 1;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    // Constructores
    public SiproUsuarioProductoRol() {
    }

    public SiproUsuarioProductoRol(Long idUsuario, Long idProducto, Integer idRol, Long idSegmento, Short ordenFlujo) {
        this.id = new SiproUsuarioProductoRolId(idUsuario, idProducto, idRol, idSegmento);
        this.ordenFlujo = ordenFlujo;
        this.activo = true;
    }

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
        this.actualizadoEn = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.actualizadoEn = LocalDateTime.now();
    }

    // Getters y Setters
    public SiproUsuarioProductoRolId getId() {
        return id;
    }

    public void setId(SiproUsuarioProductoRolId id) {
        this.id = id;
    }

    public Long getIdSegmento() {
        return id != null ? id.getIdSegmento() : null;
    }

    public UsuarioPersona getUsuario() {
        return usuario;
    }

    public void setUsuario(UsuarioPersona usuario) {
        this.usuario = usuario;
    }

    public Producto getProducto() {
        return producto;
    }

    public void setProducto(Producto producto) {
        this.producto = producto;
    }

    public SiproRolesPermisos getRol() {
        return rol;
    }

    public void setRol(SiproRolesPermisos rol) {
        this.rol = rol;
    }

    public Short getOrdenFlujo() {
        return ordenFlujo;
    }

    public void setOrdenFlujo(Short ordenFlujo) {
        this.ordenFlujo = ordenFlujo;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(LocalDateTime actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }

    @Override
    public String toString() {
        return "SiproUsuarioProductoRol{" +
                "id=" + id +
                ", ordenFlujo=" + ordenFlujo +
                ", activo=" + activo +
                '}';
    }
}
