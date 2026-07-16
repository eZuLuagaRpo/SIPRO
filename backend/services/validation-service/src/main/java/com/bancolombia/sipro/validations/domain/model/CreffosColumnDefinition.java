package com.bancolombia.sipro.validations.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

/**
 * Define cómo se construye una columna de salida del archivo CREFFSOS.
 */
public record CreffosColumnDefinition(
        String nombreColumna,
        int orden,
        String tipoDatoSalida,
        Integer longitudMaxima,
        Integer precisionNumerica,
        Integer escalaNumerica,
        String formatoSalida,
        String origenDato,
        String tablaOrigen,
        String columnaOrigen,
        String aliasOrigen,
        String valorConstante,
        String funcionJava,
        String expresionSql,
        JsonNode parametrosJson,
        boolean obligatorio,
        boolean permiteNulo,
        boolean incluirSalida,
        short estado,
        String descripcion,
        String observaciones
) {

    /**
     * Devuelve el bloque JSON de parámetros o un nodo vacío si no existe.
     */
    public JsonNode parametros() {
        return parametrosJson == null ? MissingNode.getInstance() : parametrosJson;
    }

    /**
     * Lee un parámetro de texto desde la configuración JSON de la columna.
     */
    public String parametroTexto(String clave) {
        JsonNode node = parametros().path(clave);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    /**
     * Lee un parámetro booleano y aplica un valor por defecto si no está informado.
     */
    public boolean parametroBooleano(String clave, boolean defaultValue) {
        JsonNode node = parametros().path(clave);
        return node.isMissingNode() || node.isNull() ? defaultValue : node.asBoolean(defaultValue);
    }

    /**
     * Lee un parámetro entero y aplica un valor por defecto si no está informado.
     */
    public int parametroEntero(String clave, int defaultValue) {
        JsonNode node = parametros().path(clave);
        return node.isMissingNode() || node.isNull() ? defaultValue : node.asInt(defaultValue);
    }
}