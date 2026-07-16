package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproLzCatalogoTablas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Administra el catálogo de tablas de Landing Zone disponibles para ingesta.
 */
@Repository
public interface SiproLzCatalogoTablasRepository extends JpaRepository<SiproLzCatalogoTablas, Integer> {

    /**
     * Busca una tabla por su id.
     */
    Optional<SiproLzCatalogoTablas> findByIdTabla(Integer idTabla);

    /**
     * Busca una tabla por nombre de origen (case-insensitive, trimmed).
     */
    @Query("""
        SELECT t FROM SiproLzCatalogoTablas t
        WHERE LOWER(TRIM(t.tablaOrigen)) = LOWER(TRIM(:tablaOrigen))
    """)
    Optional<SiproLzCatalogoTablas> findByTablaOrigenNorm(@Param("tablaOrigen") String tablaOrigen);

    /**
     * Todas las tablas activas del catalogo.
     */
    List<SiproLzCatalogoTablas> findByActivoTrue();
}
