package com.bancolombia.sipro.validations.application.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Respuesta del endpoint de aprobaciones pendientes para un líder.
 * Contiene los últimos 3 meses (hasta el mes actual) con la lista de
 * planillas PENDIENTE de aprobar por mes.
 */
public class AprobacionesPendientesResponse {

    /** Lista de 3 meses (del más reciente al más antiguo), cada uno con sus pendientes. */
    private List<MesPendiente> meses;

    public AprobacionesPendientesResponse() {
    }

    public AprobacionesPendientesResponse(List<MesPendiente> meses) {
        this.meses = meses;
    }

    public List<MesPendiente> getMeses() {
        return meses;
    }

    public void setMeses(List<MesPendiente> meses) {
        this.meses = meses;
    }

    /**
     * Datos de un mes específico con las planillas pendientes de aprobación.
     */
    public static class MesPendiente {
        /** Último día del mes (para marcar en el calendario). */
        private LocalDate fechaUltimoDia;
        /** Nombre del mes en español (ej: "Febrero"). */
        private String nombreMes;
        /** Año del mes. */
        private int anio;
        /** Lista de productos con planillas pendientes de aprobar en este mes. */
        private List<ProductoPendienteAprobacion> productosPendientes;

        public MesPendiente() {
        }

        public MesPendiente(LocalDate fechaUltimoDia, String nombreMes, int anio,
                List<ProductoPendienteAprobacion> productosPendientes) {
            this.fechaUltimoDia = fechaUltimoDia;
            this.nombreMes = nombreMes;
            this.anio = anio;
            this.productosPendientes = productosPendientes;
        }

        public LocalDate getFechaUltimoDia() {
            return fechaUltimoDia;
        }

        public void setFechaUltimoDia(LocalDate fechaUltimoDia) {
            this.fechaUltimoDia = fechaUltimoDia;
        }

        public String getNombreMes() {
            return nombreMes;
        }

        public void setNombreMes(String nombreMes) {
            this.nombreMes = nombreMes;
        }

        public int getAnio() {
            return anio;
        }

        public void setAnio(int anio) {
            this.anio = anio;
        }

        public List<ProductoPendienteAprobacion> getProductosPendientes() {
            return productosPendientes;
        }

        public void setProductosPendientes(List<ProductoPendienteAprobacion> productosPendientes) {
            this.productosPendientes = productosPendientes;
        }

        public boolean tienePendientes() {
            return productosPendientes != null && !productosPendientes.isEmpty();
        }
    }

    /**
     * Producto con planilla pendiente de aprobación.
     */
    public static class ProductoPendienteAprobacion {
        private Long idPlanilla;
        private Long idProducto;
        private String tituloProducto;

        public ProductoPendienteAprobacion() {
        }

        public ProductoPendienteAprobacion(Long idPlanilla, Long idProducto, String tituloProducto) {
            this.idPlanilla = idPlanilla;
            this.idProducto = idProducto;
            this.tituloProducto = tituloProducto;
        }

        public Long getIdPlanilla() {
            return idPlanilla;
        }

        public void setIdPlanilla(Long idPlanilla) {
            this.idPlanilla = idPlanilla;
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
