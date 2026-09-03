package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.HomologacionFullIfrs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface HomologacionFullIfrsRepository extends JpaRepository<HomologacionFullIfrs, Long> {

    @Query("SELECT h.cuentaPlanilla FROM HomologacionFullIfrs h WHERE h.cuentaPlanilla IN :cuentas AND h.estado = 1")
    Set<String> findExistingCuentasPlanilla(@Param("cuentas") Collection<String> cuentas);

    @Query("SELECT h.cuentaPlanilla FROM HomologacionFullIfrs h WHERE h.cuentaPlanilla IN :cuentas")
    Set<String> findAllExistingCuentasPlanilla(@Param("cuentas") Collection<String> cuentas);

    boolean existsByCuentaPlanilla(String cuentaPlanilla);

    List<HomologacionFullIfrs> findAllByOrderByEstadoDescCreadoEnDesc();

    @Query("SELECT h FROM HomologacionFullIfrs h WHERE h.cuentaPlanilla = :cuentaPlanilla AND h.estado = 1")
    Optional<HomologacionFullIfrs> findActivaByCuentaPlanilla(@Param("cuentaPlanilla") String cuentaPlanilla);

    @Query("SELECT h FROM HomologacionFullIfrs h WHERE h.estado = 1 ORDER BY h.idHomologacionFullIfrs ASC")
    List<HomologacionFullIfrs> findActivasOrdenadas();
}