package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.UsuarioPersona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Accede a la información personal complementaria de los usuarios de SIPRO.
 */
@Repository
public interface UsuarioPersonaRepository extends JpaRepository<UsuarioPersona, Long> {

	boolean existsByCorreoIgnoreCase(String correo);
}