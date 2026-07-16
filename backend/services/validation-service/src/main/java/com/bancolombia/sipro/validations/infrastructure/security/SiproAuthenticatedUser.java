package com.bancolombia.sipro.validations.infrastructure.security;

import java.util.Set;

/**
 * Identidad autenticada que el backend deja disponible en SecurityContext.
 */
public record SiproAuthenticatedUser(
        Long idUsuario,
        String usuario,
        String alias,
        String preferredUsername,
        String email,
        String objectId,
        Set<String> groupNames,
        boolean groupsOverage) {
}