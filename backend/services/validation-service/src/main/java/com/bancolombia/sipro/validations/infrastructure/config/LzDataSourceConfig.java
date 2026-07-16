package com.bancolombia.sipro.validations.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * Configuración LZ (Landing Zone - Impala On-Prem).
 *
 * La conexion a LZ se gestiona de forma dinámica por-peticion en LzJdbcService,
 * construyendo la URL JDBC a partir de lz.host y lz.port + credenciales de
 * Secrets Manager (o shortcut DEV). Esto sigue el patron del ejemplo
 * LzMinimalTest.java ("Conectar a LZ").
 *
 * NO se usa un DataSource/Pool permanente porque:
 *   1. La ingesta se ejecuta 1 vez al mes.
 *   2. Impala no soporta bien pools de conexiones largas.
 *   3. En DEV sin red corporativa el pool fallaria al arrancar.
 *
 * Si en el futuro se necesita un pool, se puede crear aqui con
 * @ConditionalOnProperty("lz.host") y HikariDataSource.
 */
@Configuration
public class LzDataSourceConfig {
    // Intencionalmente vacia — la logica de conexion esta en LzJdbcService.
    // Se mantiene la clase para referencias en @ConditionalOnBean y como
    // punto de extension futuro.
}
