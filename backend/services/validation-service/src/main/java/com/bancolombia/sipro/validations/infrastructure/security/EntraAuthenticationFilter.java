package com.bancolombia.sipro.validations.infrastructure.security;

import com.bancolombia.sipro.validations.domain.model.UsuarioLogin;
import com.bancolombia.sipro.validations.infrastructure.security.EntraIdTokenService.EntraAuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Valida el bearer token de Entra ID para todos los endpoints protegidos.
 */
@Component
public class EntraAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(EntraAuthenticationFilter.class);
    private static final String GRAPH_ACCESS_TOKEN_HEADER = "X-Graph-Access-Token";

    private static final List<RequestMatcher> PUBLIC_MATCHERS = List.of(
            new AntPathRequestMatcher("/", "GET"),
            new AntPathRequestMatcher("/error"),
            new AntPathRequestMatcher("/health", "GET"),
            new AntPathRequestMatcher("/health/s3", "GET"),
            new AntPathRequestMatcher("/api/auth/login", "POST"),
            new AntPathRequestMatcher("/api/auth/entra/config", "GET"),
            new AntPathRequestMatcher("/api/auth/health", "GET"),
            new AntPathRequestMatcher("/actuator/health", "GET"),
            new AntPathRequestMatcher("/actuator/info", "GET")
    );

    private final EntraIdTokenService entraIdTokenService;
    private final LocalUserProvisioningService localUserProvisioningService;
    private final MicrosoftGraphDirectoryService microsoftGraphDirectoryService;

    public EntraAuthenticationFilter(EntraIdTokenService entraIdTokenService,
                                     LocalUserProvisioningService localUserProvisioningService,
                                     MicrosoftGraphDirectoryService microsoftGraphDirectoryService) {
        this.entraIdTokenService = entraIdTokenService;
        this.localUserProvisioningService = localUserProvisioningService;
        this.microsoftGraphDirectoryService = microsoftGraphDirectoryService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        return PUBLIC_MATCHERS.stream().anyMatch(matcher -> matcher.matches(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authorization.substring(7).trim();
            EntraAuthenticatedUser entraUser = entraIdTokenService.authenticate(token);
            UsuarioLogin usuario = localUserProvisioningService.findExistingUser(entraUser)
                    .orElseThrow(() -> new IllegalArgumentException("Debes iniciar sesión en SIPRO antes de consumir APIs protegidas"));

            Set<String> gruposAd = entraUser.groupNames();
            String graphAccessToken = request.getHeader(GRAPH_ACCESS_TOKEN_HEADER);
            if (graphAccessToken != null && !graphAccessToken.isBlank()) {
            try {
                gruposAd = microsoftGraphDirectoryService.resolveCurrentUserGroupNames(
                    graphAccessToken,
                    entraUser.groupNames(),
                    entraUser.groupsOverage());
            } catch (Exception ex) {
                logger.warn("No se pudieron refrescar grupos delegados para usuario {}. Se usarán claims del token. Motivo: {}",
                    usuario.getIdUsuario(), ex.getMessage());
            }
            }

            SiproAuthenticatedUser principal = new SiproAuthenticatedUser(
                    usuario.getIdUsuario(),
                    usuario.getUsuario(),
                    entraUser.alias(),
                    entraUser.preferredUsername(),
                    entraUser.email(),
                    entraUser.objectId(),
                gruposAd,
                    entraUser.groupsOverage());

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    token,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, ex.getMessage());
        }
    }
}