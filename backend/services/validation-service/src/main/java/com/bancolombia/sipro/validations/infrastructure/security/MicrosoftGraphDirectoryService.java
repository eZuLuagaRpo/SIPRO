package com.bancolombia.sipro.validations.infrastructure.security;

import com.bancolombia.sipro.validations.domain.service.ParametroUnicoService;
import com.bancolombia.sipro.validations.infrastructure.security.EntraIdTokenService.EntraAuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final String SIPRO_GROUP_PREFIX = "a_sipro_";

    // Claves para token de aplicación (client_credentials)
    private static final String TENANT_ID_KEY = "AZURE_TENANT_ID";
    private static final String CLIENT_ID_KEY = "AZURE_CLIENT_ID";
    private static final String CLIENT_SECRET_KEY = "AZURE_CLIENT_SECRET";
    private static final String TOKEN_URL_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";

    private final ParametroUnicoService parametroUnicoService;

    // Cache simple del token de aplicación
    private volatile String cachedAppToken;
    private volatile long appTokenExpiry = 0;

    public MicrosoftGraphDirectoryService(ParametroUnicoService parametroUnicoService) {
        this.parametroUnicoService = parametroUnicoService;
    }

    public DirectoryUserContext resolveCurrentUserContext(EntraAuthenticatedUser entraUser, String graphAccessToken) {
        HttpHeaders headers = buildHeaders(graphAccessToken);
        Map<String, Object> profile = getMap(headers,
                GRAPH_BASE_URL + "/me?$select=id,displayName,mail,userPrincipalName,department,companyName,jobTitle,employeeId,officeLocation");

        Map<String, Object> manager = getOptionalMap(headers,
                GRAPH_BASE_URL + "/me/manager?$select=id,displayName,mail,userPrincipalName,jobTitle");

        Set<String> groups = resolveCurrentUserGroupNames(graphAccessToken, entraUser.groupNames(), entraUser.groupsOverage());
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
                Set.copyOf(groups));
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

    // ── Consulta de grupos de usuario arbitrario via token de aplicación ────

    /**
     * Resuelve los nombres de grupos de un usuario específico (no el autenticado) usando
     * un token de aplicación obtenido via client_credentials.
     * <p>
     * Requiere que {@code AZURE_TENANT_ID}, {@code AZURE_CLIENT_ID} y
     * {@code AZURE_CLIENT_SECRET} estén configurados en {@code sipro_parametros_unico}.
     * El permiso de Graph necesario es {@code GroupMember.Read.All} o {@code Directory.Read.All}.
     * <p>
     * Si las credenciales no están configuradas o Graph falla, retorna conjunto vacío
     * (degradación elegante — el rol quedará como «Sin rol»).
     *
     * @param userUpn UPN o correo del usuario (ej: juan.ortiz@bancolombia.com.co)
     * @return conjunto de nombres de grupos en minúsculas
     */
    public Set<String> resolveUserGroupsByUpn(String userUpn) {
        if (userUpn == null || userUpn.isBlank()) {
            return Set.of();
        }
        String appToken = getAppAccessToken();
        if (appToken == null) {
            logger.warn("No hay token de aplicación disponible para consultar grupos de '{}'", userUpn);
            return Set.of();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(appToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Set<String> groups = new HashSet<>();
        String nextUrl = GRAPH_BASE_URL + "/users/" + userUpn
                + "/memberOf/microsoft.graph.group?$select=displayName,id,securityEnabled&$top=999";

        try {
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
            logger.info("Grupos resueltos para '{}' via app token: {}", userUpn, groups.size());
        } catch (Exception ex) {
            logger.warn("No fue posible resolver grupos para '{}' desde Graph: {}", userUpn, ex.getMessage());
        }
        return Set.copyOf(groups);
    }

    /**
     * Obtiene un token de acceso de aplicación vía client_credentials, con caché simple en memoria.
     * Retorna {@code null} si las credenciales no están configuradas.
     */
    private synchronized String getAppAccessToken() {
        if (cachedAppToken != null && System.currentTimeMillis() < appTokenExpiry) {
            return cachedAppToken;
        }

        String tenantId = parametroUnicoService.getString(TENANT_ID_KEY, "").trim();
        String clientId = parametroUnicoService.getString(CLIENT_ID_KEY, "").trim();
        String clientSecret = parametroUnicoService.getString(CLIENT_SECRET_KEY, "").trim();

        if (tenantId.isEmpty() || clientId.isEmpty() || clientSecret.isEmpty()) {
            logger.debug("Credenciales de app Graph no configuradas (AZURE_TENANT_ID / CLIENT_ID / CLIENT_SECRET). " +
                    "La consulta de roles Azure en tiempo real no estará disponible.");
            return null;
        }

        try {
            String tokenUrl = String.format(TOKEN_URL_TEMPLATE, tenantId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String body = "client_id=" + clientId
                    + "&scope=https://graph.microsoft.com/.default"
                    + "&client_secret=" + clientSecret
                    + "&grant_type=client_credentials";

                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cachedAppToken = (String) response.getBody().get("access_token");
                Integer expiresIn = (Integer) response.getBody().get("expires_in");
                appTokenExpiry = System.currentTimeMillis() + ((expiresIn - 60) * 1000L);
                logger.debug("Token de app Graph obtenido (expira en {} seg)", expiresIn);
                return cachedAppToken;
            }
        } catch (Exception ex) {
            logger.error("Error obteniendo token de app Graph: {}", ex.getMessage());
        }
        return null;
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