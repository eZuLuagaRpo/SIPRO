# Despliegue - SIPRO Backend

Activos de empaquetado y despliegue del validation-service: Dockerfile y chart Helm. Esta carpeta es una copia de trabajo, preparada para el dia en que `backend/` se convierta en la raiz de su propio repositorio.

Mientras el monorepo siga unido, `infra/` en la raiz del repo sigue siendo la version activa y documentada — no se ha eliminado ni modificado nada de ahi. Esta carpeta no la reemplaza todavia, es un adelanto.

LocalStack (simulacion de S3 para desarrollo local) no esta incluido aqui a proposito: es una herramienta de desarrollo, no de despliegue, y sigue viviendo en `infra/localstack/` hasta que se decida su ubicacion definitiva.

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

Chart base para desplegar el servicio. Varios valores siguen siendo placeholders corporativos (imagen, dominio) y deben revisarse antes de un despliegue real — el detalle de cada variable esta comentado en `helm/values.yaml`.

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

## LocalStack

`localstack/start-localstack.ps1` automatiza levantar LocalStack en Docker dentro de WSL2 para simular S3 en desarrollo local: mantiene el forwarding a `localhost:4566`, asegura que exista el bucket `sipro-bucket` y restaura un seed minimo cuando el volumen queda vacio.

### Modos soportados

- `Start`: levanta o reutiliza el contenedor y deja corriendo un keepalive en segundo plano.
- `Status`: muestra contenedor, health, hooks de init y conteo de objetos del bucket.
- `Stop`: detiene el contenedor y el keepalive de Windows.
- `Recreate`: elimina contenedor y datos runtime, pero conserva `init-scripts` y `seed`.