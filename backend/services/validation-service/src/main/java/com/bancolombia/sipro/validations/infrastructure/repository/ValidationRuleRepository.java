package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.ValidationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio para consultar reglas de validación desde la tabla data_quality.data_validation_rule
 */
@Repository
public interface ValidationRuleRepository extends JpaRepository<ValidationRule, Integer> {
    
        /**
         * Obtiene reglas activas por segmento, incluyendo reglas comunes 9999 y el legado ALL.
         */
        @Query("SELECT r FROM ValidationRule r WHERE r.isActive = true " +
            "AND (UPPER(r.appliesToProduct) = 'ALL' OR r.appliesToProduct = :commonSegmentKey OR r.appliesToProduct = :segmentKey) " +
            "ORDER BY COALESCE(r.orden, 999999), r.ruleId")
        List<ValidationRule> findActiveRulesBySegment(@Param("segmentKey") String segmentKey,
                                 @Param("commonSegmentKey") String commonSegmentKey);
    
    /**
     * Obtiene todas las reglas activas (para validaciones globales)
     * 
     * @return Lista de todas las reglas activas
     */
    @Query("SELECT r FROM ValidationRule r WHERE r.isActive = true ORDER BY COALESCE(r.orden, 999999), r.ruleId")
    List<ValidationRule> findAllActiveRules();
}
