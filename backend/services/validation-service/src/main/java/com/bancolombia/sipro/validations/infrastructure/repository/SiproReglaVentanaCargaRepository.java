package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproReglaVentanaCarga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository para acceder a las reglas generales de ventana de carga.
 */
@Repository
public interface SiproReglaVentanaCargaRepository extends JpaRepository<SiproReglaVentanaCarga, Long> {

    /**
     * Busca la regla activa vigente para una fecha dada.
     * activa = true, vigente_desde <= fecha, vigente_hasta IS NULL o >= fecha.
     * Ordena por vigente_desde DESC para tomar la más reciente.
     */
    @Query("SELECT r FROM SiproReglaVentanaCarga r " +
           "WHERE r.activa = true " +
           "AND r.vigenteDesde <= :fecha " +
           "AND (r.vigenteHasta IS NULL OR r.vigenteHasta >= :fecha) " +
           "ORDER BY r.vigenteDesde DESC")
    Optional<SiproReglaVentanaCarga> findReglaVigente(@Param("fecha") LocalDate fecha);
}
