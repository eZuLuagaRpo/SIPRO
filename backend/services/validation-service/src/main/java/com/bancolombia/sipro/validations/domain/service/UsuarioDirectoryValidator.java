package com.bancolombia.sipro.validations.domain.service;

import java.util.Set;

/**
 * Interfaz para validar la existencia de usuarios en el directorio corporativo.
 * <p>
 * Contrato agnóstico de implementación que permite intercambiar entre:
 * <ul>
 *   <li><b>DEV/QA:</b> Validación contra tabla local PostgreSQL (usuario_login)</li>
 *   <li><b>PRD:</b> Validación contra Microsoft Graph API (Azure AD)</li>
 * </ul>
 */
public interface UsuarioDirectoryValidator {

    /**
     * Dado un conjunto de aliases (usernames), retorna el subconjunto que SÍ existen
     * en el directorio corporativo.
     *
     * @param aliases Conjunto de aliases en lowercase a verificar
     * @return Conjunto de aliases que existen (en lowercase)
     */
    Set<String> findExistingUsers(Set<String> aliases);

    /**
     * Verifica si un único usuario existe en el directorio.
     *
     * @param alias Username a verificar (case-insensitive)
     * @return true si el usuario existe
     */
    boolean userExists(String alias);
}
