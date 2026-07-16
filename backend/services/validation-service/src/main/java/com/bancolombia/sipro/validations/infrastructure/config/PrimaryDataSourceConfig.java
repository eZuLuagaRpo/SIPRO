package com.bancolombia.sipro.validations.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Configuración explícita del DataSource principal (PostgreSQL).
 * Necesario para evitar conflictos con el DataSource secundario de LZ.
 */
@Configuration
public class PrimaryDataSourceConfig {

    /**
     * Expone las propiedades base del datasource principal definidas en spring.datasource.
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Construye el datasource principal de PostgreSQL usando HikariCP.
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource primaryDataSource(DataSourceProperties primaryDataSourceProperties) {
        return primaryDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
