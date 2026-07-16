package com.bancolombia.sipro.validations.infrastructure.entrypoint;

import com.bancolombia.sipro.validations.application.dto.LoginRequest;
import com.bancolombia.sipro.validations.application.dto.LoginResponse;
import com.bancolombia.sipro.validations.application.dto.UsuarioPermisosResponse;
import com.bancolombia.sipro.validations.application.usecase.LoginUseCase;
import com.bancolombia.sipro.validations.domain.service.ParametroUnicoService;
import com.bancolombia.sipro.validations.domain.service.RbacService;
import com.bancolombia.sipro.validations.infrastructure.security.MicrosoftGraphDirectoryService;
import com.bancolombia.sipro.validations.infrastructure.security.SiproAuthenticatedUser;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Expone los endpoints de autenticación y consulta de permisos del usuario.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final LoginUseCase loginUseCase;
    private final RbacService rbacService;
    private final ParametroUnicoService parametroUnicoService;
    private final MicrosoftGraphDirectoryService microsoftGraphDirectoryService;

    public AuthController(LoginUseCase loginUseCase,
                          RbacService rbacService,
                          ParametroUnicoService parametroUnicoService,
                          MicrosoftGraphDirectoryService microsoftGraphDirectoryService) {
        this.loginUseCase = loginUseCase;
        this.rbacService = rbacService;
        this.parametroUnicoService = parametroUnicoService;
        this.microsoftGraphDirectoryService = microsoftGraphDirectoryService;
    }

    /**
     * Endpoint de bootstrap de sesión con token de Entra ID.
        * @param request Token de identidad emitido por Entra ID y token delegado para Graph
     * @param bindingResult Resultado de la validación
     * @return ResponseEntity con el resultado de la autenticación
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               BindingResult bindingResult) {
        // Validar campos obligatorios
        if (bindingResult.hasErrors()) {
            String errores = bindingResult.getAllErrors()
                    .stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            
            logger.warn("Error de validación en login: {}", errores);
            return ResponseEntity
                    .badRequest()
                    .body(LoginResponse.failure(errores));
        }

        try {
            LoginResponse response = loginUseCase.authenticate(request);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            }

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Acceso denegado en login: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(LoginResponse.failure(e.getMessage()));
        } catch (RuntimeException e) {
            // Desempaquetar causa raíz por si Spring envolvió la excepción en UnexpectedRollbackException
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof IllegalArgumentException) {
                logger.warn("Acceso denegado (causa raíz): {}", cause.getMessage());
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(LoginResponse.failure(cause.getMessage()));
            }
            logger.error("Error interno durante el login: ", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(LoginResponse.failure("Error interno durante el proceso de autenticación."));
        }
    }

    /**
     * Exposición controlada de la configuración pública necesaria para iniciar login con Entra ID.
     * El secreto NUNCA se expone al frontend.
     */
    @GetMapping("/entra/config")
    public ResponseEntity<java.util.Map<String, Object>> getEntraConfig() {
        String clientId = parametroUnicoService.getString("AZURE_CLIENT_ID", "").trim();
        String tenantId = parametroUnicoService.getString("AZURE_TENANT_ID", "").trim();
        String apiScope = parametroUnicoService.getString("AZURE_API_SCOPE", "").trim();
        String apiAudience = parametroUnicoService.getString("AZURE_API_AUDIENCE", "").trim();
        boolean enabled = !clientId.isBlank() && !tenantId.isBlank();

        if (apiAudience.isBlank() && !clientId.isBlank()) {
            apiAudience = "api://" + clientId;
        }

        if (!enabled) {
            logger.warn("Configuración Entra incompleta en sipro_parametros_unico");
        }

        return ResponseEntity.ok(java.util.Map.of(
                "enabled", enabled,
                "clientId", clientId,
                "tenantId", tenantId,
                "apiScope", apiScope,
                "apiAudience", apiAudience));
    }

    /**
     * Endpoint para consultar los permisos RBAC de un usuario.
     * Permite al frontend refrescar permisos sin re-autenticar.
     * @param idUsuario ID del usuario
     * @return ResponseEntity con los permisos del usuario
     */
    @GetMapping("/permisos/{idUsuario}")
    public ResponseEntity<UsuarioPermisosResponse> getPermisos(@PathVariable Long idUsuario,
                                                               @RequestHeader(name = "X-Graph-Access-Token", required = false) String graphAccessToken,
                                                               Authentication authentication) {
        try {
            if (!(authentication != null && authentication.getPrincipal() instanceof SiproAuthenticatedUser principal)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            if (!principal.idUsuario().equals(idUsuario)) {
                logger.warn("Se ignoró idUsuario={} y se usó el usuario autenticado={} para refrescar permisos", idUsuario, principal.idUsuario());
            }

            logger.info("Consultando permisos para usuario autenticado: {}", principal.idUsuario());
            Set<String> gruposAd = principal.groupNames();
            if (graphAccessToken != null && !graphAccessToken.isBlank()) {
                try {
                    gruposAd = microsoftGraphDirectoryService.resolveCurrentUserGroupNames(
                            graphAccessToken,
                            principal.groupNames(),
                            principal.groupsOverage());
                } catch (Exception ex) {
                    logger.warn("No se pudieron refrescar grupos delegados para usuario {}. Se usarán claims del token. Motivo: {}",
                            principal.idUsuario(), ex.getMessage());
                }
            }

            UsuarioPermisosResponse permisos = rbacService.obtenerPermisosUsuario(
                    principal.idUsuario(),
                    gruposAd);
            return ResponseEntity.ok(permisos);
        } catch (Exception e) {
            logger.error("Error al consultar permisos del usuario {}: ", idUsuario, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint de prueba de salud
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auth service is running");
    }
}
