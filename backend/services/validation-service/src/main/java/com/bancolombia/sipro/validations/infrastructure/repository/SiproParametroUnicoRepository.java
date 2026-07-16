package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproParametroUnico;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Accede a parámetros simples guardados por clave única.
 */
public interface SiproParametroUnicoRepository extends JpaRepository<SiproParametroUnico, Integer> {

    /**
     * Busca un parámetro único por su clave funcional.
     */
    Optional<SiproParametroUnico> findByClave(String clave);

    /**
     * Bloquea pesimistamente una clave para reservar secuencias concurrentes sin duplicados.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM SiproParametroUnico p WHERE p.clave = :clave")
    Optional<SiproParametroUnico> findByClaveForUpdate(@Param("clave") String clave);
}
