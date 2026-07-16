package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidadoRegistro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Consulta los registros detallados generados por un consolidado.
 */
@Repository
public interface SiproDetalleConsolidadoRegistroRepository extends JpaRepository<SiproDetalleConsolidadoRegistro, Long> {

	/**
	 * Obtiene los registros consolidados de una fecha de corte en orden estable.
	 */
	List<SiproDetalleConsolidadoRegistro> findByFechaCorteOrderByIdConsolidadoRegistroAsc(LocalDate fechaCorte);

	/**
	 * Obtiene los registros asociados a una consolidación específica.
	 */
	List<SiproDetalleConsolidadoRegistro> findByIdConsolidacionOrderByIdConsolidadoRegistroAsc(Long idConsolidacion);

	/**
	 * Elimina un lote acotado de registros asociados a una consolidación.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "DELETE FROM public.sipro_detalle_consolidado_registros "
			+ "WHERE id_consolidado_registro IN ("
			+ "    SELECT id_consolidado_registro "
			+ "    FROM public.sipro_detalle_consolidado_registros "
			+ "    WHERE id_consolidacion = :idConsolidacion "
			+ "    ORDER BY id_consolidado_registro "
			+ "    LIMIT :batchSize"
			+ ")",
			nativeQuery = true)
	int deleteBatchByIdConsolidacion(@Param("idConsolidacion") Long idConsolidacion,
	                                 @Param("batchSize") int batchSize);
}