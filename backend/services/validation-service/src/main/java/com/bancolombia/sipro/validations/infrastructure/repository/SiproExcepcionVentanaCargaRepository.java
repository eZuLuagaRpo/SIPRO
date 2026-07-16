package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproExcepcionVentanaCarga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository para acceder a las excepciones de ventana de carga por periodo.
 */
@Repository
public interface SiproExcepcionVentanaCargaRepository extends JpaRepository<SiproExcepcionVentanaCarga, LocalDate> {

    /**
     * Busca la excepción para un periodo de valoración específico.
     */
    Optional<SiproExcepcionVentanaCarga> findByPeriodoValoracion(LocalDate periodoValoracion);
}
