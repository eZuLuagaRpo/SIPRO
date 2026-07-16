package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Relaciona al usuario con su área organizacional y el líder asociado.
 */
@Entity
@Table(name = "usuario_area")
public class UsuarioArea implements Serializable {

    @Id
    @Column(name = "id_usuario")
    private Long idUsuario;

    @OneToOne
    @MapsId
    @JoinColumn(name = "id_usuario")
    private UsuarioLogin usuarioLogin;

    @Column(name = "area_nombre")
    private String areaNombre;

    @Column(name = "rol_lider")
    private String cargoJefe;

    @Column(name = "id_usuario_lider", insertable = false, updatable = false)
    private Long idJefe;

    /**
     * Relación ManyToOne: id_usuario_lider → usuario_persona.id_usuario.
     * Se conserva el nombre lógico "jefe" para no propagar un renombre amplio en el dominio.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_lider", referencedColumnName = "id_usuario")
    private UsuarioPersona jefe;

    // Constructores
    public UsuarioArea() {
    }

    public UsuarioArea(Long idUsuario, String areaNombre) {
        this.idUsuario = idUsuario;
        this.areaNombre = areaNombre;
    }

    // Getters y Setters
    public Long getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Long idUsuario) {
        this.idUsuario = idUsuario;
    }

    public UsuarioLogin getUsuarioLogin() {
        return usuarioLogin;
    }

    public void setUsuarioLogin(UsuarioLogin usuarioLogin) {
        this.usuarioLogin = usuarioLogin;
    }

    public String getAreaNombre() {
        return areaNombre;
    }

    public void setAreaNombre(String areaNombre) {
        this.areaNombre = areaNombre;
    }

    public String getCargoJefe() {
        return cargoJefe;
    }

    public void setCargoJefe(String cargoJefe) {
        this.cargoJefe = cargoJefe;
    }

    public Long getIdJefe() {
        return idJefe;
    }

    public void setIdJefe(Long idJefe) {
        this.idJefe = idJefe;
    }

    public UsuarioPersona getJefe() {
        return jefe;
    }

    public void setJefe(UsuarioPersona jefe) {
        this.jefe = jefe;
    }

    /**
     * Helper: obtiene el nombre completo del jefe navegando la relación JPA.
     */
    public String getJefeNombreCompleto() {
        if (jefe == null) return null;
        String n = jefe.getNombres() != null ? jefe.getNombres() : "";
        String a = jefe.getApellidos() != null ? jefe.getApellidos() : "";
        return (n + " " + a).trim();
    }

    /**
     * Helper: obtiene el correo del jefe navegando la relación JPA.
     */
    public String getJefeCorreo() {
        return jefe != null ? jefe.getCorreo() : null;
    }

    @Override
    public String toString() {
        return "UsuarioArea{" +
                "idUsuario=" + idUsuario +
                ", areaNombre='" + areaNombre + '\'' +
                ", idJefe=" + idJefe +
                '}';
    }
}
