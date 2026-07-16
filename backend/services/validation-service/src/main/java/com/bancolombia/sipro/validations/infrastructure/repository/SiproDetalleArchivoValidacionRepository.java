package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleArchivoValidacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Gestiona el detalle persistido del archivo generado durante una validación.
 */
@Repository
public interface SiproDetalleArchivoValidacionRepository extends JpaRepository<SiproDetalleArchivoValidacion, Long> {

    /**
     * Recupera el detalle de validación asociado a una carga de planilla.
     */
    Optional<SiproDetalleArchivoValidacion> findByIdCargaPlanilla(Long idCargaPlanilla);

    /**
     * Recupera en lote los detalles de validación asociados a varias cargas.
     */
    List<SiproDetalleArchivoValidacion> findByIdCargaPlanillaIn(Collection<Long> idsCargaPlanilla);
}
