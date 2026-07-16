package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Administra la persistencia operativa de cargas de planillas y su ciclo de aprobación.
 */
@Repository
public interface SiproDetalleCargaPlanillasRepository extends JpaRepository<SiproDetalleCargaPlanillas, Long> {

    // ===== Métodos originales (sin filtro activo - para consultas históricas si se
    // necesitan) =====
    List<SiproDetalleCargaPlanillas> findByEstadoPlanilla(String estadoPlanilla);

    List<SiproDetalleCargaPlanillas> findByCorreoLider(String correoLider);

    List<SiproDetalleCargaPlanillas> findByCorreoLiderAndEstadoPlanilla(String correoLider, String estadoPlanilla);

    List<SiproDetalleCargaPlanillas> findAllByOrderByFechaCreacionDesc();

    // ===== Métodos filtrados por activo = true (para uso operativo) =====
    List<SiproDetalleCargaPlanillas> findByEstadoPlanillaAndActivoTrue(String estadoPlanilla);

        List<SiproDetalleCargaPlanillas> findByActivoTrueAndArchivoUidStartingWith(String archivoUidPrefix);

    List<SiproDetalleCargaPlanillas> findByCorreoLiderAndActivoTrue(String correoLider);

    List<SiproDetalleCargaPlanillas> findByCorreoLiderAndEstadoPlanillaAndActivoTrue(String correoLider,
            String estadoPlanilla);

    List<SiproDetalleCargaPlanillas> findAllByActivoTrueOrderByFechaCreacionDesc();

    // ===== Métodos por id_lider (preferidos sobre correoLider) =====
    List<SiproDetalleCargaPlanillas> findByIdLiderAndActivoTrueOrderByFechaCreacionDesc(Long idLider);

    @Query("SELECT p FROM SiproDetalleCargaPlanillas p " +
           "WHERE p.idProducto IN :productIds " +
           "AND p.activo = true " +
           "ORDER BY p.fechaCreacion DESC")
    List<SiproDetalleCargaPlanillas> findByProductIdsAndActivoTrueOrderByFechaCreacionDesc(
            @Param("productIds") List<Long> productIds);

    List<SiproDetalleCargaPlanillas> findByIdLiderAndEstadoPlanillaAndActivoTrue(Long idLider, String estadoPlanilla);

    @Query("SELECT COUNT(p) FROM SiproDetalleCargaPlanillas p " +
           "WHERE p.idLider = :idLider " +
           "AND p.estadoPlanilla = :estado " +
           "AND p.activo = true")
    long countByIdLiderAndEstadoPlanillaAndActivoTrue(
            @Param("idLider") Long idLider,
            @Param("estado") String estado);

    @Query("SELECT COUNT(p) FROM SiproDetalleCargaPlanillas p " +
           "WHERE p.idUsuarioCarga = :idUsuarioCarga " +
           "AND p.estadoPlanilla = :estado " +
           "AND p.activo = true")
    long countByIdUsuarioCargaAndEstadoPlanillaAndActivoTrue(
            @Param("idUsuarioCarga") Long idUsuarioCarga,
            @Param("estado") String estado);

    @Modifying
    @Query("UPDATE SiproDetalleCargaPlanillas p " +
            "SET p.idLider = :idNuevoLider, " +
            "p.nombreLider = :nombreLider, " +
            "p.correoLider = :correoLider " +
            "WHERE p.idUsuarioCarga IN :idsUsuarios " +
            "AND p.estadoPlanilla = :estado " +
            "AND p.activo = true")
    int reasignarPendientesActivosPorUsuarios(@Param("idsUsuarios") List<Long> idsUsuarios,
                                                    @Param("idNuevoLider") Long idNuevoLider,
                                                    @Param("nombreLider") String nombreLider,
                                                    @Param("correoLider") String correoLider,
                                                    @Param("estado") String estado);

