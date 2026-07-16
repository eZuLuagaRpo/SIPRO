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
     * Busca los NITs (numeroid_externo) que SÍ existen en la tabla para un periodo y conjunto dados.
     * Retorna solo los que existen; los ausentes en el resultado son los que no cruzan.
     */
    @Query("SELECT DISTINCT c.numeroidExterno FROM SiproLzMdmCliente c " +
           "WHERE c.periodYear = :year AND c.periodMonth = :month " +
           "AND c.numeroidExterno IN :nits")
    Set<String> findExistingNits(@Param("year") int year,
                                  @Param("month") int month,
                                  @Param("nits") Collection<String> nits);

    /**
     * Obtiene el tipo de documento más reciente por numero_id para el conjunto solicitado.
     * Se usa durante la consolidación para enriquecer TIPO_ID sin hacer N+1 queries.
     */
    @Query(value = """
            SELECT ranked.numero_id AS numeroId,
                   ranked.tipo_id AS tipoId
            FROM (
                SELECT TRIM(c.numero_id) AS numero_id,
                       c.tipo_id,
                       ROW_NUMBER() OVER (
                           PARTITION BY TRIM(c.numero_id)
                                             ORDER BY c.f_ult_actualizacion DESC NULLS LAST,
                                                               COALESCE(c.year, 0) DESC,
                                    COALESCE(c.month, 0) DESC,
                                    COALESCE(c.day, 0) DESC,
                                    c.period_year DESC,
                                    c.period_month DESC,
                                    c.ingestion_run_id DESC
                       ) AS rn
                FROM sipro_lz_mdm_datos_generales_clientes c
                WHERE TRIM(c.numero_id) IN (:documentos)
            ) ranked
            WHERE ranked.rn = 1
            """, nativeQuery = true)
    List<DocumentoTipoIdProjection> findLatestTipoIdByNumeroIdIn(@Param("documentos") Collection<String> documentos);
}
