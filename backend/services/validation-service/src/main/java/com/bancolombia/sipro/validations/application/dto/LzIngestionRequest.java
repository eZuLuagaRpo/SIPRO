package com.bancolombia.sipro.validations.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Parámetros que necesita la ingesta desde LZ para ejecutar un periodo específico.
 */
public class LzIngestionRequest {

    @NotBlank(message = "tablaOrigen es obligatorio")
    private String tablaOrigen;

    @Min(value = 2020, message = "year debe ser >= 2020")
    @Max(value = 2100, message = "year debe ser <= 2100")
    private int periodYear;

    @Min(value = 1, message = "month debe ser entre 1 y 12")
    @Max(value = 12, message = "month debe ser entre 1 y 12")
    private int periodMonth;

    /** Si true, permite re-ejecutar aunque ya exista un SUCCESS en el mismo periodo. */
    private boolean forceOverwrite = false;

    public String getTablaOrigen() { return tablaOrigen; }
    public void setTablaOrigen(String tablaOrigen) { this.tablaOrigen = tablaOrigen; }

    public int getPeriodYear() { return periodYear; }
    public void setPeriodYear(int periodYear) { this.periodYear = periodYear; }

    public int getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(int periodMonth) { this.periodMonth = periodMonth; }

    public boolean isForceOverwrite() { return forceOverwrite; }
    public void setForceOverwrite(boolean forceOverwrite) { this.forceOverwrite = forceOverwrite; }
}
