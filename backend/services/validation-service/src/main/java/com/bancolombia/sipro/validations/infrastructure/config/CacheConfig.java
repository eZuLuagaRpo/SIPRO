package com.bancolombia.sipro.validations.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * Nombres de los caches en memoria de SIPRO.
 * La configuracion de Caffeine (TTL, tamaño maximo) se define en application.yml
 * bajo spring.cache.caffeine.spec — Spring Boot la aplica automaticamente.
 */
@Configuration
public class CacheConfig {

    public static final String CACHE_PERMISOS_USUARIO = "permisosUsuario";
    public static final String CACHE_LIST_OBJECTS_NAS = "listObjectsNas";
    public static final String CACHE_GRUPOS_GRAPH = "gruposGraph";
}
