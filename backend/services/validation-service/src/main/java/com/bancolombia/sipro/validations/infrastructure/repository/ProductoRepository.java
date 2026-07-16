package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Accede al catálogo de productos disponibles para la operación de SIPRO.
 */
@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    
    /**
     * Obtiene todos los productos ordenados alfabéticamente por título.
     * Spring Data JPA genera automáticamente la query.
     */
    List<Producto> findAllByOrderByTituloAsc();

    /**
     * Busca un producto por nombre de archivo y segmento (llave de unicidad de negocio).
     * Se usa para evitar duplicados al crear o editar productos.
     */
    java.util.Optional<Producto> findByNombreArchivoPermitidoAndIdSegmento(String nombreArchivoPermitido, Long idSegmento);

        /**
         * Obtiene los productos activos que el usuario autenticado puede cargar.
         */
        @Query("SELECT DISTINCT p FROM SiproUsuarioProductoRol upr " +
            "JOIN upr.rol r " +
            "JOIN upr.producto p " +
            "WHERE upr.id.idUsuario = :idUsuario " +
            "AND upr.activo = true " +
            "AND r.cargarArchivos = 1 " +
            "AND (:segmentoId IS NULL OR p.idSegmento = :segmentoId) " +
                "AND (p.activo IS NULL OR p.activo = 1) " +
            "ORDER BY p.titulo ASC")
        List<Producto> findProductosPermitidosParaCarga(@Param("idUsuario") Long idUsuario,
                                   @Param("segmentoId") Long segmentoId);

    /**
     * Busca un producto por su título exacto (case insensitive si se requiere, pero aquí estricto)
     */
    java.util.Optional<Producto> findByTitulo(String titulo);
}
