package com.bancolombia.sipro.validations.infrastructure.adapters.out;

import com.bancolombia.sipro.validations.domain.service.UsuarioDirectoryValidator;
import com.bancolombia.sipro.validations.infrastructure.repository.UsuarioLoginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementación LOCAL de {@link UsuarioDirectoryValidator} que valida usuarios
 * contra la tabla {@code usuario_login} en PostgreSQL.
 * <p>
 * Activa para perfiles dev y qa. En producción será reemplazada por
 * {@link MicrosoftGraphUsuarioValidator}.
 */
@Component
@Profile({"dev", "qa", "default"})
public class LocalDbUsuarioValidator implements UsuarioDirectoryValidator {

    private static final Logger logger = LoggerFactory.getLogger(LocalDbUsuarioValidator.class);

    private final UsuarioLoginRepository usuarioLoginRepository;

    public LocalDbUsuarioValidator(UsuarioLoginRepository usuarioLoginRepository) {
        this.usuarioLoginRepository = usuarioLoginRepository;
    }

    /**
     * Busca de forma masiva qué aliases existen en la base local de usuarios.
     */
    @Override
    public Set<String> findExistingUsers(Set<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return Set.of();
        }

        // Normalizar a lowercase
        Set<String> normalizedAliases = aliases.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        logger.debug("Validando {} usuarios únicos contra tabla usuario_login", normalizedAliases.size());

        List<String> existingList = usuarioLoginRepository.findExistingAliases(normalizedAliases);
        Set<String> existing = new HashSet<>(existingList);

        logger.info("Validación USUARIO (local DB): {}/{} usuarios encontrados",
                existing.size(), normalizedAliases.size());

        return existing;
    }

    /**
     * Verifica si un alias puntual existe en la tabla local de login.
     */
    @Override
    public boolean userExists(String alias) {
        if (alias == null || alias.trim().isEmpty()) {
            return false;
        }
        return usuarioLoginRepository.findByUsuarioIgnoreCase(alias.trim()).isPresent();
    }
}
