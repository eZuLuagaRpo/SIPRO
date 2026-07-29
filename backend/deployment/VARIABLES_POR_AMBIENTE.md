# Variables de despliegue por ambiente — SIPRO

## Cómo funciona el mecanismo

SIPRO usa **Azure DevOps Replace Tokens** para inyectar configuración por ambiente.

El flujo es el siguiente:

```
Pipeline Azure DevOps
  │
  ├─ 1. Tarea "Replace Tokens" lee application-cloud.yml
  │       Reemplaza cada #{TOKEN}# con el valor del Variable Group activo
  │       (el Variable Group de DEV tiene valores DEV, el de QA tiene QA, etc.)
  │
  └─ 2. App arranca con SPRING_PROFILES_ACTIVE=cloud
          Spring carga: application.yml (base) + application-cloud.yml (ya con valores reales)
```

**Archivos que participan:**

| Archivo | Cuándo se carga |
|---|---|
| `application.yml` | Siempre (base con defaults) |
| `application-cloud.yml` | Ambientes desplegados — DEV, QA, PDN |
| `application-dev.yml` | Solo desarrollo local en la máquina del desarrollador |

`application-qa.yml` y `application-prd.yml` son el mecanismo anterior (variables de entorno `${VAR}`).
Con Replace Tokens ya no son necesarios para el despliegue, pero se conservan como referencia.

---

## Variable SPRING_PROFILES_ACTIVE

Esta variable se configura en el servidor (o en el pipeline) antes de arrancar la app.

| Ambiente | Valor |
|---|---|
| Local (máquina desarrollador) | `dev` |
| DEV desplegado | `cloud` |
| QA desplegado | `cloud` |
| PDN desplegado | `cloud` |

---

## Variables por ambiente

Las columnas marcadas con `⚠️ Pendiente` requieren que el equipo de provisión entregue el valor real.

### Base de datos PostgreSQL

| Token | DEV | QA | PDN |
|---|---|---|---|
| `JDBC_URL` | URL del postgres DEV | URL del postgres QA | ⚠️ Pendiente provisión |
| `DB_USER` | Usuario postgres DEV | Usuario postgres QA | ⚠️ Pendiente provisión |
| `DB_PASS` | Clave postgres DEV | Clave postgres QA | ⚠️ Pendiente provisión |

---

### Autenticación — Active Directory / Entra ID

| Token | DEV | QA | PDN |
|---|---|---|---|
| `AZURE_CLIENT_ID` | Client ID de la app DEV en Entra | Client ID QA | ⚠️ Pendiente provisión |
| `AZURE_TENANT_ID` | Tenant ID de Bancolombia | Mismo que DEV | Mismo que DEV |
| `AZURE_CLIENT_SECRET` | Secret de la app DEV | Secret QA | ⚠️ Pendiente provisión |
| `AD_URL` | URL del AD DEV | URL del AD QA | ⚠️ Pendiente provisión |
| `AD_DOMAIN` | Dominio AD DEV | Dominio AD QA | ⚠️ Pendiente provisión |

---

### URLs del frontend (redireccionamiento OAuth)

| Token | DEV | QA | PDN |
|---|---|---|---|
| `URL_DEV` | URL real del frontend DEV | *(puede quedar vacío)* | *(puede quedar vacío)* |
| `URL_QA` | *(puede quedar vacío)* | URL real del frontend QA | *(puede quedar vacío)* |
| `URL_PDN` | *(puede quedar vacío)* | *(puede quedar vacío)* | ⚠️ URL real del frontend PDN |

---

### CORS

| Token | DEV | QA | PDN |
|---|---|---|---|
| `APP_CORS_ALLOWED_ORIGINS` | URL frontend DEV | URL frontend QA | ⚠️ URL frontend PDN |

Acepta múltiples orígenes separados por coma: `https://sipro-dev.bancolombia.com,https://otro.com`

---

### Almacenamiento S3

| Token | DEV | QA | PDN |
|---|---|---|---|
| `APP_STORAGE_S3_ENDPOINT` | Endpoint S3 DEV | Endpoint S3 QA | ⚠️ Pendiente provisión |
| `APP_STORAGE_S3_REGION` | `us-east-1` | `us-east-1` | ⚠️ Pendiente provisión |
| `APP_STORAGE_S3_BUCKET` | Nombre bucket DEV | Nombre bucket QA | ⚠️ Pendiente provisión |
| `APP_STORAGE_S3_ACCESS_KEY` | Access key DEV | Access key QA | ⚠️ Pendiente provisión |
| `APP_STORAGE_S3_SECRET_KEY` | Secret key DEV | Secret key QA | ⚠️ Pendiente provisión |

> **Nota local:** En la máquina del desarrollador el almacenamiento es local (`C:/s3mock2/sipro-local-storage`).
> En cualquier ambiente desplegado siempre se usa S3.

---

### Landing Zone (LZ — Impala)

DEV y QA usan el ambiente intermedio de LZ. PDN usa el ambiente productivo.

| Token | DEV | QA | PDN |
|---|---|---|---|
| `LZ_HOST` | `10.8.85.237` | `10.8.85.237` | `impala.bancolombia.corp` |
| `LZ_PORT` | `21050` | `21050` | `21050` |
| `APP_LZ_SCHEMA` | `proceso` | `default` | `resultados_fcr` |
| `APP_LZ_TABLE_MDM` | `sipro_fcr_mdm_datos_generales_clientes` | `sipro_fcr_mdm_datos_generales_clientes` | `fcr_mdm_datos_generales_clientes` |

