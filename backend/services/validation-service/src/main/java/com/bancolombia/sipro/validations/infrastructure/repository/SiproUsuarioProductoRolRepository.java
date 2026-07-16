package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproUsuarioProductoRol;
import com.bancolombia.sipro.validations.domain.model.SiproUsuarioProductoRolId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Gestiona las asignaciones activas entre usuarios, productos y roles del flujo SIPRO.
 */
@Repository
public interface SiproUsuarioProductoRolRepository extends JpaRepository<SiproUsuarioProductoRol, SiproUsuarioProductoRolId> {

    /**
     * Obtiene todas las asignaciones activas de un usuario.
     * Incluye datos del rol (EAGER) para construir el mapa de permisos.
     */
    @Query("SELECT upr FROM SiproUsuarioProductoRol upr " +
           "JOIN FETCH upr.rol " +
           "WHERE upr.id.idUsuario = :idUsuario AND upr.activo = true")
    List<SiproUsuarioProductoRol> findActiveByUsuario(@Param("idUsuario") Long idUsuario);

    /**
     * Obtiene asignaciones activas de un usuario con el producto pre-cargado (EAGER).
     * Útil para obtener título del producto sin N+1 queries.
     */
    @Query("SELECT upr FROM SiproUsuarioProductoRol upr " +
           "JOIN FETCH upr.rol " +
           "JOIN FETCH upr.producto " +
           "WHERE upr.id.idUsuario = :idUsuario AND upr.activo = true")
    List<SiproUsuarioProductoRol> findActiveByUsuarioWithProducto(@Param("idUsuario") Long idUsuario);

    /**
     * Verifica si un usuario tiene un permiso específico (por id_rol) en un producto dado.
     */
    boolean existsByIdIdUsuarioAndIdIdProductoAndIdIdRolAndActivoTrue(
            Long idUsuario, Long idProducto, Integer idRol);

    /**
     * Verifica si un usuario tiene al menos una asignación activa con permiso de carga
     * en cualquier producto. Usa la columna cargar_archivos del rol asociado.
     */
    @Query("SELECT CASE WHEN COUNT(upr) > 0 THEN true ELSE false END " +
           "FROM SiproUsuarioProductoRol upr " +
           "JOIN upr.rol r " +
           "WHERE upr.id.idUsuario = :idUsuario " +
           "AND upr.activo = true " +
           "AND r.cargarArchivos = 1")
    boolean canUserUploadAny(@Param("idUsuario") Long idUsuario);

    /**
     * Verifica si un usuario tiene al menos una asignación activa con permiso de aprobación
     * en cualquier producto.
     */
    @Query("SELECT CASE WHEN COUNT(upr) > 0 THEN true ELSE false END " +
           "FROM SiproUsuarioProductoRol upr " +
           "WHERE upr.id.idUsuario = :idUsuario " +
           "AND upr.activo = true " +
           "AND upr.id.idRol = 2")
    boolean canUserApproveAny(@Param("idUsuario") Long idUsuario);

    /**
     * Verifica si un usuario puede cargar archivos en un producto específico.
     */
    @Query("SELECT CASE WHEN COUNT(upr) > 0 THEN true ELSE false END " +
           "FROM SiproUsuarioProductoRol upr " +
           "JOIN upr.rol r " +
           "WHERE upr.id.idUsuario = :idUsuario " +
           "AND upr.id.idProducto = :idProducto " +
           "AND upr.activo = true " +
           "AND r.cargarArchivos = 1")
    boolean canUserUploadForProduct(@Param("idUsuario") Long idUsuario,
                                    @Param("idProducto") Long idProducto);

    /**
     * Verifica si un usuario puede aprobar en un producto específico.
     */
    @Query("SELECT CASE WHEN COUNT(upr) > 0 THEN true ELSE false END " +
           "FROM SiproUsuarioProductoRol upr " +
           "WHERE upr.id.idUsuario = :idUsuario " +
           "AND upr.id.idProducto = :idProducto " +
           "AND upr.activo = true " +
           "AND upr.id.idRol = 2")
    boolean canUserApproveForProduct(@Param("idUsuario") Long idUsuario,
                                     @Param("idProducto") Long idProducto);

    /**
     * Obtiene las asignaciones de un producto (para el módulo de administración).
     */
    @Query("SELECT upr FROM SiproUsuarioProductoRol upr " +
           "JOIN FETCH upr.usuario " +
           "JOIN FETCH upr.rol " +
           "WHERE upr.id.idProducto = :idProducto AND upr.activo = true " +
           "ORDER BY upr.rol.idRol, upr.ordenFlujo")
    List<SiproUsuarioProductoRol> findActiveByProducto(@Param("idProducto") Long idProducto);

    /**
     * Obtiene los correos activos de usuarios con alguno de los grupos AD indicados.
     */
    @Query("SELECT DISTINCT LOWER(TRIM(upr.usuario.correo)) FROM SiproUsuarioProductoRol upr " +
           "JOIN upr.rol rol " +
           "JOIN upr.usuario usuario " +
           "WHERE upr.activo = true " +
           "AND rol.grupoAd IN :gruposAd " +
           "AND usuario.correo IS NOT NULL " +
           "AND TRIM(usuario.correo) <> ''")
    List<String> findDistinctActiveEmailsByGrupoAdIn(@Param("gruposAd") Collection<String> gruposAd);

    /**
     * Obtiene los correos activos de los usuarios que tengan un rol específico.
     */
    @Query("SELECT DISTINCT LOWER(TRIM(usuario.correo)) FROM SiproUsuarioProductoRol upr " +
           "JOIN upr.usuario usuario " +
           "WHERE upr.activo = true " +
           "AND upr.id.idRol = :idRol " +
           "AND usuario.correo IS NOT NULL " +
           "AND TRIM(usuario.correo) <> ''")
    List<String> findDistinctActiveEmailsByRolId(@Param("idRol") Integer idRol);
}