    /**
     * Selecciona planillas en estado PENDIENTE o RECHAZADO del líder anterior para los cargadores afectados.
     * Usado para construir el resumen de transferencia y enviar la notificación al nuevo líder.
     */
    @Query("SELECT p FROM SiproDetalleCargaPlanillas p " +
           "WHERE p.idUsuarioCarga IN :idsUsuarios " +
           "AND p.idLider = :idLiderAnterior " +
           "AND p.estadoPlanilla IN ('PENDIENTE', 'RECHAZADO') " +
           "AND p.activo = true " +
           "ORDER BY p.idUsuarioCarga, p.fechaCorteInformacion DESC")
    List<SiproDetalleCargaPlanillas> findPendientesYRechazadosPorCargadoresYLider(
            @Param("idsUsuarios") List<Long> idsUsuarios,
            @Param("idLiderAnterior") Long idLiderAnterior);

    /**
     * Transfiere planillas PENDIENTE y RECHAZADO de un conjunto de cargadores al nuevo líder.
     * Solo se tocan planillas activas del líder anterior; las aprobadas/consolidadas no se modifican.
     */
    @Modifying
    @Query("UPDATE SiproDetalleCargaPlanillas p " +
           "SET p.idLider = :idNuevoLider, " +
           "p.nombreLider = :nombreLider, " +
           "p.correoLider = :correoLider " +
           "WHERE p.idUsuarioCarga IN :idsUsuarios " +
           "AND p.idLider = :idLiderAnterior " +
           "AND p.estadoPlanilla IN ('PENDIENTE', 'RECHAZADO') " +
           "AND p.activo = true")
    int transferirPlanillasActivasAlNuevoLider(
            @Param("idsUsuarios") List<Long> idsUsuarios,
            @Param("idLiderAnterior") Long idLiderAnterior,
            @Param("idNuevoLider") Long idNuevoLider,
            @Param("nombreLider") String nombreLider,
            @Param("correoLider") String correoLider);

    List<SiproDetalleCargaPlanillas> findByFechaCorteInformacionAndEstadoPlanillaAndActivoTrue(
            LocalDate fechaCorteInformacion,
            String estadoPlanilla);

    List<SiproDetalleCargaPlanillas> findByFechaCorteInformacionAndActivoTrueOrderByIdAsc(
            LocalDate fechaCorteInformacion);

    // ===== Métodos para versionado/inactivación =====

    /**
     * Busca el registro activo para la llave funcional (fecha_corte +
     * id_producto) con bloqueo pesimista
     * para evitar condiciones de carrera.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM SiproDetalleCargaPlanillas p WHERE p.fechaCorteInformacion = :fechaCorte AND p.idProducto = :idProducto AND p.activo = true")
    Optional<SiproDetalleCargaPlanillas> findActiveByFechaCorteAndIdProductoForUpdate(
            @Param("fechaCorte") LocalDate fechaCorte,
            @Param("idProducto") Long idProducto);

    /**
     * Inactiva el registro activo anterior para la misma llave funcional.
     * Retorna el número de filas afectadas (0 o 1).
     */
    @Modifying
    @Query("UPDATE SiproDetalleCargaPlanillas p SET p.activo = false, p.fechaInactivacion = CURRENT_TIMESTAMP WHERE p.fechaCorteInformacion = :fechaCorte AND p.idProducto = :idProducto AND p.activo = true")
    int inactivatePreviousActiveByFechaCorteAndIdProducto(
            @Param("fechaCorte") LocalDate fechaCorte,
            @Param("idProducto") Long idProducto);

    // ===== Resumen de cargas por correo usuario =====

    @Query("SELECT COUNT(p) FROM SiproDetalleCargaPlanillas p WHERE p.correoUsuarioCarga = :correo AND p.estadoPlanilla = :estado AND p.activo = true")
    long countByCorreoUsuarioCargaAndEstadoPlanillaAndActivoTrue(
            @Param("correo") String correo,
            @Param("estado") String estado);

    @Query("SELECT MAX(p.fechaCreacion) FROM SiproDetalleCargaPlanillas p WHERE p.correoUsuarioCarga = :correo AND p.activo = true")
    Optional<LocalDateTime> findUltimaCargaByCorreoUsuario(@Param("correo") String correo);

