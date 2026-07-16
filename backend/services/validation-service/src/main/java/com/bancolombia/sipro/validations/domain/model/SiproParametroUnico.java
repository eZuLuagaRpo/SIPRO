package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Almacena parámetros simples clave-valor usados para controlar comportamiento del sistema.
 */
@Entity
@Table(name = "sipro_parametros_unico", schema = "public")
public class SiproParametroUnico implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "clave", nullable = false, unique = true, length = 100)
    private String clave;

    @Column(name = "valor", nullable = false, length = 255)
    private String valor;

    @Column(name = "tipo", nullable = false, length = 50)
    private String tipo;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "modificado_en", insertable = false, updatable = false)
    private LocalDateTime modificadoEn;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getClave() { return clave; }
    public void setClave(String clave) { this.clave = clave; }

    public String getValor() { return valor; }
    public void setValor(String valor) { this.valor = valor; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getModificadoEn() { return modificadoEn; }
}
