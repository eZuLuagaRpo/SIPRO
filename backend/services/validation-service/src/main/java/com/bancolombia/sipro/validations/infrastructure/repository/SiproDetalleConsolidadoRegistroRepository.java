package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.application.dto.ProductoAgregadoDto;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidadoRegistro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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
	 * Suma el campo vlriniobl de todos los registros de una consolidación.
	 */
	@Query("SELECT COALESCE(SUM(r.vlriniobl), 0) FROM SiproDetalleConsolidadoRegistro r WHERE r.idConsolidacion = :idConsolidacion")
	BigDecimal sumVlrinioblByIdConsolidacion(@Param("idConsolidacion") Long idConsolidacion);

	/**
	 * Agrega los registros de una consolidación agrupando por producto.
	 * Devuelve una fila por producto en lugar de todos los registros individuales,
	 * evitando cargar decenas de miles de filas en memoria Java.
	 */
	@Query("SELECT new com.bancolombia.sipro.validations.application.dto.ProductoAgregadoDto(" +
		"r.idProductoOrigen, r.productoOrigen, COUNT(r.idConsolidadoRegistro), SUM(r.vlriniobl), " +
		"SUM(CASE WHEN r.tipoId IS NULL OR TRIM(r.tipoId) = '' OR r.clasificacion IS NULL THEN 1L ELSE 0L END)) " +
		"FROM SiproDetalleConsolidadoRegistro r " +
		"WHERE r.idConsolidacion = :idConsolidacion " +
		"GROUP BY r.idProductoOrigen, r.productoOrigen")
	List<ProductoAgregadoDto> findProductosAgregadosByIdConsolidacion(@Param("idConsolidacion") Long idConsolidacion);

	/**
	 * Elimina un lote acotado de registros asociados a una consolidación.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "DELETE FROM schsipro.sipro_detalle_consolidado_registros "
			+ "WHERE id_consolidado_registro IN ("
			+ "    SELECT id_consolidado_registro "
			+ "    FROM schsipro.sipro_detalle_consolidado_registros "
			+ "    WHERE id_consolidacion = :idConsolidacion "
			+ "    ORDER BY id_consolidado_registro "
			+ "    LIMIT :batchSize"
			+ ")",
			nativeQuery = true)
	int deleteBatchByIdConsolidacion(@Param("idConsolidacion") Long idConsolidacion,
	                                 @Param("batchSize") int batchSize);
}