    /**
     * Verifica si existe al menos una carga activa (no rechazada) para un usuario + producto
     * cuya fecha_corte_informacion caiga dentro de un rango de fechas (mes anterior).
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
           "FROM SiproDetalleCargaPlanillas p " +
           "WHERE p.idUsuarioCarga = :idUsuario " +
           "AND p.idProducto = :idProducto " +
           "AND p.activo = true " +
           "AND p.estadoPlanilla <> 'RECHAZADO' " +
           "AND p.fechaCorteInformacion BETWEEN :fechaInicio AND :fechaFin")
    boolean existsCargaActivaForProductoInMonth(
            @Param("idUsuario") Long idUsuario,
            @Param("idProducto") Long idProducto,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin);

        /**
         * Busca planillas PENDIENTE de aprobar por un líder dentro de un rango de fechas de corte.
         * Devuelve solo las activas con estado PENDIENTE asignadas al líder por id_lider.
         */
    @Query("SELECT p FROM SiproDetalleCargaPlanillas p " +
           "WHERE p.idLider = :idLider " +
           "AND p.estadoPlanilla = 'PENDIENTE' " +
           "AND p.activo = true " +
           "AND p.fechaCorteInformacion BETWEEN :fechaInicio AND :fechaFin " +
           "ORDER BY p.fechaCorteInformacion DESC, p.producto ASC")
    List<SiproDetalleCargaPlanillas> findPendientesAprobacionByLider(
            @Param("idLider") Long idLider,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin);

    // ===== Queries para vista de aprobador (basado en productos RBAC) =====

    /**
     * Busca planillas PENDIENTE de aprobar para una lista de productos.
     * El aprobador ve las planillas de los productos a los que tiene acceso con rol de aprobación.
     */
    @Query("SELECT p FROM SiproDetalleCargaPlanillas p " +
           "WHERE p.idProducto IN :productIds " +
           "AND p.estadoPlanilla = 'PENDIENTE' " +
           "AND p.activo = true " +
           "AND p.fechaCorteInformacion BETWEEN :fechaInicio AND :fechaFin " +
           "ORDER BY p.fechaCorteInformacion DESC, p.producto ASC")
    List<SiproDetalleCargaPlanillas> findPendientesAprobacionByProductos(
            @Param("productIds") List<Long> productIds,
            @Param("fechaInicio") LocalDate fechaInicio,
            @Param("fechaFin") LocalDate fechaFin);

    /**
     * Cuenta planillas activas por lista de productos y estado.
     * Útil para el resumen del aprobador.
     */
    @Query("SELECT COUNT(p) FROM SiproDetalleCargaPlanillas p " +
           "WHERE p.idProducto IN :productIds " +
           "AND p.estadoPlanilla = :estado " +
           "AND p.activo = true")
    long countByProductIdsAndEstado(
            @Param("productIds") List<Long> productIds,
            @Param("estado") String estado);

    /**
     * Obtiene la fecha de la última aprobación real en los productos del aprobador.
     * Usa fecha_aprobacion, no fecha_creacion, para evitar desfases en el resumen.
     */
    @Query("SELECT MAX(p.fechaAprobacion) FROM SiproDetalleCargaPlanillas p " +
           "WHERE p.idProducto IN :productIds " +
            "AND p.estadoPlanilla = 'APROBADO' " +
            "AND p.fechaAprobacion IS NOT NULL " +
           "AND p.activo = true")
    Optional<OffsetDateTime> findUltimaAprobacionByProductos(
            @Param("productIds") List<Long> productIds);

    /**
     * Obtiene la última aprobación registrada para las planillas asignadas a un líder.
     */
    @Query("SELECT MAX(p.fechaAprobacion) FROM SiproDetalleCargaPlanillas p " +
            "WHERE p.idLider = :idLider " +
            "AND p.estadoPlanilla = 'APROBADO' " +
            "AND p.fechaAprobacion IS NOT NULL " +
            "AND p.activo = true")
    Optional<OffsetDateTime> findUltimaAprobacionByLider(
             @Param("idLider") Long idLider);

    /**
     * Lista las fechas de corte activas que tienen planillas en el estado indicado.
     */
    @Query("SELECT DISTINCT p.fechaCorteInformacion FROM SiproDetalleCargaPlanillas p " +
            "WHERE p.estadoPlanilla = :estado " +
            "AND p.activo = true " +
            "ORDER BY p.fechaCorteInformacion ASC")
    List<LocalDate> findDistinctFechasCorteInformacionByEstadoPlanillaAndActivoTrue(
             @Param("estado") String estado);

