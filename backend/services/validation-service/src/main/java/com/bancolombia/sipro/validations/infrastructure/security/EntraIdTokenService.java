package com.bancolombia.sipro.validations.infrastructure.security;

import com.bancolombia.sipro.validations.domain.service.ParametroUnicoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Valida tokens ID emitidos por Entra ID usando la configuración almacenada en sipro_parametros_unico.
 */
@Service
public class EntraIdTokenService {

    private static final Logger logger = LoggerFactory.getLogger(EntraIdTokenService.class);

    private static final String TENANT_ID_KEY = "AZURE_TENANT_ID";
    private static final String CLIENT_ID_KEY = "AZURE_CLIENT_ID";
    private static final String API_AUDIENCE_KEY = "AZURE_API_AUDIENCE";
    private static final String ISSUER_TEMPLATE = "https://login.microsoftonline.com/%s/v2.0";
    private static final String LEGACY_ISSUER_TEMPLATE = "https://sts.windows.net/%s/";
    private static final String JWK_SET_URI_TEMPLATE = "https://login.microsoftonline.com/%s/discovery/v2.0/keys";

    private final ParametroUnicoService parametroUnicoService;

    private volatile JwtDecoder jwtDecoder;
    private volatile String cachedTenantId;
    private volatile String cachedClientId;

    public EntraIdTokenService(ParametroUnicoService parametroUnicoService) {
        this.parametroUnicoService = parametroUnicoService;
    }

    public EntraAuthenticatedUser authenticate(String token) {
        String tenantId = configuredValue(TENANT_ID_KEY);
        String clientId = configuredValue(CLIENT_ID_KEY);
        Set<String> acceptedIssuers = resolveAcceptedIssuers(tenantId);
        Set<String> acceptedAudiences = resolveAcceptedAudiences(clientId);

        Jwt jwt;
        try {
            jwt = getDecoder(tenantId, acceptedIssuers, acceptedAudiences).decode(token);
        } catch (JwtException ex) {
            logTokenDiagnostics(token, acceptedAudiences, ex);
            throw new IllegalArgumentException("No fue posible validar el token de Entra ID", ex);
        }

        String preferredUsername = firstNonBlank(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("upn"),
                jwt.getClaimAsString("email"));
        String email = firstNonBlank(jwt.getClaimAsString("email"), preferredUsername);
        String displayName = firstNonBlank(jwt.getClaimAsString("name"), preferredUsername);
        String objectId = jwt.getClaimAsString("oid");
        Set<String> groupNames = extractGroupNames(jwt);
        boolean groupsOverage = hasGroupsOverage(jwt);

        if (preferredUsername == null) {
            throw new IllegalArgumentException("El token de Entra ID no contiene un identificador de usuario usable");
        }

        return new EntraAuthenticatedUser(
                normalizeAlias(preferredUsername),
                preferredUsername.trim().toLowerCase(),
                email == null ? null : email.trim().toLowerCase(),
                displayName,
                objectId,
                Set.copyOf(groupNames),
                groupsOverage);
    }

