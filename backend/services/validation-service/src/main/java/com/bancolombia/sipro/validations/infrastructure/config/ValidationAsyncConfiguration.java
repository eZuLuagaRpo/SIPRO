package com.bancolombia.sipro.validations.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(ValidationAsyncProperties.class)
/**
 * Configura el pool usado por las validaciones asíncronas de archivos.
 *
 * La idea es poder mover tamaño de cola, hilos base y nombre de threads desde
 * configuración sin tocar código cada vez que cambie la carga operativa.
 *
 * Nota de mantenimiento 2026: comentarios funcionales agregados por
 * Junior Alexander Ortiz Arenas (junortiz), ANALITICO/A - EVC OTRAS FUNCIONES CORPORATIVAS.
 */
public class ValidationAsyncConfiguration {

    @Bean(name = "validationTaskExecutor")
    public Executor validationTaskExecutor(ValidationAsyncProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Todos estos valores salen de properties para poder ajustar capacidad por ambiente
        // sin recompilar el backend.
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        // initialize() deja el executor listo para que Spring lo inyecte de inmediato.
        executor.initialize();
        return executor;
    }
}