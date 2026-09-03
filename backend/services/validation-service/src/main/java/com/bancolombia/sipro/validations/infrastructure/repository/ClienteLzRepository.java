package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproLzMdmCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Repositorio para consultar clientes en la tabla LZ local (PostgreSQL).
 * Usado para validar existencia de NIT durante la carga de archivos.
 */
@Repository
public interface ClienteLzRepository extends JpaRepository<SiproLzMdmCliente, SiproLzMdmCliente.PK> {

       interface DocumentoTipoIdProjection {
              String getNumeroId();
              String getTipoId();
       }

    /**
     * Verifica si existen datos para un period_year/period_month dado.
     */
    @Query("SELECT COUNT(c) FROM SiproLzMdmCliente c WHERE c.periodYear = :year AND c.periodMonth = :month")
    long countByPeriod(@Param("year") int year, @Param("month") int month);

    /**
     * Busca los NITs que existen como numero_id o numeroid_externo para un periodo dado.
     * Retorna el mismo valor recibido desde Excel para que el caller marque el cruce correctamente.
     */
    @Query(value = """
            SELECT DISTINCT matched.nit
            FROM (
                                                        SELECT TRIM(CAST(c.numero_id AS text)) AS nit
                FROM schsipro.sipro_lz_mdm_datos_generales_clientes c
                WHERE c.period_year = :year AND c.period_month = :month
                  AND c.numero_id IS NOT NULL
                                                               AND TRIM(CAST(c.numero_id AS text)) IN (:nits)
                UNION
                                                        SELECT TRIM(CAST(c.numeroid_externo AS text)) AS nit
                FROM schsipro.sipro_lz_mdm_datos_generales_clientes c
                WHERE c.period_year = :year AND c.period_month = :month
                  AND c.numeroid_externo IS NOT NULL
                                                               AND TRIM(CAST(c.numeroid_externo AS text)) IN (:nits)
            ) matched
            """, nativeQuery = true)
    Set<String> findExistingNits(@Param("year") int year,
                                  @Param("month") int month,
                                  @Param("nits") Collection<String> nits);

    /**
     * Obtiene el tipo de documento más reciente cruzando por numero_id o numeroid_externo.
     * Se usa durante la consolidación para enriquecer TIPO_ID sin hacer N+1 queries.
     */
    @Query(value = """
            SELECT ranked.numero_id AS numeroId,
                   ranked.tipo_id AS tipoId
            FROM (
                SELECT candidate.numero_id,
                       candidate.tipo_id,
                       ROW_NUMBER() OVER (
                           PARTITION BY candidate.numero_id
                           ORDER BY candidate.match_priority ASC,
                                    candidate.f_ult_actualizacion DESC NULLS LAST,
                                    COALESCE(candidate.year, 0) DESC,
                                    COALESCE(candidate.month, 0) DESC,
                                    COALESCE(candidate.day, 0) DESC,
                                    candidate.period_year DESC,
                                    candidate.period_month DESC,
                                    candidate.ingestion_run_id DESC
                       ) AS rn
                FROM (
                                   SELECT TRIM(CAST(c.numero_id AS text)) AS numero_id,
                           c.tipo_id,
                           1 AS match_priority,
                           c.f_ult_actualizacion,
                           c.year,
                           c.month,
                           c.day,
                           c.period_year,
                           c.period_month,
                           c.ingestion_run_id
                    FROM schsipro.sipro_lz_mdm_datos_generales_clientes c
                    WHERE c.numero_id IS NOT NULL
                                                                             AND TRIM(CAST(c.numero_id AS text)) IN (:documentos)
                    UNION ALL
                                                                      SELECT TRIM(CAST(c.numeroid_externo AS text)) AS numero_id,
                           c.tipo_id,
                           2 AS match_priority,
                           c.f_ult_actualizacion,
                           c.year,
                           c.month,
                           c.day,
                           c.period_year,
                           c.period_month,
                           c.ingestion_run_id
                    FROM schsipro.sipro_lz_mdm_datos_generales_clientes c
                    WHERE c.numeroid_externo IS NOT NULL
                                                                             AND TRIM(CAST(c.numeroid_externo AS text)) IN (:documentos)
                ) candidate
            ) ranked
            WHERE ranked.rn = 1
            """, nativeQuery = true)
    List<DocumentoTipoIdProjection> findLatestTipoIdByNumeroIdIn(@Param("documentos") Collection<String> documentos);
}