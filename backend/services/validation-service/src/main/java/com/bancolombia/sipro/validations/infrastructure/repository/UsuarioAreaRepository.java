package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.UsuarioArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Accede a la relación local del usuario con su área y jefe inmediato.
 */
@Repository
public interface UsuarioAreaRepository extends JpaRepository<UsuarioArea, Long> {

    @Query("SELECT ua FROM UsuarioArea ua JOIN FETCH ua.jefe WHERE ua.idUsuario IN :ids")
    List<UsuarioArea> findWithJefeByIdUsuarioIn(@Param("ids") Collection<Long> ids);
}