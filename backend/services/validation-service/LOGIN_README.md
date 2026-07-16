# Sistema de Login y Permisos - SIPRO

Documento de referencia del flujo actual de autenticacion para SIPRO. El login ya no vive en paginas estaticas del backend; el acceso operativo ocurre desde la SPA Angular en /login y consume la API del validation-service.

## Estado actual del flujo

- El frontend obtiene clientId y tenantId desde GET /api/auth/entra/config.
- El frontend inicia sesion con Entra ID usando MSAL y envía el idToken a POST /api/auth/login.
- El backend valida el token de Entra ID, resuelve perfil/manager/grupos en Microsoft Graph y luego monta la sesion SIPRO.
- Los permisos efectivos se filtran por los grupos AD del usuario, mapeados contra sipro_roles_permisos.grupo_ad.
- La respuesta incluye datos del usuario, permisos RBAC efectivos y timeout de sesion.
- AuthService persiste la sesion en sessionStorage y expone currentUser$.
- Los guards del frontend controlan acceso a /inicio, /cargar, /aprobacion y /resumen.

## Request y response del login

### Request

```json
{
  "idToken": "<token-id-emitido-por-entra-id>"
}
```

### Response exitosa

```json
{
  "success": true,
  "mensaje": "Autenticacion exitosa",
  "idUsuario": 1,
  "usuario": "diego",
  "nombres": "Diego",
  "apellidos": "...",
  "correo": "diego@bancolombia.com.co",
  "areaNombre": "Analitica / Operaciones",
  "jefeNombre": "Laura Diaz Lopera",
  "permisos": {
    "puedeCargar": true,
    "puedeAprobar": false,
    "puedeSolicitarAprobacion": false,
    "puedeVisualizar": false,
    "puedeExportar": true,
    "puedeModificarParametros": false,
    "productosAsignados": []
  },
  "sessionTimeoutMinutes": 5
}
```

## Archivos relevantes

### Backend

- src/main/java/com/bancolombia/sipro/validations/infrastructure/entrypoint/AuthController.java
- src/main/java/com/bancolombia/sipro/validations/application/usecase/LoginUseCase.java
- src/main/java/com/bancolombia/sipro/validations/domain/service/RbacService.java
- src/main/java/com/bancolombia/sipro/validations/application/dto/LoginRequest.java
- src/main/java/com/bancolombia/sipro/validations/application/dto/LoginResponse.java

### Frontend

- frontend/src/app/components/login/
- frontend/src/app/services/auth.service.ts
- frontend/src/app/guards/auth.guard.ts
- frontend/src/app/models/user.model.ts

## Sesion y timeout

- El frontend usa sessionStorage, no localStorage.
- El timeout por defecto es de 5 minutos si el backend no envía otro valor.
- El cierre de sesion limpia SIPRO y también dispara logoutPopup de MSAL cuando ya existe contexto Entra cargado.
- AuthService tiene actividades protegidas para extender la sesion durante procesos largos como carga, aprobacion o consolidacion manual.
- Si la sesion expira, el usuario se limpia del BehaviorSubject y debe autenticarse de nuevo.

## Guards actuales

| Guard | Uso |
|------|-----|
| authGuard | Requiere usuario autenticado |
| cargarGuard | Requiere autenticacion y permiso de carga |
| aprobacionGuard | Requiere autenticacion y permiso de aprobacion |

## Endpoints relacionados

| Metodo | Endpoint | Proposito |
|--------|----------|-----------|
| GET | /api/auth/entra/config | Configuracion publica para iniciar login con Entra ID |
| POST | /api/auth/login | Bootstrap de sesion SIPRO a partir del idToken de Entra ID |
| GET | /api/auth/permisos/{idUsuario} | Refrescar permisos |
| GET | /api/auth/health | Health del modulo auth |

## Prueba manual recomendada

1. Arranca backend y frontend.
2. Abre http://localhost:4200/login.
3. Pulsa Iniciar sesion y autentica con una cuenta corporativa valida.
4. Verifica redireccion a /inicio.
5. Verifica que los permisos efectivos correspondan a los grupos AD del usuario (por ejemplo, SIPRO_Usuario_Cargador o SIPRO_Usuario_Aprobador).
6. Si el usuario no tiene permiso, intenta navegar a /cargar o /aprobacion y valida redireccion a /inicio.
6. Refresca la pagina y confirma restauracion de sesion desde sessionStorage.

## Consideraciones de seguridad

- El backend sigue con seguridad relajada para desarrollo y no bloquea la API por Spring Security.
- Los guards Angular y RBAC mejoran UX y control funcional, pero no reemplazan una proteccion backend endurecida.
- El secreto AZURE_CLIENT_SECRET se consume solo en backend desde sipro_parametros_unico; no debe exponerse al frontend ni a archivos cliente.
- Para resolver grupos por Graph se requieren permisos de aplicacion adecuados en Microsoft Graph, ademas del acceso al perfil del usuario.
