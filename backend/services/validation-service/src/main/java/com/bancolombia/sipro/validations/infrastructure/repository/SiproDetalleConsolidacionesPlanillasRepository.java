package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Administra los encabezados de consolidación por período de valoración.
 */
@Repository
public interface SiproDetalleConsolidacionesPlanillasRepository
        extends JpaRepository<SiproDetalleConsolidacionesPlanillas, Long> {

        /**
         * Lista consolidaciones por estado, mostrando primero las más recientes.
         */
        List<SiproDetalleConsolidacionesPlanillas> findAllByEstadoConsolidacionOrderByPeriodoValoracionDescCreadoEnDesc(
                        String estadoConsolidacion);

    /**
     * Indica si ya existe una consolidación con un estado específico para el período.
     */
    boolean existsByPeriodoValoracionAndEstadoConsolidacion(LocalDate periodoValoracion, String estadoConsolidacion);

    /**
     * Indica si el período ya tiene consolidaciones en cualquiera de los estados enviados.
     */
    boolean existsByPeriodoValoracionAndEstadoConsolidacionIn(LocalDate periodoValoracion,
                                                              Collection<String> estadosConsolidacion);

    /**
     * Recupera la última ejecución terminada para un período y estado concreto.
     */
    Optional<SiproDetalleConsolidacionesPlanillas> findFirstByPeriodoValoracionAndEstadoConsolidacionOrderByFechaHoraFinDesc(
            LocalDate periodoValoracion,
            String estadoConsolidacion);

    /**
     * Recupera la última consolidación exitosa del período, incluyendo advertencias.
     */
    Optional<SiproDetalleConsolidacionesPlanillas> findFirstByPeriodoValoracionAndEstadoConsolidacionInOrderByCreadoEnDesc(
            LocalDate periodoValoracion,
            Collection<String> estadosConsolidacion);

        /**
         * Obtiene la consolidación más reciente creada para un período.
         */
        Optional<SiproDetalleConsolidacionesPlanillas> findFirstByPeriodoValoracionOrderByCreadoEnDesc(LocalDate periodoValoracion);

                /**
                 * Obtiene la última consolidación registrada en cualquier periodo.
                 */
                Optional<SiproDetalleConsolidacionesPlanillas> findTopByOrderByCreadoEnDesc();

                /**
                 * Lista el histórico reciente de consolidaciones sin filtrar por periodo.
                 */
                List<SiproDetalleConsolidacionesPlanillas> findTop20ByOrderByCreadoEnDesc();

        /**
         * Lista el historial de consolidaciones registradas para un período.
         */
        List<SiproDetalleConsolidacionesPlanillas> findAllByPeriodoValoracionOrderByCreadoEnDesc(LocalDate periodoValoracion);

                /**
                 * Recupera los periodos que ya tienen una consolidación exitosa registrada.
                 */
                @Query("SELECT DISTINCT c.periodoValoracion FROM SiproDetalleConsolidacionesPlanillas c "
                        + "WHERE c.estadoConsolidacion IN :estadosConsolidacion")
                List<LocalDate> findDistinctPeriodoValoracionByEstadoConsolidacionIn(
                        @Param("estadosConsolidacion") Collection<String> estadosConsolidacion);
}