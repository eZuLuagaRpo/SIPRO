package com.bancolombia.sipro.validations.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO que resume el resultado final de una validación de archivo.
 */
public class ValidationResult {
    private String loteId;
    private String status; // "OK" | "ERROR"
    private String sha256;
    private boolean archivoTemporalDisponible;
    private List<String> errores = new ArrayList<>();
    private ResumenDto resumen;
    private List<ResumenFilaDto> resumenDetallado = new ArrayList<>();
    
    /**
     * Resume el total principal del archivo validado.
     */
    public static class ResumenDto {
        private String moneda;
        private long registros;
        private BigDecimal totalVlrIniObl;
        
        public ResumenDto(String moneda, long registros, BigDecimal totalVlrIniObl) {
            this.moneda = moneda;
            this.registros = registros;
            this.totalVlrIniObl = totalVlrIniObl;
        }
        
        public String getMoneda() { return moneda; }
        public void setMoneda(String moneda) { this.moneda = moneda; }
        
        public long getRegistros() { return registros; }
        public void setRegistros(long registros) { this.registros = registros; }
        
        public BigDecimal getTotalVlrIniObl() { return totalVlrIniObl; }
        public void setTotalVlrIniObl(BigDecimal totalVlrIniObl) { this.totalVlrIniObl = totalVlrIniObl; }
    }
    
    public String getLoteId() { return loteId; }
    public void setLoteId(String loteId) { this.loteId = loteId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public boolean isArchivoTemporalDisponible() { return archivoTemporalDisponible; }
    public void setArchivoTemporalDisponible(boolean archivoTemporalDisponible) {
        this.archivoTemporalDisponible = archivoTemporalDisponible;
    }
    
    public List<String> getErrores() { return errores; }
    public void setErrores(List<String> errores) { this.errores = errores; }
    
    public ResumenDto getResumen() { return resumen; }
    public void setResumen(ResumenDto resumen) { this.resumen = resumen; }
    
    public List<ResumenFilaDto> getResumenDetallado() { return resumenDetallado; }
    public void setResumenDetallado(List<ResumenFilaDto> resumenDetallado) { this.resumenDetallado = resumenDetallado; }
    
    /**
     * Representa una fila del resumen agrupado por moneda.
     */
    public static class ResumenFilaDto {
        private String moneda;
        private long cantidad;
        private BigDecimal total;
        
        public ResumenFilaDto(String moneda, long cantidad, BigDecimal total) {
            this.moneda = moneda;
            this.cantidad = cantidad;
            this.total = total;
        }
        
        public String getMoneda() { return moneda; }
        public void setMoneda(String moneda) { this.moneda = moneda; }
        
        public long getCantidad() { return cantidad; }
        public void setCantidad(long cantidad) { this.cantidad = cantidad; }
        
        public BigDecimal getTotal() { return total; }
        public void setTotal(BigDecimal total) { this.total = total; }
    }
}
