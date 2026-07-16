package com.bancolombia.sipro.validations.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración CORS a nivel Spring MVC. En la práctica, con Spring Security activo,
 * es SecurityConfig.corsConfigurationSource() quien resuelve CORS antes de llegar aquí —
 * esta clase se mantiene alineada al mismo origen configurado para no desincronizarse.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Origenes permitidos para CORS. Configurable via app.cors.allowed-origins (ver application*.yml). */
    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}