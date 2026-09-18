# Seguridad - Frontend

Resumen de la postura de seguridad del frontend SIPRO.

## Controles vigentes

- Credenciales y secretos reales no deben almacenarse en Git.
- El frontend protege navegacion con guards por autenticacion, carga, aprobacion, admin, parametros y tablero (`frontend/src/app/guards/auth.guard.ts`).
- La sesion del frontend vive en sessionStorage y aplica timeout con extensiones controladas durante actividades largas.
- El login ya no pide usuario/clave local; toda autenticacion interactiva se hace contra Entra ID (MSAL).
- Los permisos que el frontend usa para mostrar u ocultar funcionalidad son los que el backend calcula y devuelve al hacer login; el control de acceso real (RBAC) se aplica en el backend, protegido por `SecurityConfig`/`EntraAuthenticationFilter`.

## Lineamientos obligatorios

- No commitear secretos, passwords ni tokens.
- No introducir cambios que rompan de forma abrupta el login o los flujos operativos vigentes.
- Cualquier endurecimiento futuro debe preservar el desarrollo local y la transicion controlada entre ambientes.

## Recomendaciones operativas

- Verifica los guards de ruta (autenticacion, carga, aprobacion, admin, parametros, tablero) cuando cambies permisos o roles.
- Ejecuta pruebas y build despues de cualquier ajuste de seguridad o de guards.
- Para el detalle de login, permisos y sesion del lado del backend, revisa [backend/services/validation-service/LOGIN_README.md](../backend/services/validation-service/LOGIN_README.md) y [backend/SECURITY.md](../backend/SECURITY.md).