package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionArchivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Consulta los archivos incluidos dentro de una consolidación manual o automática.
 */
@Repository
public interface SiproDetalleConsolidacionArchivoRepository extends JpaRepository<SiproDetalleConsolidacionArchivo, Long> {

	/**
	 * Lista los archivos de una consolidación en el orden en que fueron asociados.
	 */
	List<SiproDetalleConsolidacionArchivo> findByIdConsolidacionOrderByIdCargaPlanillaAsc(Long idConsolidacion);

	/**
	 * Elimina los archivos asociados a una consolidación específica.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("DELETE FROM SiproDetalleConsolidacionArchivo a WHERE a.idConsolidacion = :idConsolidacion")
	int deleteByIdConsolidacion(@Param("idConsolidacion") Long idConsolidacion);
}