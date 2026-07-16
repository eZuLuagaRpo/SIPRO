package com.bancolombia.sipro.validations.shared.utils;

import java.util.Locale;

/**
 * Normaliza nombres de grupos funcionales SIPRO para comparar Azure Entra ID
 * contra la configuración almacenada en base de datos.
 */
public final class GroupNameNormalizer {

    private static final String SIPRO_GROUP_PREFIX = "a_sipro_";

    private GroupNameNormalizer() {
    }

    public static String normalizeFunctionalGroupName(String groupName) {
        if (groupName == null) {
            return null;
        }

        String normalized = groupName.trim()
                .toLowerCase(Locale.ROOT)
                .replace("{", "")
                .replace("}", "")
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("[^a-z0-9_]", "")
                .replaceAll("_+", "_");

        if (normalized.startsWith(SIPRO_GROUP_PREFIX)) {
            normalized = normalized.substring(SIPRO_GROUP_PREFIX.length());
        }

        return normalized.isBlank() ? null : normalized;
    }
}