package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproParametrosRangoHabilitado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository para acceder a la configuración de rango de fechas habilitadas.
 */
@Repository
public interface SiproParametrosRangoHabilitadoRepository extends JpaRepository<SiproParametrosRangoHabilitado, Long> {

    /**
     * Busca la regla activa por código que esté vigente en la fecha actual.
     * La regla debe estar activa, vigente_desde <= hoy, y vigente_hasta es null o >= hoy.
     */
    @Query("SELECT p FROM SiproParametrosRangoHabilitado p " +
           "WHERE p.codigo = :codigo " +
           "AND p.activo = true " +
           "AND p.vigenteDesde <= :fechaActual " +
           "AND (p.vigenteHasta IS NULL OR p.vigenteHasta >= :fechaActual) " +
           "ORDER BY p.vigenteDesde DESC")
    Optional<SiproParametrosRangoHabilitado> findActiveByCodigo(
            @Param("codigo") String codigo,
            @Param("fechaActual") LocalDate fechaActual);

    /**
     * Busca la configuración activa para FECHA_CORTE vigente hoy.
     */
    default Optional<SiproParametrosRangoHabilitado> findActiveConfigFechaCorte() {
        return findActiveByCodigo("FECHA_CORTE", LocalDate.now());
    }
}