#### Credenciales LZ — dos mecanismos

**DEV y QA: credenciales directas (bypass de Secrets Manager)**

| Token | DEV | QA |
|---|---|---|
| `LZ_USER` | `emzulu@ambientesbc.com` (o el usuario del deployer) | Usuario QA de LZ |
| `LZ_PASSWORD` | Clave del usuario DEV | Clave del usuario QA |
| `LZ_SECRETS_ENDPOINT` | *(dejar vacío — no se usa)* | *(dejar vacío — no se usa)* |
| `LZ_SECRETS_REGION` | *(dejar vacío)* | *(dejar vacío)* |
| `LZ_SECRETS_NAME` | *(dejar vacío)* | *(dejar vacío)* |

Cuando `LZ_USER` y `LZ_PASSWORD` tienen valor, la app los usa directamente y no consulta Secrets Manager.

**PDN: AWS Secrets Manager real**

| Token | PDN |
|---|---|
| `LZ_USER` | *(dejar vacío — obliga a usar Secrets Manager)* |
| `LZ_PASSWORD` | *(dejar vacío — obliga a usar Secrets Manager)* |
| `LZ_SECRETS_ENDPOINT` | *(dejar vacío → SDK usa endpoint AWS real)* |
| `LZ_SECRETS_REGION` | `us-east-1` |
| `LZ_SECRETS_NAME` | `lz/creds` |

El secreto en AWS Secrets Manager debe tener este formato JSON:
```json
{ "user": "usuario_impala", "password": "clave_impala" }
```

#### SSL / Truststore (todos los ambientes desplegados)

| Token | DEV | QA | PDN |
|---|---|---|---|
| `LZ_TRUSTSTORE_PATH` | *(vacío → usa el .jks del classpath)* | *(vacío → classpath)* | Ruta absoluta del JKS en el servidor |
| `LZ_TRUSTSTORE_PWD` | `changeit` | `changeit` | ⚠️ Clave real del truststore PDN |

> **Nota:** El archivo `.jks` está en `src/main/resources/certificates/` y se incluye en el classpath al compilar.
> En PDN puede requerirse apuntar a un JKS externo con `LZ_TRUSTSTORE_PATH`.

---

### Correo electrónico

| Token | DEV | QA | PDN |
|---|---|---|---|
| `APP_MAIL_ENABLED` | `true` | `true` | `true` |
| `APP_MAIL_TRANSPORT` | `ses-api` | `ses-api` | `ses-api` |
| `APP_MAIL_FROM` | `no-reply-sipro@bancolombia.com.co` | `no-reply-sipro@bancolombia.com.co` | `no-reply-sipro@bancolombia.com.co` |
| `APP_MAIL_ACTION_URL` | URL frontend DEV + `/aprobacion` | URL frontend QA + `/aprobacion` | ⚠️ URL PDN + `/aprobacion` |
| `APP_MAIL_BANNER_URL` | URL backend DEV + `/email-assets/SIPRO_BannerCorreo.png` | URL backend QA + `/email-assets/...` | ⚠️ URL backend PDN + `/email-assets/...` |
| `APP_MAIL_SES_REGION` | `us-east-1` | `us-east-1` | `us-east-1` |
| `APP_MAIL_SES_ACCESS_KEY` | Access key SES DEV | Access key SES QA | ⚠️ Pendiente provisión |
| `APP_MAIL_SES_SECRET_KEY` | Secret key SES DEV | Secret key SES QA | ⚠️ Pendiente provisión |

---

## Checklist antes de salir a PDN

- [ ] Variable Group de PDN creado en Azure DevOps con todos los tokens de esta tabla
- [ ] `SPRING_PROFILES_ACTIVE=cloud` configurado en el servidor PDN
- [ ] Secreto `lz/creds` creado en AWS Secrets Manager con formato `{"user":"...","password":"..."}`
- [ ] Bucket S3 creado y credenciales entregadas por provisión
- [ ] Dominio del frontend PDN entregado por provisión (`URL_PDN`, `APP_CORS_ALLOWED_ORIGINS`)
- [ ] Credenciales SES (correo) entregadas por provisión
- [ ] Truststore JKS disponible en el servidor PDN (o confirmar que el del classpath es válido)
- [ ] Base de datos PostgreSQL PDN creada y migrada con Flyway antes del primer arranque
- [ ] App Registration en Entra ID para PDN con `AZURE_CLIENT_ID` y `AZURE_CLIENT_SECRET` correctos
- [ ] Pipeline de Azure DevOps configurado para usar `application-cloud.yml` como archivo destino de Replace Tokens

---

## Resumen rápido de qué entrega quién

| Responsable | Qué entrega |
|---|---|
| **Equipo SIPRO** | Este documento, el archivo `application-cloud.yml`, los nombres exactos de cada token |
| **Equipo de provisión** | Los valores reales de cada token en el Variable Group de Azure DevOps |
| **Equipo de infraestructura** | Bucket S3, Secrets Manager, servidor PostgreSQL PDN, servidor de despliegue |
