package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.SiproRolesPermisos;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Gestiona el catálogo de roles y permisos usados por el modelo RBAC.
 */
@Repository
public interface SiproRolesPermisosRepository extends JpaRepository<SiproRolesPermisos, Integer> {

    /**
     * Busca un rol por su nombre.
     */
    Optional<SiproRolesPermisos> findByRol(String rol);

    /**
     * Busca los roles cuyo grupo_ad coincide con alguno de los grupos resueltos desde Entra ID.
     */
    @Query("SELECT r FROM SiproRolesPermisos r WHERE LOWER(r.grupoAd) IN :grupos")
    List<SiproRolesPermisos> findByGrupoAdInIgnoreCase(@Param("grupos") Collection<String> grupos);
}
