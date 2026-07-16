package com.bancolombia.sipro.validations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada del servicio de validaciones y habilitador de tareas programadas y asíncronas.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class ValidationServiceApplication {

    /**
     * Inicia el contexto Spring Boot del servicio.
     */
    public static void main(String[] args) {
        SpringApplication.run(ValidationServiceApplication.class, args);
    }
}
