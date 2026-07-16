package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Guarda las credenciales del usuario y enlaza sus datos personales y de área.
 */
@Entity
@Table(name = "usuario_login")
public class UsuarioLogin implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "usuario", nullable = false, unique = true, length = 50)
    private String usuario;

    @Column(name = "clave", length = 255)
    private String clave;

    @OneToOne(mappedBy = "usuarioLogin", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UsuarioPersona persona;

    @OneToOne(mappedBy = "usuarioLogin", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UsuarioArea area;

    // Constructores
    public UsuarioLogin() {
    }

    public UsuarioLogin(String usuario, String clave) {
        this.usuario = usuario;
        this.clave = clave;
    }

    // Getters y Setters
    public Long getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Long idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }

    public UsuarioPersona getPersona() {
        return persona;
    }

    public void setPersona(UsuarioPersona persona) {
        this.persona = persona;
    }

    public UsuarioArea getArea() {
        return area;
    }

    public void setArea(UsuarioArea area) {
        this.area = area;
    }

    @Override
    public String toString() {
        return "UsuarioLogin{" +
                "idUsuario=" + idUsuario +
                ", usuario='" + usuario + '\'' +
                '}';
    }
}
