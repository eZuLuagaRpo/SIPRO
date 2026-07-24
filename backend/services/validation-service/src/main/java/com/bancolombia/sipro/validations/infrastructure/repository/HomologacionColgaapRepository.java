package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.HomologacionColgaap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;

@Repository
public interface HomologacionColgaapRepository extends JpaRepository<HomologacionColgaap, String> {

    @Query("SELECT h.cuentaSap FROM HomologacionColgaap h WHERE h.cuentaSap IN :cuentas")
    Set<String> findExistingCuentasSap(@Param("cuentas") Collection<String> cuentas);
}