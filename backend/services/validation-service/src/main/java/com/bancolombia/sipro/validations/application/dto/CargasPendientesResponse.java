package com.bancolombia.sipro.validations.application.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Respuesta del endpoint de cargas pendientes para un usuario.
 * Contiene la fecha del mes anterior (último día) y la lista de productos
 * que aún no tienen archivos cargados para ese mes.
 */
public class CargasPendientesResponse {

    /** Último día del mes anterior (la fecha que se marca en el calendario). */
    private LocalDate fechaMesAnterior;

    /** Nombre del mes anterior en español (ej: "Febrero"). */
    private String nombreMesAnterior;

    /** Productos que tienen carga pendiente del mes anterior. */
    private List<ProductoPendiente> productosPendientes;

    public CargasPendientesResponse() {
    }

    public CargasPendientesResponse(LocalDate fechaMesAnterior, String nombreMesAnterior,
            List<ProductoPendiente> productosPendientes) {
        this.fechaMesAnterior = fechaMesAnterior;
        this.nombreMesAnterior = nombreMesAnterior;
        this.productosPendientes = productosPendientes;
    }

    public LocalDate getFechaMesAnterior() {
        return fechaMesAnterior;
    }

    public void setFechaMesAnterior(LocalDate fechaMesAnterior) {
        this.fechaMesAnterior = fechaMesAnterior;
    }

    public String getNombreMesAnterior() {
        return nombreMesAnterior;
    }

    public void setNombreMesAnterior(String nombreMesAnterior) {
        this.nombreMesAnterior = nombreMesAnterior;
    }

    public List<ProductoPendiente> getProductosPendientes() {
        return productosPendientes;
    }

    public void setProductosPendientes(List<ProductoPendiente> productosPendientes) {
        this.productosPendientes = productosPendientes;
    }

    /**
     * Producto que el usuario debería haber cargado y no lo ha hecho.
     */
    public static class ProductoPendiente {
        private Long idProducto;
        private String tituloProducto;

        public ProductoPendiente() {
        }

        public ProductoPendiente(Long idProducto, String tituloProducto) {
            this.idProducto = idProducto;
            this.tituloProducto = tituloProducto;
        }

        public Long getIdProducto() {
            return idProducto;
        }

        public void setIdProducto(Long idProducto) {
            this.idProducto = idProducto;
        }

        public String getTituloProducto() {
            return tituloProducto;
        }

        public void setTituloProducto(String tituloProducto) {
            this.tituloProducto = tituloProducto;
        }
    }
}
