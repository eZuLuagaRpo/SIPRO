package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Contiene los datos personales básicos del usuario autenticado en SIPRO.
 */
@Entity
@Table(name = "usuario_persona")
public class UsuarioPersona implements Serializable {

    @Id
    @Column(name = "id_usuario")
    private Long idUsuario;

    @OneToOne
    @MapsId
    @JoinColumn(name = "id_usuario")
    private UsuarioLogin usuarioLogin;

    @Column(name = "nombres", length = 100)
    private String nombres;

    @Column(name = "apellidos", length = 100)
    private String apellidos;

    @Column(name = "correo", length = 100)
    private String correo;

    @Column(name = "usuario", length = 100)
    private String usuario;

    // Constructores
    public UsuarioPersona() {
    }

    public UsuarioPersona(Long idUsuario, String nombres, String apellidos, String correo) {
        this.idUsuario = idUsuario;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.correo = correo;
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

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    @Override
    public String toString() {
        return "UsuarioPersona{" +
                "idUsuario=" + idUsuario +
                ", nombres='" + nombres + '\'' +
                ", apellidos='" + apellidos + '\'' +
                ", correo='" + correo + '\'' +
                ", usuario='" + usuario + '\'' +
                '}';
    }
}
