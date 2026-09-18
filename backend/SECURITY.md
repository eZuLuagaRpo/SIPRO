# Seguridad - Backend

Resumen de la postura de seguridad del backend SIPRO.

## Controles vigentes

- Credenciales y secretos reales no deben almacenarse en Git.
- SecurityConfig usa Spring Security en modo stateless (sin sesion HTTP) y exige autenticacion en cualquier ruta que no este en la lista blanca (`/`, `/health`, `/health/s3`, `/error`, `POST /api/auth/login`, `GET /api/auth/entra/config`, `GET /api/auth/health`, `/actuator/health`, `/actuator/info`, y `OPTIONS` para CORS). Todo lo demas responde 401/403 sin token valido.
- `EntraAuthenticationFilter` se ejecuta antes que `UsernamePasswordAuthenticationFilter` en cada peticion: extrae el Bearer token, lo valida contra Microsoft Entra ID (issuer, audiencia y firma JWKS reales via `EntraIdTokenService`) y solo entonces deja pasar la peticion, en todos los ambientes (dev incluido). La validacion se basa en el idToken emitido por Entra ID.
- El login se resuelve validando el idToken de Entra ID contra PostgreSQL (tabla de usuarios) y retorna permisos RBAC efectivos al frontend, calculados a partir de los grupos AD del usuario.
- La autorizacion real por rol (RBAC via `sipro_roles_permisos`) vive en el backend; los guards del frontend son experiencia de usuario, no el punto de control.
- La conectividad a Impala usa truststore y debe manejarse con criterios de secreto por ambiente.

## Lineamientos obligatorios

- No commitear secretos, passwords, tokens, certificados privados ni llaves.
- No debilitar trazabilidad, auditoria ni segregacion de ambientes.
- No introducir cambios de seguridad que rompan de forma abrupta el login o los flujos operativos vigentes.
- Cualquier endurecimiento futuro debe preservar el desarrollo local y la transicion controlada entre ambientes.

## Recomendaciones operativas

- Usa gestores corporativos para secretos y certificados sensibles.
- Revisa [services/validation-service/src/main/resources/certificates/README.md](services/validation-service/src/main/resources/certificates/README.md) antes de tocar truststores.
- Verifica permisos RBAC y filtros por lider asignado cuando hagas cambios en aprobacion.
- Ejecuta pruebas y build despues de cualquier ajuste de seguridad.

## Referencias

- [services/validation-service/LOGIN_README.md](services/validation-service/LOGIN_README.md)