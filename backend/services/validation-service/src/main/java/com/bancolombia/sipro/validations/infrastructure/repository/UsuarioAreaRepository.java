package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.UsuarioArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Accede a la relación local del usuario con su área y jefe inmediato.
 */
@Repository
public interface UsuarioAreaRepository extends JpaRepository<UsuarioArea, Long> {
}