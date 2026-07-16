package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * Llave primaria compuesta para la tabla sipro_usuario_producto_rol.
 * Compuesta por: id_usuario + id_producto + id_rol + id_segmento.
 */
@Embeddable
public class SiproUsuarioProductoRolId implements Serializable {

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "id_producto")
    private Long idProducto;

    @Column(name = "id_rol")
    private Integer idRol;

    @Column(name = "id_segmento")
    private Long idSegmento;

    public SiproUsuarioProductoRolId() {
    }

    public SiproUsuarioProductoRolId(Long idUsuario, Long idProducto, Integer idRol, Long idSegmento) {
        this.idUsuario = idUsuario;
        this.idProducto = idProducto;
        this.idRol = idRol;
        this.idSegmento = idSegmento;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Long idUsuario) {
        this.idUsuario = idUsuario;
    }

    public Long getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(Long idProducto) {
        this.idProducto = idProducto;
    }

    public Integer getIdRol() {
        return idRol;
    }

    public void setIdRol(Integer idRol) {
        this.idRol = idRol;
    }

    public Long getIdSegmento() {
        return idSegmento;
    }

    public void setIdSegmento(Long idSegmento) {
        this.idSegmento = idSegmento;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SiproUsuarioProductoRolId that = (SiproUsuarioProductoRolId) o;
        return Objects.equals(idUsuario, that.idUsuario) &&
                Objects.equals(idProducto, that.idProducto) &&
            Objects.equals(idRol, that.idRol) &&
            Objects.equals(idSegmento, that.idSegmento);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idUsuario, idProducto, idRol, idSegmento);
    }

    @Override
    public String toString() {
        return "SiproUsuarioProductoRolId{" +
                "idUsuario=" + idUsuario +
                ", idProducto=" + idProducto +
                ", idRol=" + idRol +
            ", idSegmento=" + idSegmento +
                '}';
    }
}
