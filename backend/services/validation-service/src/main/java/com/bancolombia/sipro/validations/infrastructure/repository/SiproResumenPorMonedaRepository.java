package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproResumenPorMoneda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Administra el resumen monetario calculado para cada validación de archivo.
 */
public interface SiproResumenPorMonedaRepository extends JpaRepository<SiproResumenPorMoneda, Long> {

    /**
     * Lista el resumen por moneda de una validación en orden por código.
     */
    List<SiproResumenPorMoneda> findByIdValidacionOrderByCodigoMonedaAsc(Long idValidacion);

    /**
     * Elimina el resumen monetario asociado a una validación.
     */
    @Modifying
    @Transactional
    void deleteByIdValidacion(Long idValidacion);
}
