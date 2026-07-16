package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproLzIngestionRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Guarda el historial de ejecuciones de ingesta desde Landing Zone.
 */
@Repository
public interface SiproLzIngestionRunRepository extends JpaRepository<SiproLzIngestionRun, Long> {

    Optional<SiproLzIngestionRun> findFirstByIdTablaAndPeriodYearAndPeriodMonthAndStatusOrderByStartedAtDesc(
      Integer idTabla,
      int year,
      int month,
      String status);

    default Optional<SiproLzIngestionRun> findSuccess(Integer idTabla, int year, int month) {
      return findFirstByIdTablaAndPeriodYearAndPeriodMonthAndStatusOrderByStartedAtDesc(
        idTabla,
        year,
        month,
        "SUCCESS");
    }
}
