# validation-service

Servicio Spring Boot principal de SIPRO. Expone la API REST para autenticacion, validacion de archivos, planillas, resumen consolidado, consolidacion manual temporal e integracion con Landing Zone.

## Alcance del servicio

- Validacion de archivos Excel sync y async.
- Persistencia de lotes, planillas y consolidaciones en PostgreSQL.
- Comparacion del consolidado con el archivo CREFFSOS publicado.
- Generacion y consulta de resumen consolidado por periodo.
- Gestion del flujo de aprobacion con visibilidad por lider asignado.
- Integracion con LZ/Impala y storage local/S3.

## Paquetes clave

```text
src/main/java/com/bancolombia/sipro/validations/
├── api/                       # Controladores legacy de validacion, health y config
├── application/               # DTOs y use cases
├── domain/model/              # Entidades de dominio y entidades JPA activas
├── domain/service/            # Servicios de consolidacion, resumen, reglas, storage
├── infrastructure/config/     # DataSources, security, async, scheduler
├── infrastructure/entrypoint/ # AuthController, MainController, PlanillaController, LzIngestionController
├── infrastructure/repository/ # Repositorios JPA
├── service/                   # Validacion y store temporal de lotes/jobs
└── shared/                    # Excepciones y utilidades
```

## Comandos recomendados

### Desde backend/

```powershell
.\gradlew.bat :services:validation-service:compileJava
.\gradlew.bat :services:validation-service:test
.\gradlew.bat :services:validation-service:bootRun
```

### Desde la raiz del monorepo

```powershell
.\run-backend.bat
```

## Endpoints mas usados

| Metodo | Endpoint | Uso |
|--------|----------|-----|
| POST | /api/auth/login | Login y carga inicial de permisos |
| POST | /api/validar | Validacion sincrona |
| POST | /api/validar/async | Validacion asincrona |
| GET | /api/validar/jobs/{jobId} | Polling del job |
| POST | /api/planillas/solicitar | Solicitar aprobacion |
| GET | /api/planillas/para-aprobador | Bandeja del aprobador |
| PUT | /api/planillas/{id}/aprobar | Aprobar planilla |
| PUT | /api/planillas/{id}/rechazar | Rechazar planilla |
| GET | /api/planillas/tablero-control | Estado por producto y segmento para tablero |
| GET | /api/main/consolidacion/resumen | Resumen consolidado |
| GET | /api/main/consolidacion/detalle-diferencia | Detalle consolidado por periodo |
| GET | /api/main/consolidacion/resumen/reporte | Descarga XLSX del resumen consolidado |
| POST | /api/admin/consolidacion/manual | Iniciar consolidacion manual (panel /admin, exclusivo Admin_Permisos) |
| POST | /api/lz/ingest | Iniciar ingesta LZ |

## Comportamientos importantes

- La aprobacion operativa usa el lider asignado en la planilla para visibilidad y autorizacion.
- La sesion del frontend se protege durante operaciones largas como validacion y consolidacion manual.
- Las diferencias de conciliacion se calculan aparte de las observaciones internas de calidad en los datos consolidados.
- El servicio arranca en dev con seguridad relajada y truststore de Impala opcional.

## Documentos complementarios

- [INICIO_RAPIDO.md](INICIO_RAPIDO.md)
- [LOGIN_README.md](LOGIN_README.md)
- [src/main/resources/certificates/README.md](src/main/resources/certificates/README.md)