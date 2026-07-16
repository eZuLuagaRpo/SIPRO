package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.Segmento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Accede al catálogo de segmentos usados por productos y validaciones.
 */
@Repository
public interface SegmentoRepository extends JpaRepository<Segmento, Long> {

    /**
     * Obtiene todos los segmentos ordenados alfabéticamente por nombre.
     * Spring Data JPA genera automáticamente la query.
     */
    List<Segmento> findAllByOrderByNombreAsc();

        /**
         * Obtiene los segmentos con al menos un producto cargable para el usuario.
         */
        @Query("SELECT DISTINCT s FROM Segmento s, SiproUsuarioProductoRol upr " +
            "JOIN upr.rol r " +
            "JOIN upr.producto p " +
            "WHERE upr.id.idUsuario = :idUsuario " +
            "AND upr.activo = true " +
            "AND r.cargarArchivos = 1 " +
            "AND s.id = p.idSegmento " +
            "AND (p.activo IS NULL OR p.activo = 1) " +
            "ORDER BY s.nombre ASC")
        List<Segmento> findSegmentosPermitidosParaCarga(@Param("idUsuario") Long idUsuario);

    /**
     * Busca un segmento por nombre ignorando mayúsculas y minúsculas.
     */
    Optional<Segmento> findByNombreIgnoreCase(String nombre);
}
