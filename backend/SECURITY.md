# Seguridad - Backend

Resumen de la postura de seguridad actual del backend SIPRO y de los pendientes de endurecimiento conocidos.

## Controles vigentes

- Credenciales y secretos reales no deben almacenarse en Git.
- El backend usa Spring Security, pero en el perfil de desarrollo la configuracion funcional actual esta en modo permitAll para no bloquear los flujos locales.
- El login operativo se resuelve contra PostgreSQL y retorna permisos RBAC al frontend.
- La conectividad a Impala usa truststore y debe manejarse con criterios de secreto por ambiente.

## Limitaciones actuales que deben asumirse como conocidas

- No hay JWT corporativo ni resource server activo en este momento.
- SecurityConfig esta orientado a desarrollo local; cualquier fortalecimiento debe hacerse de forma incremental y compatible.
- Los guards del frontend no sustituyen autorizacion backend endurecida — la autorizacion real vive aqui (RBAC via sipro_roles_permisos).

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