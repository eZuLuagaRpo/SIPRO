# Seguridad - Frontend

Resumen de la postura de seguridad actual del frontend SIPRO y de los pendientes de endurecimiento conocidos.

## Controles vigentes

- Credenciales y secretos reales no deben almacenarse en Git.
- El frontend protege navegacion con guards por autenticacion, carga y aprobacion.
- La sesion del frontend vive en sessionStorage y aplica timeout con extensiones controladas durante actividades largas.
- El login ya no pide usuario/clave local; toda autenticacion interactiva se hace contra Entra ID (MSAL).

## Limitaciones actuales que deben asumirse como conocidas

- Los guards Angular no sustituyen autorizacion backend endurecida — son experiencia de usuario, no el control real. El control real (RBAC) vive en el backend.
- El frontend confia en los permisos que el backend devuelve al hacer login; no valida RBAC de forma independiente.

## Lineamientos obligatorios

- No commitear secretos, passwords ni tokens.
- No introducir cambios que rompan de forma abrupta el login o los flujos operativos vigentes.
- Cualquier endurecimiento futuro debe preservar el desarrollo local y la transicion controlada entre ambientes.

## Recomendaciones operativas

- Verifica los guards de ruta (autenticacion, carga, aprobacion, modulos administrativos) cuando cambies permisos o roles.
- Ejecuta pruebas y build despues de cualquier ajuste de seguridad o de guards.
- Para el detalle de login, permisos y sesion del lado del backend, revisa el repositorio de backend (LOGIN_README.md).