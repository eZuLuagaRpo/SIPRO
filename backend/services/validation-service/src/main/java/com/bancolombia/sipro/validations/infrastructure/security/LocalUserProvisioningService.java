package com.bancolombia.sipro.validations.infrastructure.security;

import com.bancolombia.sipro.validations.domain.model.UsuarioLogin;
import com.bancolombia.sipro.validations.infrastructure.repository.UsuarioLoginRepository;
import com.bancolombia.sipro.validations.infrastructure.security.EntraIdTokenService.EntraAuthenticatedUser;
import com.bancolombia.sipro.validations.infrastructure.security.MicrosoftGraphDirectoryService.DirectoryUserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Valida que el usuario autenticado por Entra ID exista previamente en SIPRO.
 * No crea ni modifica ningún registro; el alta de usuarios es responsabilidad
 * exclusiva del administrador a través de la aplicación.
 */
@Service
public class LocalUserProvisioningService {

    private static final Logger logger = LoggerFactory.getLogger(LocalUserProvisioningService.class);

    private final UsuarioLoginRepository usuarioLoginRepository;

    public LocalUserProvisioningService(UsuarioLoginRepository usuarioLoginRepository) {
        this.usuarioLoginRepository = usuarioLoginRepository;
    }

    /**
     * Busca el usuario registrado en SIPRO a partir de la identidad de Entra ID.
     * Lanza {@link IllegalArgumentException} si el correo/alias no corresponde a ningún
     * usuario registrado. El alta debe realizarse manualmente desde la aplicación.
     */
    @Transactional(readOnly = true, noRollbackFor = IllegalArgumentException.class)
    public UsuarioLogin resolveOrProvision(EntraAuthenticatedUser entraUser, DirectoryUserContext directoryUser) {
        String correo   = normalize(firstNonBlank(directoryUser.email(), entraUser.email()));
        String alias    = normalize(resolveAlias(entraUser.alias(), entraUser.preferredUsername(), entraUser.email()));
        String principal = normalize(entraUser.preferredUsername());

        logger.debug("Buscando usuario SIPRO — alias={}, correo={}", alias, correo);

        return usuarioLoginRepository.findByAnyIdentifierIgnoreCase(alias, principal, correo)
                .orElseThrow(() -> {
                    String id = correo != null ? correo : alias;
                    logger.warn("Acceso denegado: '{}' no está registrado en SIPRO", id);
                    return new IllegalArgumentException(
                            "Tu cuenta de Microsoft fue verificada correctamente, pero no tienes un usuario activo en SIPRO. "
                            + "Solicita al administrador del sistema que cree tu usuario ("
                            + id + ").");
                });
    }

    /**
     * Comprueba si existe un usuario para el token de Entra ID proporcionado.
     * Usado por el filtro de autorización para endpoints protegidos.
     */
    @Transactional(readOnly = true)
    public Optional<UsuarioLogin> findExistingUser(EntraAuthenticatedUser entraUser) {
        return lookupUser(entraUser.alias(), entraUser.preferredUsername(), entraUser.email());
    }

    // ────────────────────────────────────────────────────────
    // Helpers privados de búsqueda
    // ────────────────────────────────────────────────────────

    private Optional<UsuarioLogin> lookupUser(String alias, String principal, String correo) {
        return usuarioLoginRepository.findByAnyIdentifierIgnoreCase(
                normalize(resolveAlias(alias, principal, correo)),
                normalize(principal),
                normalize(correo));
    }

    private String resolveAlias(String alias, String principal, String correo) {
        String candidate = firstNonBlank(alias, principal, correo);
        if (candidate == null) return null;
        String normalized = candidate.trim().toLowerCase(Locale.ROOT);
        int atIndex = normalized.indexOf('@');
        return atIndex > 0 ? normalized.substring(0, atIndex) : normalized;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }
}