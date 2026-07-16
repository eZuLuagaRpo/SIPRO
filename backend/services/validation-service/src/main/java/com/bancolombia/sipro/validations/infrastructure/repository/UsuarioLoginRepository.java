package com.bancolombia.sipro.validations.infrastructure.repository;

import com.bancolombia.sipro.validations.domain.model.UsuarioLogin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Consulta usuarios de autenticación y sus relaciones necesarias para login y permisos.
 */
@Repository
public interface UsuarioLoginRepository extends JpaRepository<UsuarioLogin, Long> {

    /**
     * Busca un usuario por su nombre de usuario
     * @param usuario Nombre de usuario
     * @return Optional con el usuario encontrado
     */
    Optional<UsuarioLogin> findByUsuario(String usuario);

    /**
     * Busca un usuario por nombre de usuario (case-insensitive)
     * @param usuario Nombre de usuario
     * @return Optional con el usuario encontrado
     */
    @Query("SELECT u FROM UsuarioLogin u WHERE LOWER(u.usuario) = LOWER(:usuario)")
    Optional<UsuarioLogin> findByUsuarioIgnoreCase(@Param("usuario") String usuario);

    /**
     * Obtiene todos los nombres de usuario (lowercase) que existen en la BD
     * para un conjunto dado de aliases. Permite validación batch eficiente.
     * @param aliases Conjunto de aliases a verificar (en lowercase)
     * @return Lista de aliases que SÍ existen
     */
    @Query("SELECT LOWER(u.usuario) FROM UsuarioLogin u WHERE LOWER(u.usuario) IN :aliases")
    List<String> findExistingAliases(@Param("aliases") Set<String> aliases);

    /**
     * Resuelve un usuario por alias corporativo, principal completo o correo.
     * Se usa después de validar la identidad contra Entra ID.
     */
    @Query("SELECT DISTINCT u FROM UsuarioLogin u " +
           "LEFT JOIN FETCH u.persona p " +
           "LEFT JOIN FETCH u.area a " +
           "LEFT JOIN FETCH a.jefe " +
           "WHERE (:alias IS NOT NULL AND (LOWER(u.usuario) = LOWER(:alias) OR LOWER(p.usuario) = LOWER(:alias))) " +
           "OR (:principal IS NOT NULL AND LOWER(u.usuario) = LOWER(:principal)) " +
           "OR (:correo IS NOT NULL AND LOWER(p.correo) = LOWER(:correo))")
    Optional<UsuarioLogin> findByAnyIdentifierIgnoreCase(@Param("alias") String alias,
                                                         @Param("principal") String principal,
                                                         @Param("correo") String correo);
}
