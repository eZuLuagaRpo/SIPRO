package com.bancolombia.sipro.validations.application.usecase;

import com.bancolombia.sipro.validations.application.dto.LoginRequest;
import com.bancolombia.sipro.validations.application.dto.LoginResponse;
import com.bancolombia.sipro.validations.application.dto.UsuarioPermisosResponse;
import com.bancolombia.sipro.validations.domain.model.UsuarioArea;
import com.bancolombia.sipro.validations.domain.model.UsuarioLogin;
import com.bancolombia.sipro.validations.domain.model.UsuarioPersona;
import com.bancolombia.sipro.validations.domain.service.ParametroUnicoService;
import com.bancolombia.sipro.validations.domain.service.RbacService;
import com.bancolombia.sipro.validations.infrastructure.security.EntraIdTokenService;
import com.bancolombia.sipro.validations.infrastructure.security.EntraIdTokenService.EntraAuthenticatedUser;
import com.bancolombia.sipro.validations.infrastructure.security.LocalUserProvisioningService;
import com.bancolombia.sipro.validations.infrastructure.security.MicrosoftGraphDirectoryService;
import com.bancolombia.sipro.validations.infrastructure.security.MicrosoftGraphDirectoryService.DirectoryUserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve la autenticación del usuario y arma la respuesta con permisos vigentes.
 */
@Service
public class LoginUseCase {

    private static final Logger logger = LoggerFactory.getLogger(LoginUseCase.class);
    private static final int DEFAULT_TIEMPO_EXPI_SESION = 5;

    private final RbacService rbacService;
    private final ParametroUnicoService parametroUnicoService;
    private final EntraIdTokenService entraIdTokenService;
    private final MicrosoftGraphDirectoryService microsoftGraphDirectoryService;
    private final LocalUserProvisioningService localUserProvisioningService;

    public LoginUseCase(RbacService rbacService,
                        ParametroUnicoService parametroUnicoService,
                        EntraIdTokenService entraIdTokenService,
                        MicrosoftGraphDirectoryService microsoftGraphDirectoryService,
                        LocalUserProvisioningService localUserProvisioningService) {
        this.rbacService = rbacService;
        this.parametroUnicoService = parametroUnicoService;
        this.entraIdTokenService = entraIdTokenService;
        this.microsoftGraphDirectoryService = microsoftGraphDirectoryService;
        this.localUserProvisioningService = localUserProvisioningService;
    }

    /**
     * Valida el token de acceso emitido para SIPRO, resuelve el usuario local y carga sus permisos RBAC.
     * @param request Token de acceso de SIPRO, token de identidad opcional y token delegado de Microsoft Graph
     * @return LoginResponse con los datos del usuario, permisos y roles
     */
        @Transactional(noRollbackFor = IllegalArgumentException.class)
    public LoginResponse authenticate(LoginRequest request) {
        try {
            EntraAuthenticatedUser entraUser = entraIdTokenService.authenticate(request.getApiAccessToken());
            logger.info("Token de acceso Entra validado para usuario: {}", entraUser.preferredUsername());

            DirectoryUserContext directoryUser = microsoftGraphDirectoryService.resolveCurrentUserContext(
                    entraUser, request.getGraphAccessToken());

            UsuarioLogin usuario = localUserProvisioningService.resolveOrProvision(entraUser, directoryUser);
            UsuarioPersona persona = usuario.getPersona();
            UsuarioArea area = usuario.getArea();

            String correo = firstNonBlank(
                persona != null ? persona.getCorreo() : null,
                directoryUser.email(),
                entraUser.email());
            String areaNombre = firstNonBlank(
                area != null ? area.getAreaNombre() : null,
                directoryUser.department());
            String jefeNombre = firstNonBlank(
                area != null ? area.getJefeNombreCompleto() : null,
                directoryUser.managerDisplayName());
            String nombres = firstNonBlank(
                persona != null ? persona.getNombres() : null,
                safeFirstName(directoryUser.displayName()),
                safeFirstName(entraUser.displayName()));
            String apellidos = firstNonBlank(
                persona != null ? persona.getApellidos() : null,
                safeLastName(directoryUser.displayName()),
                safeLastName(entraUser.displayName()));

            // Construir respuesta exitosa
            LoginResponse response = LoginResponse.success(
                    usuario.getIdUsuario(),
                    usuario.getUsuario(),
                nombres,
                apellidos,
                    correo,
                areaNombre,
                jefeNombre
            );
            response.setSessionTimeoutMinutes(
                    parametroUnicoService.getInt("TIEMPO_EXPI_SESION", DEFAULT_TIEMPO_EXPI_SESION));

            // Cargar permisos RBAC del usuario
            try {
            UsuarioPermisosResponse permisos = rbacService.obtenerPermisosUsuario(
                usuario.getIdUsuario(),
                directoryUser.groupNames());
                response.setPermisos(permisos);
                logger.info("Permisos RBAC cargados para usuario: {} (cargar={}, aprobar={})",
                        usuario.getUsuario(), permisos.isPuedeCargar(), permisos.isPuedeAprobar());
            } catch (Exception e) {
                logger.warn("No se pudieron cargar permisos RBAC para usuario: {}. " +
                        "El usuario podrá autenticarse pero sin permisos específicos.", usuario.getUsuario(), e);
                // No bloquear el login por falta de permisos RBAC
                response.setPermisos(new UsuarioPermisosResponse());
            }

            logger.info("Autenticación Entra exitosa para usuario: {}", usuario.getUsuario());
            return response;

        } catch (IllegalArgumentException e) {
            logger.warn("Token Entra inválido: {}", e.getMessage());
            return LoginResponse.failure(e.getMessage());
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String safeFirstName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        String trimmed = displayName.trim();
        int firstSpace = trimmed.indexOf(' ');
        return firstSpace < 0 ? trimmed : trimmed.substring(0, firstSpace);
    }

    private String safeLastName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        String trimmed = displayName.trim();
        int firstSpace = trimmed.indexOf(' ');
        if (firstSpace < 0 || firstSpace == trimmed.length() - 1) {
            return null;
        }
        return trimmed.substring(firstSpace + 1).trim();
    }
}
