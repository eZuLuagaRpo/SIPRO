# Despliegue - SIPRO Backend

Activos de empaquetado y despliegue del validation-service: Dockerfile y chart Helm, para construir y correr el backend como imagen de contenedor. El despliegue operativo de SIPRO en AWS se hace por otra via — ver la seccion "Despliegue e infraestructura" del [README.md raiz](../../README.md#12-despliegue-e-infraestructura).

## Estructura

```text
deployment/
├── Dockerfile
└── helm/
    ├── Chart.yaml
    ├── values.yaml
    └── templates/
        ├── configmap.yaml
        ├── deployment.yaml
        ├── ingress.yaml
        ├── secret.yaml
        └── service.yaml
```

## Docker

Empaqueta el JAR del backend en una imagen ejecutable. El contexto de build es la raiz de `backend/` (donde vive este mismo folder `deployment/`), no la carpeta `deployment/` en si.

### Build

Ejecutar desde la raiz de `backend/`:

```powershell
docker build -f deployment/Dockerfile -t sipro-validation-service .
```

### Run

```powershell
docker run -p 8080:8080 sipro-validation-service
```

### Notas

- Build multi-stage: Temurin 21 JDK para compilar, Temurin 21 JRE para runtime — la app sigue compilando con toolchain Java 17 dentro de Gradle.
- El wrapper `gradlew` pierde el bit de ejecucion al versionarse desde Windows; el Dockerfile lo corrige con `chmod +x gradlew` antes de invocarlo.
- No se hornean secretos ni certificados (`.jks`, `.pem`, etc.) en la imagen — se inyectan en runtime via variables de entorno. Ver `.dockerignore` en la raiz de `backend/`.

## Helm

Chart base para desplegar el servicio en Kubernetes. El detalle de cada variable esta comentado en `helm/values.yaml`.

### Comandos

```powershell
cd deployment/helm
helm install sipro-validation-service .
helm upgrade sipro-validation-service .
helm uninstall sipro-validation-service
```

### Valores que suelen ajustarse por ambiente

- `image.repository` / `image.tag`
- `service.port` / `service.targetPort`
- `ingress.enabled` / `ingress.hosts`
- `env.SPRING_PROFILES_ACTIVE` — obligatorio en un despliegue real ("qa" o "prd"); sin esto no se activa ningun `application-{profile}.yml`.
- `env.JDBC_URL` / `DB_USER` / `DB_PASS`
- `env.APP_CORS_ALLOWED_ORIGINS`
- `env.APP_STORAGE_S3_*`
- `env.APP_MAIL_*` / `MAIL_*`

## LocalStack (desarrollo local con almacenamiento S3)

Para desarrollar con `app.storage.type=s3` sin depender de AWS real, hace falta LocalStack corriendo por cuenta propia (por ejemplo `docker run -p 4566:4566 localstack/localstack`) con el bucket configurado en `application*.yml` ya creado. `S3Config.java` resuelve automaticamente la IP de WSL2 como fallback si `localhost:4566` no responde.