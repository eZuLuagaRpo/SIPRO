package com.bancolombia.sipro.validations.infrastructure.adapters.out;

import com.bancolombia.sipro.validations.domain.service.ParametroUnicoService;
import com.bancolombia.sipro.validations.domain.service.UsuarioDirectoryValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementación de {@link UsuarioDirectoryValidator} que valida usuarios contra
 * Microsoft Graph API (Azure Active Directory / Entra ID).
 * <p>
 * Activa SOLO para perfil {@code prd}.
 * <p>
 * API utilizada:
 * <pre>
 *   GET https://graph.microsoft.com/v1.0/users?$filter=startsWith(userPrincipalName,'{alias}')
 * </pre>
 * <p>
 * <b>Configuración requerida:</b> Las siguientes propiedades deben existir en
 * {@code application-prd.yml}:
 * <ul>
 *   <li>{@code app.graph.tenant-id} — Azure AD Tenant ID</li>
 *   <li>{@code app.graph.client-id} — App Registration Client ID</li>
 *   <li>{@code app.graph.client-secret} — App Registration Client Secret</li>
 * </ul>
 *
 * <b>Permisos requeridos en Azure AD:</b>
 * <ul>
 *   <li>Application permission: {@code User.Read.All}</li>
 * </ul>
 */
@Component
@Profile("prd")
public class MicrosoftGraphUsuarioValidator implements UsuarioDirectoryValidator {

    private static final Logger logger = LoggerFactory.getLogger(MicrosoftGraphUsuarioValidator.class);

    private static final String GRAPH_USERS_URL = "https://graph.microsoft.com/v1.0/users";
    private static final String TOKEN_URL_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String TENANT_ID_KEY = "AZURE_TENANT_ID";
    private static final String CLIENT_ID_KEY = "AZURE_CLIENT_ID";
    private static final String CLIENT_SECRET_KEY = "AZURE_CLIENT_SECRET";

    private final RestTemplate restTemplate;
    private final ParametroUnicoService parametroUnicoService;

    // Token cache simple (en producción real considerar usar azure-identity SDK)
    private String cachedToken;
    private long tokenExpiry = 0;

    public MicrosoftGraphUsuarioValidator(ParametroUnicoService parametroUnicoService) {
        this.restTemplate = new RestTemplate();
        this.parametroUnicoService = parametroUnicoService;
    }

    /**
     * Consulta en lote qué aliases existen y están activos en Microsoft Graph.
     */
    @Override
    public Set<String> findExistingUsers(Set<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return Set.of();
        }

        Set<String> normalizedAliases = aliases.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Set<String> existingUsers = new HashSet<>();

        String token = getAccessToken();
        if (token == null) {
            logger.error("No se pudo obtener token de Microsoft Graph. " +
                    "Verifique app.graph.tenant-id, client-id y client-secret en application-prd.yml");
            return existingUsers;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        for (String alias : normalizedAliases) {
            try {
                String url = GRAPH_USERS_URL +
                        "?$filter=startsWith(userPrincipalName,'" + alias + "')" +
                        "&$select=userPrincipalName,accountEnabled" +
                        "&$top=1";

                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        new ParameterizedTypeReference<Map<String, Object>>() {
                        });

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    List<Map<String, Object>> value = safeList(response.getBody().get("value"));
                    if (value != null && !value.isEmpty()) {
                        // Verificar que el usuario está activo
                        Map<String, Object> user = value.get(0);
                        Boolean accountEnabled = (Boolean) user.get("accountEnabled");
                        String upn = (String) user.get("userPrincipalName");

                        if (Boolean.TRUE.equals(accountEnabled) && upn != null
                                && upn.toLowerCase().startsWith(alias)) {
                            existingUsers.add(alias);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Error consultando Graph API para usuario '{}': {}", alias, e.getMessage());
                // Continuar con el siguiente usuario
            }
        }

        logger.info("Validación USUARIO (Graph API): {}/{} usuarios encontrados en directorio activo",
                existingUsers.size(), normalizedAliases.size());

        return existingUsers;
    }

    /**
     * Verifica si un alias puntual existe en el directorio corporativo.
     */
    @Override
    public boolean userExists(String alias) {
        if (alias == null || alias.trim().isEmpty()) {
            return false;
        }
        Set<String> result = findExistingUsers(Set.of(alias.trim().toLowerCase()));
        return !result.isEmpty();
    }

    /**
     * Obtiene un access token de Azure AD usando Client Credentials flow.
     * Incluye cache básico para evitar solicitar un token por cada llamada.
     */
    private String getAccessToken() {
        // Verificar cache
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) {
            return cachedToken;
        }

        String tenantId = parametroUnicoService.getString(TENANT_ID_KEY, "").trim();
        String clientId = parametroUnicoService.getString(CLIENT_ID_KEY, "").trim();
        String clientSecret = parametroUnicoService.getString(CLIENT_SECRET_KEY, "").trim();

        if (tenantId == null || tenantId.isEmpty() ||
                clientId == null || clientId.isEmpty() ||
                clientSecret == null || clientSecret.isEmpty()) {
            logger.error("Credenciales de Microsoft Graph no configuradas. " +
                    "Configure AZURE_TENANT_ID, AZURE_CLIENT_ID y AZURE_CLIENT_SECRET en sipro_parametros_unico");
            return null;
        }

        try {
            String tokenUrl = String.format(TOKEN_URL_TEMPLATE, tenantId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String body = "client_id=" + clientId +
                    "&scope=https://graph.microsoft.com/.default" +
                    "&client_secret=" + clientSecret +
                    "&grant_type=client_credentials";

                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cachedToken = (String) response.getBody().get("access_token");
                Integer expiresIn = (Integer) response.getBody().get("expires_in");
                // Expirar 60 segundos antes para evitar race conditions
                tokenExpiry = System.currentTimeMillis() + ((expiresIn - 60) * 1000L);
                logger.debug("Token de Microsoft Graph obtenido (expira en {} seg)", expiresIn);
                return cachedToken;
            }
        } catch (Exception e) {
            logger.error("Error obteniendo token de Microsoft Graph: {}", e.getMessage());
        }

        return null;
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
}
