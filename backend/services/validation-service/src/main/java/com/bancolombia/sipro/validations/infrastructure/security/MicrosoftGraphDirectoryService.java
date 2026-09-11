package com.bancolombia.sipro.validations.infrastructure.security;

import com.bancolombia.sipro.validations.infrastructure.security.EntraIdTokenService.EntraAuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cliente mínimo de Microsoft Graph para leer perfil, manager y grupos del usuario autenticado.
 * También soporta consulta de grupos de usuarios arbitrarios via token de aplicación (client credentials).
 */
@Service
public class MicrosoftGraphDirectoryService {

    private static final Logger logger = LoggerFactory.getLogger(MicrosoftGraphDirectoryService.class);

    private static final String GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0";
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String SIPRO_GROUP_PREFIX = "a_euc_sipro_";

    public MicrosoftGraphDirectoryService() {
    }

    public DirectoryUserContext resolveCurrentUserContext(EntraAuthenticatedUser entraUser, String graphAccessToken) {
        HttpHeaders headers = buildHeaders(graphAccessToken);
        Map<String, Object> profile = getMap(headers,
                GRAPH_BASE_URL + "/me?$select=id,displayName,mail,userPrincipalName,department,companyName,jobTitle,employeeId,officeLocation");

        Map<String, Object> manager = getOptionalMap(headers,
                GRAPH_BASE_URL + "/me/manager?$select=id,displayName,mail,userPrincipalName,jobTitle");

        String displayName = firstNonBlank(asString(profile.get("displayName")), entraUser.displayName());
        String email = firstNonBlank(asString(profile.get("mail")), asString(profile.get("userPrincipalName")), entraUser.email());
        String managerName = manager == null ? null : asString(manager.get("displayName"));
        String managerEmail = manager == null ? null : firstNonBlank(asString(manager.get("mail")), asString(manager.get("userPrincipalName")));
        String managerPrincipal = manager == null ? null : asString(manager.get("userPrincipalName"));
        String managerJobTitle = manager == null ? null : asString(manager.get("jobTitle"));

        return new DirectoryUserContext(
                firstNonBlank(asString(profile.get("id")), entraUser.objectId()),
                displayName,
                email,
                asString(profile.get("department")),
                asString(profile.get("companyName")),
                asString(profile.get("jobTitle")),
                asString(profile.get("employeeId")),
                asString(profile.get("officeLocation")),
                managerName,
                managerEmail,
                managerPrincipal,
                managerJobTitle,
                Set.of());
    }

    public DirectoryUserContext resolveUserContext(EntraAuthenticatedUser entraUser, String graphAccessToken) {
        return resolveCurrentUserContext(entraUser, graphAccessToken);
    }

    public Set<String> resolveCurrentUserGroupNames(String graphAccessToken,
                                                    Set<String> tokenGroupNames,
                                                    boolean groupsOverage) {
        LinkedHashSet<String> normalizedTokenGroups = normalizeGroupNames(tokenGroupNames);
        boolean shouldQueryGraph = groupsOverage || !containsFunctionalGroupNames(normalizedTokenGroups);
        if (!shouldQueryGraph) {
            return Set.copyOf(normalizedTokenGroups);
        }

        HttpHeaders headers = buildHeaders(graphAccessToken);
        Set<String> graphGroups = getGroups(headers);
        if (graphGroups.isEmpty()) {
            return Set.copyOf(normalizedTokenGroups);
        }

        normalizedTokenGroups.addAll(graphGroups);
        return Set.copyOf(normalizedTokenGroups);
    }

    @Cacheable(value = "gruposGraph", key = "#userObjectId")
    public Set<String> resolveCurrentUserGroupNamesCached(String userObjectId,
                                                          String graphAccessToken,
                                                          Set<String> tokenGroupNames,
                                                          boolean groupsOverage) {
        return resolveCurrentUserGroupNames(graphAccessToken, tokenGroupNames, groupsOverage);
    }

    private Set<String> getGroups(HttpHeaders headers) {
        Set<String> groups = new HashSet<>();
        String nextUrl = GRAPH_BASE_URL + "/me/memberOf/microsoft.graph.group?$select=displayName,id,securityEnabled&$top=999";

        while (nextUrl != null && !nextUrl.isBlank()) {
            Map<String, Object> response = getMap(headers, nextUrl);
            List<Map<String, Object>> values = safeList(response.get("value"));
            for (Map<String, Object> group : values) {
                Object securityEnabled = group.get("securityEnabled");
                if (securityEnabled instanceof Boolean enabled && !enabled) {
                    continue;
                }
                String displayName = asString(group.get("displayName"));
                if (displayName != null && !displayName.isBlank()) {
                    groups.add(displayName.trim().toLowerCase(Locale.ROOT));
                }
            }
            nextUrl = asString(response.get("@odata.nextLink"));
        }

        logger.info("Grupos delegados de Entra resueltos para el usuario autenticado: {}", groups);
        return groups;
    }

    private Map<String, Object> getMap(HttpHeaders headers, String url) {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<String, Object>>() {
                });
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Respuesta no válida de Microsoft Graph para " + url);
        }
        return response.getBody();
    }

    private Map<String, Object> getOptionalMap(HttpHeaders headers, String url) {
        try {
            return getMap(headers, url);
        } catch (Exception ex) {
            logger.warn("No fue posible resolver manager en Graph: {}", ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return List.of();
    }

    private HttpHeaders buildHeaders(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("No se recibió token delegado de Microsoft Graph para el usuario autenticado");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken.trim());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private LinkedHashSet<String> normalizeGroupNames(Set<String> groups) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (groups == null) {
            return normalized;
        }

        for (String group : groups) {
            if (group == null || group.isBlank()) {
                continue;
            }
            normalized.add(group.trim().toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private boolean containsFunctionalGroupNames(Set<String> groups) {
        return groups.stream().anyMatch(this::isFunctionalGroupName);
    }

    private boolean isFunctionalGroupName(String groupName) {
        return groupName != null && groupName.startsWith(SIPRO_GROUP_PREFIX);
    }

    public record DirectoryUserContext(
            String id,
            String displayName,
            String email,
            String department,
            String companyName,
            String jobTitle,
            String employeeId,
            String officeLocation,
            String managerDisplayName,
            String managerEmail,
            String managerUserPrincipalName,
            String managerJobTitle,
            Set<String> groupNames) {
    }
}