    private JwtDecoder getDecoder(String tenantId,
                                  Set<String> acceptedIssuers,
                                  Set<String> acceptedAudiences) {
        String decoderCacheKey = String.join("|", acceptedIssuers) + "::" + String.join("|", acceptedAudiences);
        if (jwtDecoder != null && tenantId.equals(cachedTenantId) && decoderCacheKey.equals(cachedClientId)) {
            return jwtDecoder;
        }

        synchronized (this) {
            if (jwtDecoder != null && tenantId.equals(cachedTenantId) && decoderCacheKey.equals(cachedClientId)) {
                return jwtDecoder;
            }

            String jwkSetUri = String.format(JWK_SET_URI_TEMPLATE, tenantId);
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

            OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefault();
            OAuth2TokenValidator<Jwt> issuerValidator = jwt -> {
                String tokenIssuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
                if (tokenIssuer != null && acceptedIssuers.contains(tokenIssuer)) {
                    return OAuth2TokenValidatorResult.success();
                }
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        "El token recibido no corresponde al issuer configurado para SIPRO",
                        null));
            };
            OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
                List<String> audience = jwt.getAudience();
                if (audience != null && audience.stream().anyMatch(acceptedAudiences::contains)) {
                    return OAuth2TokenValidatorResult.success();
                }
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        "El token recibido no corresponde a la audiencia configurada para SIPRO",
                        null));
            };

            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidator, issuerValidator, audienceValidator));

            jwtDecoder = decoder;
            cachedTenantId = tenantId;
            cachedClientId = decoderCacheKey;
            logger.info("Validador JWT de Entra ID inicializado para tenant {}, issuers {} y audiencias {}",
                    tenantId, acceptedIssuers, acceptedAudiences);
            return jwtDecoder;
        }
    }

    private String configuredValue(String key) {
        String value = parametroUnicoService.getString(key, "").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Falta configurar " + key + " en sipro_parametros_unico");
        }
        return value;
    }

    private String normalizeAlias(String login) {
        String normalized = login.trim().toLowerCase();
        int atIndex = normalized.indexOf('@');
        if (atIndex > 0) {
            return normalized.substring(0, atIndex);
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Set<String> extractGroupNames(Jwt jwt) {
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        List<String> rawGroups = jwt.getClaimAsStringList("groups");
        if (rawGroups == null) {
            return groups;
        }

        for (String rawGroup : rawGroups) {
            if (rawGroup == null || rawGroup.isBlank()) {
                continue;
            }
            groups.add(rawGroup.trim().toLowerCase(Locale.ROOT));
        }
        return groups;
    }

    private boolean hasGroupsOverage(Jwt jwt) {
        Object hasGroups = jwt.getClaims().get("hasgroups");
        if (Boolean.TRUE.equals(hasGroups) || "true".equalsIgnoreCase(String.valueOf(hasGroups))) {
            return true;
        }

        Object claimNames = jwt.getClaims().get("_claim_names");
        if (claimNames instanceof Map<?, ?> map) {
            return map.containsKey("groups");
        }

        return false;
    }

    private Set<String> resolveAcceptedAudiences(String clientId) {
        LinkedHashSet<String> audiences = new LinkedHashSet<>();
        audiences.add(clientId);
        audiences.add("api://" + clientId);

        String configuredAudience = parametroUnicoService.getString(API_AUDIENCE_KEY, "").trim();
        if (!configuredAudience.isBlank()) {
            for (String rawAudience : configuredAudience.split(",")) {
                if (rawAudience != null && !rawAudience.isBlank()) {
                    audiences.add(rawAudience.trim());
                }
            }
        }

        return Set.copyOf(audiences);
    }

    private Set<String> resolveAcceptedIssuers(String tenantId) {
        LinkedHashSet<String> issuers = new LinkedHashSet<>();
        issuers.add(String.format(ISSUER_TEMPLATE, tenantId));
        issuers.add(String.format(LEGACY_ISSUER_TEMPLATE, tenantId));
        return Set.copyOf(issuers);
    }

    private void logTokenDiagnostics(String token, Set<String> acceptedAudiences, JwtException ex) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                logger.warn("Fallo validación JWT. El valor recibido no tiene formato JWT. audienciasEsperadas={} error={}",
                        acceptedAudiences, ex.getMessage());
                return;
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            logger.warn("Fallo validación JWT. audienciasEsperadas={} payloadNoVerificado={} error={}",
                    acceptedAudiences, payloadJson, ex.getMessage());
        } catch (Exception diagnosticEx) {
            logger.warn("Fallo validación JWT. audienciasEsperadas={} error={} (sin diagnóstico extra: {})",
                    acceptedAudiences, ex.getMessage(), diagnosticEx.getMessage());
        }
    }

    public record EntraAuthenticatedUser(
            String alias,
            String preferredUsername,
            String email,
            String displayName,
            String objectId,
            Set<String> groupNames,
            boolean groupsOverage) {
    }
}