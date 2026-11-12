# SIPRO - Backend

Backend Gradle multi-project del sistema SIPRO. El modulo operativo actual es services/validation-service, un servicio Spring Boot 3.4 sobre Java 17 con persistencia JPA/Hibernate, almacenamiento de archivos y conectividad a Landing Zone.

## Requisitos previos

- Java 17.0.17 o compatible con el wrapper de Gradle.
- PostgreSQL local disponible para el perfil dev.
- Truststore de Impala si vas a usar integracion LZ real (ver services/validation-service/src/main/resources/certificates/README.md).
- LocalStack opcional para desarrollo con almacenamiento tipo S3 (ver deployment/README.md).

## URLs utiles (desarrollo local)

- Backend API: http://localhost:8080/api
- Health: http://localhost:8080/api/health
- Health S3: http://localhost:8080/api/health/s3
- Actuator: http://localhost:8080/actuator/health

## Stack principal

| Tecnologia | Version | Uso |
|-----------|---------|-----|
| Java | 17.0.17 | Runtime del servicio |
| Spring Boot | 3.4.0 | API REST y configuracion |
| Gradle | 8.5 | Build y tareas |
| PostgreSQL | 18.1 | Base principal |
| Hibernate | 6.6.2.Final | ORM |
| Apache POI | 5.2.5 | Excel |
| Apache Commons JEXL | 3.3 | Reglas dinamicas |
| AWS SDK v2 | 2.20.56 | Storage / S3 |
| Cloudera Impala | JAR local | Consultas Landing Zone |

### 📍 Evidencia del stack

- 📍 Java 17 de compilacion: [services/validation-service/build.gradle](services/validation-service/build.gradle) y [gradle.properties](gradle.properties)
- 📍 Spring Boot 3.4.0: [services/validation-service/build.gradle](services/validation-service/build.gradle)
- 📍 Gradle wrapper del repo: [gradle/wrapper/gradle-wrapper.properties](gradle/wrapper/gradle-wrapper.properties)
- 📍 PostgreSQL como base principal: [services/validation-service/build.gradle](services/validation-service/build.gradle) y [services/validation-service/src/main/resources/application.yml](services/validation-service/src/main/resources/application.yml)
- 📍 S3, LocalStack e Impala/LZ: [services/validation-service/src/main/resources/application.yml](services/validation-service/src/main/resources/application.yml)

## Estructura

```text
backend/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew.bat
└── services/
    └── validation-service/
        ├── build.gradle
        ├── README.md
        ├── INICIO_RAPIDO.md
        ├── LOGIN_README.md
        ├── libs/
        └── src/
            ├── main/
            └── test/
```

## Capacidades implementadas

- Login contra PostgreSQL y consulta de permisos RBAC.
- Validacion sync y async de archivos Excel.
- Descarga de errores por lote.
- Solicitud de aprobacion y flujo de planillas.
- Resumen de cargas y aprobaciones pendientes.
- Aprobacion por lider asignado en la planilla.
- Consolidacion de periodos, generacion/comparacion CREFFSOS y resumen consolidado.
- Consolidacion manual asincrona por periodo con consulta de estado.
- Integracion con Landing Zone para datos maestros e ingesta.

## Comandos de trabajo

### Build y pruebas

```powershell
cd backend
.\gradlew.bat build
.\gradlew.bat clean build
.\gradlew.bat :services:validation-service:compileJava
.\gradlew.bat :services:validation-service:test
```

### Ejecucion local

```powershell
cd backend
.\gradlew.bat :services:validation-service:bootRun
```

El bootRun activa el perfil dev por defecto y agrega el truststore de Impala si existe en el directorio de certificados del servicio.
El truststore de Impala ya no se inyecta como JVM arg global; LzJdbcService lo aplica solo al abrir conexiones JDBC de LZ para no afectar otras llamadas HTTPS del backend.

## Endpoints principales

### Auth

| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| POST | /api/auth/login | Autenticacion de usuario |
| GET | /api/auth/permisos/{idUsuario} | Consulta de permisos RBAC |
| GET | /api/auth/health | Health del modulo auth |

### Validacion

| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| POST | /api/validar | Validacion sincrona de archivo |
| POST | /api/validar/async | Inicio de validacion asincrona |
| GET | /api/validar/jobs/{jobId} | Estado del job de validacion |
| GET | /api/lotes/{id}/errores | Descarga CSV de errores del lote |

### Planillas

| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| POST | /api/planillas/solicitar | Solicitud de aprobacion |
| GET | /api/planillas/resumen | Resumen del cargador |
| GET | /api/planillas/resumen-aprobador | Resumen del aprobador |
| GET | /api/planillas/cargas-pendientes | Pendientes de carga del mes anterior |
| GET | /api/planillas/aprobaciones-pendientes | Pendientes de aprobacion ultimos 3 meses |
| GET | /api/planillas/todas | Lista completa de planillas |
| GET | /api/planillas/por-lider | Planillas activas por id_lider |
| GET | /api/planillas/para-aprobador | Planillas visibles para el aprobador |
| GET | /api/planillas/pendientes | Lista legacy de pendientes |
| GET | /api/planillas/{id} | Detalle de planilla |
| PUT | /api/planillas/{id}/aprobar | Aprobar planilla |
| PUT | /api/planillas/{id}/rechazar | Rechazar planilla |
| POST | /api/planillas/{id}/notificaciones/prueba | Prueba de notificacion |
| GET | /api/planillas/{id}/descargar | Descargar adjunto |

### Catalogos, resumen y consolidacion

| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| GET | /api/main/productos | Catalogo de productos |
| GET | /api/main/segmentos | Catalogo de segmentos |
| GET | /api/main/consolidacion/resumen | Resumen consolidado mensual |
| POST | /api/admin/consolidacion/manual | Inicio de consolidacion manual (panel /admin, exclusivo Admin_Permisos). El estado se consulta como parte de /api/admin/dashboard. |

### Configuracion y LZ

| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| GET | /api/config/rango-fecha-corte | Rango permitido de fecha de corte |
| GET | /api/config/ventana-carga | Ventana habilitada de carga por periodo |
| POST | /api/config/parametros-unicos/recargar | Recarga parametros unicos |
| POST | /api/lz/ingest | Dispara ingesta LZ |
| GET | /api/lz/test-connection | Prueba de conexion LZ |
| GET | /api/lz/status | Estado de ingesta LZ |
| GET | /api/lz/test/check/{tableKey} | Endpoint auxiliar de prueba LZ |
| GET | /api/health | Health general |
| GET | /api/health/s3 | Health del storage |

## Notas tecnicas relevantes

- ValidationAsyncService usa un ejecutor dedicado llamado validationTaskExecutor.
- ConsolidacionManualAsyncService reaprovecha ese ejecutor y persiste estado por periodo sin crear tablas nuevas.
- El resumen consolidado diferencia observaciones internas de calidad de diferencias reales con CREFFSOS.
- SecurityConfig esta relajado en dev con permitAll; cualquier endurecimiento futuro debe ser incremental.
- El modulo mantiene coexistencia entre controladores legacy en api/ y entrypoints hexagonales en infrastructure/entrypoint/.

## Documentacion relacionada

- [services/validation-service/README.md](services/validation-service/README.md)
- [SECURITY.md](SECURITY.md)
- [deployment/README.md](deployment/README.md)