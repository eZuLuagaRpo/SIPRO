package com.bancolombia.sipro.validations.domain.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad que mapea la tabla data_validation_rule
 * Contiene las reglas de validación configurables desde BD.
 * 
 * Soporta 3 tipos de reglas (rule_kind):
 * - FIELD: Validaciones de campo individual (regex, min/max, etc.)
 * - COMPOSITE_DATE: Construir fecha desde campos año/mes/día y validar
 * - DATE_RELATION: Comparar fechas entre sí o contra variables runtime
 */
@Entity
@Table(name = "data_validation_rule")
public class ValidationRule implements Serializable {

    @Id
    @Column(name = "rule_id")
    private Integer ruleId;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "data_type")
    private String dataType;

    @Column(name = "applies_to_product")
    private String appliesToProduct;

    @Column(name = "is_required")
    private Boolean isRequired;

    @Column(name = "allow_null")
    private Boolean allowNull;

    @Column(name = "has_default")
    private Boolean hasDefault;

    @Column(name = "default_value")
    private String defaultValue;

    @Column(name = "orden")
    private Integer orden;

    @Column(name = "max_length")
    private Integer maxLength;

    @Column(name = "min_value")
    private BigDecimal minValue;

    @Column(name = "max_value")
    private BigDecimal maxValue;

    @Column(name = "allow_negative")
    private Boolean allowNegative;

    @Column(name = "allowed_values")
    private String allowedValues;

    @Column(name = "regex_pattern")
    private String regexPattern;

    @Column(name = "reference_field")
    private String referenceField;

    @Column(name = "validation_formula")
    private String validationFormula;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "comments")
    private String comments;

    // ========== NUEVOS CAMPOS PARA VALIDACIONES DE FECHA ==========

    /**
     * Tipo de regla: FIELD, COMPOSITE_DATE, DATE_RELATION
     */
    @Column(name = "rule_kind")
    private String ruleKind;

    /**
     * Código de grupo para agrupar campos relacionados (ej: FECHA_INICIO)
     */
    @Column(name = "group_code")
    private String groupCode;

    /**
     * Nombre del campo que contiene el año (para COMPOSITE_DATE)
     */
    @Column(name = "year_field")
    private String yearField;

    /**
     * Nombre del campo que contiene el mes (para COMPOSITE_DATE)
     */
    @Column(name = "month_field")
    private String monthField;

    /**
     * Nombre del campo que contiene el día (para COMPOSITE_DATE)
     */
    @Column(name = "day_field")
    private String dayField;

    /**
     * Código izquierdo para comparación (para DATE_RELATION)
     * Referencia al field_name de una regla COMPOSITE_DATE
     */
    @Column(name = "left_code")
    private String leftCode;

    /**
     * Operador de comparación: <=, <, >=, >, ==, !=
     */
    @Column(name = "operator_code")
    private String operatorCode;

    /**
     * Código derecho para comparación (para DATE_RELATION)
     * Puede ser field_name de COMPOSITE_DATE o una variable runtime (VAR_*)
     */
    @Column(name = "right_code")
    private String rightCode;

    /**
     * Indica si right_code es una variable runtime (true) o un campo (false)
     */
    @Column(name = "right_is_variable")
    private Boolean rightIsVariable;

    /**
     * Segmento al que aplica la regla.
     * 1 = Colgaap/Modificado, 2 = Full IFRS, null = ambos (se filtra por appliesToProduct=9999).
     */
    @Column(name = "id_segmento")
    private Integer idSegmento;

    // Constructores
    public ValidationRule() {
    }

    // Getters y Setters originales
    public Integer getRuleId() {
        return ruleId;
    }

    public void setRuleId(Integer ruleId) {
        this.ruleId = ruleId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getAppliesToProduct() {
        return appliesToProduct;
    }

    public void setAppliesToProduct(String appliesToProduct) {
        this.appliesToProduct = appliesToProduct;
    }

    public Boolean getIsRequired() {
        return isRequired;
    }

    public void setIsRequired(Boolean isRequired) {
        this.isRequired = isRequired;
    }

    public Boolean getAllowNull() {
        return allowNull;
    }

    public void setAllowNull(Boolean allowNull) {
        this.allowNull = allowNull;
    }

    public Boolean getHasDefault() {
        return hasDefault;
    }

    public void setHasDefault(Boolean hasDefault) {
        this.hasDefault = hasDefault;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Integer getOrden() {
        return orden;
    }

    public void setOrden(Integer orden) {
        this.orden = orden;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public BigDecimal getMinValue() {
        return minValue;
    }

    public void setMinValue(BigDecimal minValue) {
        this.minValue = minValue;
    }

    public BigDecimal getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(BigDecimal maxValue) {
        this.maxValue = maxValue;
    }

    public Boolean getAllowNegative() {
        return allowNegative;
    }

    public void setAllowNegative(Boolean allowNegative) {
        this.allowNegative = allowNegative;
    }

    public String getAllowedValues() {
        return allowedValues;
    }

    public void setAllowedValues(String allowedValues) {
        this.allowedValues = allowedValues;
    }

    public String getRegexPattern() {
        return regexPattern;
    }

    public void setRegexPattern(String regexPattern) {
        this.regexPattern = regexPattern;
    }

    public String getReferenceField() {
        return referenceField;
    }

    public void setReferenceField(String referenceField) {
        this.referenceField = referenceField;
    }

    public String getValidationFormula() {
        return validationFormula;
    }

    public void setValidationFormula(String validationFormula) {
        this.validationFormula = validationFormula;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    // ========== NUEVOS GETTERS Y SETTERS ==========

    public String getRuleKind() {
        return ruleKind;
    }

    public void setRuleKind(String ruleKind) {
        this.ruleKind = ruleKind;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public String getYearField() {
        return yearField;
    }

    public void setYearField(String yearField) {
        this.yearField = yearField;
    }

    public String getMonthField() {
        return monthField;
    }

    public void setMonthField(String monthField) {
        this.monthField = monthField;
    }

    public String getDayField() {
        return dayField;
    }

    public void setDayField(String dayField) {
        this.dayField = dayField;
    }

    public String getLeftCode() {
        return leftCode;
    }

    public void setLeftCode(String leftCode) {
        this.leftCode = leftCode;
    }

    public String getOperatorCode() {
        return operatorCode;
    }

    public void setOperatorCode(String operatorCode) {
        this.operatorCode = operatorCode;
    }

    public String getRightCode() {
        return rightCode;
    }

    public void setRightCode(String rightCode) {
        this.rightCode = rightCode;
    }

    public Boolean getRightIsVariable() {
        return rightIsVariable;
    }

    public void setRightIsVariable(Boolean rightIsVariable) {
        this.rightIsVariable = rightIsVariable;
    }

    // ========== MÉTODOS DE CONVENIENCIA ==========

    public Integer getIdSegmento() {
        return idSegmento;
    }

    public void setIdSegmento(Integer idSegmento) {
        this.idSegmento = idSegmento;
    }

    /**
     * @return true si es una regla de tipo FIELD (o null/vacío para compatibilidad)
     */
    public boolean isFieldRule() {
        return ruleKind == null || ruleKind.isEmpty() || "FIELD".equalsIgnoreCase(ruleKind);
    }

    /**
     * @return true si es una regla de tipo COMPOSITE_DATE
     */
    public boolean isCompositeDateRule() {
        return "COMPOSITE_DATE".equalsIgnoreCase(ruleKind);
    }

    /**
     * @return true si es una regla de tipo DATE_RELATION
     */
    public boolean isDateRelationRule() {
        return "DATE_RELATION".equalsIgnoreCase(ruleKind);
    }

    /**
     * @return true si es una regla de tipo CTRL_CONTENT
     * (valida que el contenido del .txt sea solo numérico)
     */
    public boolean isCtrlContentRule() {
        return "CTRL_CONTENT".equalsIgnoreCase(ruleKind);
    }

    /**
     * @return true si es una regla de tipo CTRL_RECORD_COUNT
     * (valida que el número del .txt coincida con las filas del .xlsx)
     */
    public boolean isCtrlRecordCountRule() {
        return "CTRL_RECORD_COUNT".equalsIgnoreCase(ruleKind);
    }

    public boolean participatesInInputStructure() {
        return isFieldRule();
    }
}
