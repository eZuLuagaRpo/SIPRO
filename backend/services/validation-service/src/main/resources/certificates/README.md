# Certificados y truststore de validation-service

Carpeta de soporte para certificados usados por la integracion SSL/TLS de Impala (Landing Zone).

## Por que el JKS NO esta en el repositorio

Los certificados del ambiente Bancolombia rotan periodicamente (cada pocos meses).
El archivo `impala-truststore.jks` esta excluido por `.gitignore` para evitar
commitear material sensible y obligar a commits en cada rotacion.

## Ambientes y configuracion

| Ambiente | Host LZ                         | Truststore                                                  |
|----------|---------------------------------|-------------------------------------------------------------|
| DEV      | 10.8.85.237:21050               | Classpath: `certificates/impala-truststore.jks` (en repo)  |
| QA       | 10.8.85.237:21050               | Classpath: `certificates/impala-truststore.jks` (en repo)  |
| PDN      | impala.bancolombia.corp:21050   | Truststore productivo — gestionado fuera de este repo       |

## Como colocar el truststore (DEV local) — Opcion A (recomendada)

`application-dev.yml` apunta por defecto a:
```
C:\Temp\Certificados_Bancolombia\impala-truststore.jks
```
Si el archivo esta ahi, no se necesita nada mas.
Para usar otra ruta, define la variable de entorno `LZ_TRUSTSTORE_PATH`.

## Como colocar el truststore (classpath) — Opcion B

Copia el JKS a esta misma carpeta:
```powershell
Copy-Item "C:\Temp\Certificados_Bancolombia\impala-truststore.jks" `
  "backend\services\validation-service\src\main\resources\certificates\"
```
`LzJdbcService.prepareTruststore()` lo detecta automaticamente si `LZ_TRUSTSTORE_PATH` esta vacio.

> NOTA: el archivo sigue ignorado por git aunque lo copies aqui. Debes copiarlo
> en cada maquina/pipeline que necesite conectarse a LZ.

## Rotacion de certificados (cada vez que Bancolombia entregue nuevos)

```powershell
# 1. Importar el PEM al JKS (storepass standard del proyecto: changeit)
keytool -import -trustcacerts `
  -alias impala-ambientesbc `
  -file "C:\Temp\Certificados_Bancolombia\cacerts_ambientesbc.pem" `
  -keystore "C:\Temp\Certificados_Bancolombia\impala-truststore.jks" `
  -storepass changeit -noprompt

# 2. Verificar contenido del truststore
keytool -list -keystore "C:\Temp\Certificados_Bancolombia\impala-truststore.jks" `
  -storepass changeit
```

El backend DEV lo toma automaticamente sin reiniciar (si `LZ_TRUSTSTORE_PATH` apunta ahi).
Para QA/PDN, copiar el nuevo JKS a esta carpeta antes del siguiente build.

## Variables de entorno relevantes

| Variable             | Descripcion                                      | Perfil   |
|----------------------|--------------------------------------------------|----------|
| `LZ_TRUSTSTORE_PATH` | Ruta al JKS. Vacio = usa classpath.             | DEV/QA   |
| `LZ_TRUSTSTORE_PWD`  | Contrasena del truststore. Default: `changeit`  | Todos    |
| `LZ_DEV_USER`        | Usuario ambientesBC (bypass DEV).               | DEV only |
| `LZ_DEV_PASSWORD`    | Clave ambientesBC. NUNCA en YAML.               | DEV only |

## Regla principal

No subas claves privadas ni contrasenas sensibles al repositorio.
Solo certificados publicos de confianza (CAs) estan permitidos aqui, y aun asi
solo cuando la politica corporativa lo permita explicitamente.

## Recomendacion por ambiente

### Desarrollo local

- Usa el archivo en esta carpeta solo si es necesario para probar LZ real.
- Documenta su origen y mantelo sincronizado con el equipo.

### QA / Produccion

- Externaliza la ruta y el password via variables o gestor de secretos.
- No dependas de certificados embebidos en la imagen o en el repo.

## Variables tipicas

```text
TRUSTSTORE_PATH
TRUSTSTORE_PASSWORD
```

## Soporte

Si un ajuste de certificados afecta la conectividad a LZ, revisa tambien:

- backend/services/validation-service/build.gradle
- backend/services/validation-service/src/main/java/com/bancolombia/sipro/validations/infrastructure/config/
- backend/services/validation-service/src/main/java/com/bancolombia/sipro/validations/infrastructure/lz/
