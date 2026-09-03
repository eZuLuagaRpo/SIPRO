package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.HomologacionColgaap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface HomologacionColgaapRepository extends JpaRepository<HomologacionColgaap, Long> {

    @Query("SELECT h.cuentaSap FROM HomologacionColgaap h WHERE h.cuentaSap IN :cuentas AND h.estado = 1")
    Set<String> findExistingCuentasSap(@Param("cuentas") Collection<String> cuentas);

    @Query("SELECT h.cuentaSap FROM HomologacionColgaap h WHERE h.cuentaSap IN :cuentas")
    Set<String> findAllExistingCuentasSap(@Param("cuentas") Collection<String> cuentas);

    boolean existsByCuentaSap(String cuentaSap);

    List<HomologacionColgaap> findAllByOrderByEstadoDescCreadoEnDesc();

    @Query("SELECT h FROM HomologacionColgaap h WHERE h.cuentaSap = :cuentaSap AND h.estado = 1")
    Optional<HomologacionColgaap> findActivaByCuentaSap(@Param("cuentaSap") String cuentaSap);
}