    /**
     * Lista periodos con al menos una planilla activa del segmento indicado en estado aprobado.
     * El segmento real se deriva del catálogo de productos, no del texto libre almacenado en la carga.
     */
    @Query("SELECT DISTINCT cp.fechaCorteInformacion FROM SiproDetalleCargaPlanillas cp, Producto prod " +
            "WHERE cp.idProducto = prod.idProducto " +
            "AND prod.idSegmento = :idSegmento " +
            "AND cp.activo = true " +
            "AND LOWER(COALESCE(cp.estadoPlanilla, '')) IN ('aprobado', 'archivo aprobado', 'aprobación sin datos', 'aprobacion sin datos') " +
            "ORDER BY cp.fechaCorteInformacion ASC")
    List<LocalDate> findDistinctFechasCorteInformacionAprobadasBySegmentoId(
            @Param("idSegmento") Long idSegmento);

    /**
     * Cuenta planillas activas del segmento indicado que aún no están en un estado aprobado.
     */
    @Query("SELECT COUNT(cp) FROM SiproDetalleCargaPlanillas cp, Producto prod " +
            "WHERE cp.idProducto = prod.idProducto " +
            "AND prod.idSegmento = :idSegmento " +
            "AND cp.fechaCorteInformacion = :fechaCorte " +
            "AND cp.activo = true " +
            "AND LOWER(COALESCE(cp.estadoPlanilla, '')) NOT IN ('aprobado', 'archivo aprobado', 'aprobación sin datos', 'aprobacion sin datos')")
    long countPlanillasNoAprobadasByFechaCorteAndSegmentoId(
            @Param("fechaCorte") LocalDate fechaCorte,
            @Param("idSegmento") Long idSegmento);

    /**
     * Obtiene las planillas activas y aprobadas del segmento indicado para un periodo.
     */
    @Query("SELECT cp FROM SiproDetalleCargaPlanillas cp, Producto prod " +
            "WHERE cp.idProducto = prod.idProducto " +
            "AND prod.idSegmento = :idSegmento " +
            "AND cp.fechaCorteInformacion = :fechaCorte " +
            "AND cp.activo = true " +
            "AND LOWER(COALESCE(cp.estadoPlanilla, '')) IN ('aprobado', 'archivo aprobado', 'aprobación sin datos', 'aprobacion sin datos') " +
            "ORDER BY cp.id ASC")
    List<SiproDetalleCargaPlanillas> findPlanillasAprobadasByFechaCorteAndSegmentoId(
            @Param("fechaCorte") LocalDate fechaCorte,
            @Param("idSegmento") Long idSegmento);

    /**
     * Obtiene las planillas activas del segmento indicado para un periodo, sin filtrar por estado.
     */
    @Query("SELECT cp FROM SiproDetalleCargaPlanillas cp, Producto prod " +
            "WHERE cp.idProducto = prod.idProducto " +
            "AND prod.idSegmento = :idSegmento " +
            "AND cp.fechaCorteInformacion = :fechaCorte " +
            "AND cp.activo = true " +
            "ORDER BY cp.id ASC")
    List<SiproDetalleCargaPlanillas> findPlanillasActivasByFechaCorteAndSegmentoId(
            @Param("fechaCorte") LocalDate fechaCorte,
            @Param("idSegmento") Long idSegmento);

    /**
     * Busca planillas activas para un año, mes y segmento de producto dados.
     * Cruza con la tabla de productos para filtrar por id_segmento.
     * Se usa en el tablero de control para determinar el estado por periodo.
     */
    @Query("SELECT p FROM SiproDetalleCargaPlanillas p, Producto prod " +
           "WHERE p.idProducto = prod.idProducto " +
           "AND prod.idSegmento = :idSegmento " +
           "AND p.year = :anio AND p.month = :mes " +
           "AND p.activo = true " +
           "ORDER BY p.id ASC")
    List<SiproDetalleCargaPlanillas> findActivasByAnioMesAndSegmentoId(
            @Param("anio") int anio,
            @Param("mes") int mes,
            @Param("idSegmento") Long idSegmento);

    /**
     * Devuelve los pares año-mes distintos con al menos una planilla activa.
     * Se usa para construir el selector de periodo del tablero de control.
     */
    @Query("SELECT DISTINCT p.year, p.month FROM SiproDetalleCargaPlanillas p WHERE p.activo = true ORDER BY p.year DESC, p.month DESC")
    List<Object[]> findDistinctAniosMeses();
}