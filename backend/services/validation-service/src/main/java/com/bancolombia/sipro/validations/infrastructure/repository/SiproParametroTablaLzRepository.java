package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproParametroTablaLz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Consulta la configuración versionada de tablas origen para la ingesta LZ.
 */
@Repository
public interface SiproParametroTablaLzRepository extends JpaRepository<SiproParametroTablaLz, Integer> {

    @EntityGraph(attributePaths = "catalogoTabla")
    Optional<SiproParametroTablaLz> findFirstByCatalogoTabla_TablaOrigenIgnoreCaseAndEstadoOrderByVersionDesc(
      String tablaOrigen,
      String estado);

    default Optional<SiproParametroTablaLz> findActivaMaxVersion(String tablaOrigen) {
      return findFirstByCatalogoTabla_TablaOrigenIgnoreCaseAndEstadoOrderByVersionDesc(
        tablaOrigen,
        "ACTIVO");
    }
}
