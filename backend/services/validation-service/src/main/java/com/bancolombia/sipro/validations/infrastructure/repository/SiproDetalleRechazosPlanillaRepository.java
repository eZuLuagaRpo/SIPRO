package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleRechazosPlanilla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Accede al detalle de registros rechazados durante la validación de una planilla.
 */
@Repository
public interface SiproDetalleRechazosPlanillaRepository extends JpaRepository<SiproDetalleRechazosPlanilla, Long> {

    /**
     * Lista los rechazos registrados para una carga de planilla.
     */
    List<SiproDetalleRechazosPlanilla> findByIdCargaPlanilla(Long idCargaPlanilla);
}
