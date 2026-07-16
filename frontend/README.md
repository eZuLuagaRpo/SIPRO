# SIPRO - Frontend Angular

Frontend SPA en Angular 20 para SIPRO. Implementa login, dashboard de inicio, carga y validacion de archivos, aprobacion de planillas y resumen consolidado con comparacion contra CREFFSOS.

## Requisitos previos

- Node.js 20.20.0 o superior.
- npm 10.8.2 o superior.
- Backend SIPRO corriendo (local o remoto) para que el proxy de /api tenga a donde apuntar.

## URLs utiles (desarrollo local)

- Frontend: http://localhost:4200
- Login: http://localhost:4200/login
- Inicio: http://localhost:4200/inicio
- Tablero de control: http://localhost:4200/tablero
- Resumen consolidado: http://localhost:4200/resumen

## Stack

| Tecnologia | Version | Uso |
|-----------|---------|-----|
| Angular | 20.0.0 | Framework principal |
| TypeScript | 5.8.3 | Tipado estricto |
| RxJS | 7.8.0 | Estado reactivo y flujos async |
| SCSS | - | Estilos modulares |
| Angular CLI | 20.3.14 | Build y dev server |
| Karma / Jasmine | 6.4.0 / 5.1.0 | Tests unitarios |

## Scripts

```powershell
cd frontend
npm install
npm start
npm run build
npm run build:dev
npm test
```

El dev server corre en http://localhost:4200 y usa proxy para enviar /api al backend en http://localhost:8080.

## Rutas actuales

| Ruta | Componente | Guard | Descripcion |
|------|------------|-------|-------------|
| /login | LoginComponent | - | Autenticacion |
| /inicio | InicioComponent | authGuard | Dashboard principal |
| /cargar | CargarComponent | cargarGuard | Carga y validacion |
| /aprobacion | AprobacionComponent | aprobacionGuard | Flujo de aprobacion |
| /resumen | ResumenComponent | resumenGuard | Resumen consolidado |
| /tablero | TableroComponent | tableroGuard | Tablero de control por producto y segmento |

## Componentes principales

### LoginComponent

- Obtiene la configuracion publica de Entra ID desde GET /api/auth/entra/config.
- Inicia sesion con MSAL y envía el idToken a POST /api/auth/login.
- Guarda usuario, permisos efectivos por grupos AD y expiracion de sesion en sessionStorage.
- Redirige a /inicio despues de autenticar.

### InicioComponent

- Muestra resumen del usuario actual.
- Carga pendientes del cargador o del aprobador segun permisos.
- Incluye control temporal para consolidacion manual por periodo y polling de estado.

### CargarComponent

- Ejecuta validacion de archivos por el flujo asincrono del backend.
- Reutiliza temporalmente el archivo ya validado para solicitar aprobacion sin re-subirlo.
- Permite descargar errores y consultar rangos/ventanas de fecha.

### AprobacionComponent

- Lista planillas visibles para el aprobador actual.
- Aprobacion y rechazo usando el lider asignado a la planilla.
- Descarga de adjuntos y estadisticas de la bandeja.

### ResumenComponent

- Consume el resumen consolidado por anio y mes.
- Presenta productos consolidados, lectura del archivo CREFFSOS y diferencias reales de conciliacion.
- Las observaciones internas de calidad se muestran sin disparar alerta global cuando no hay descuadre entre fuentes.
- Permite exportar a XLSX usando `descargarReporteResumenConsolidado()` y descarga con nombre definido por backend.

### TableroComponent

- Consume el tablero de control por anio y mes.
- Presenta estado de `Cargado` y `Aprobado` para ambos segmentos por producto.
- Reutiliza el mismo perfil de acceso administrativo funcional del modulo Resumen.

## Servicios clave

### AuthService

- Mantiene currentUser$ con BehaviorSubject.
- Persiste la sesion y la configuracion publica de Entra en sessionStorage.
- Inicializa MSAL de forma lazy usando clientId y tenantId obtenidos del backend.
- Aplica timeout de sesion y actividades protegidas para evitar logout durante procesos largos.
- Dispara cierre de sesion local y logoutPopup de Entra ID cuando corresponde.

### ValidationService

Expone los endpoints usados por la SPA:

- Catalogos: productos y segmentos.
- Validacion: sync, async, polling por job y descarga de errores.
- Planillas: solicitud, detalle, aprobacion, rechazo y adjuntos.
- Inicio: resumenes y pendientes por rol.
- Configuracion: rango de fecha y ventana de carga.
- Consolidacion: ejecucion manual temporal, estado y resumen consolidado.
- Resumen/Conciliacion: detalle-diferencia y reporte XLSX (`/main/consolidacion/resumen/reporte`).
- Tablero de control: estado por producto y segmento (`/planillas/tablero-control`).

## Estructura principal

```text
frontend/src/app/
├── app.routes.ts
├── components/
│   ├── login/
│   ├── inicio/
│   ├── cargar/
│   ├── aprobacion/
│   ├── resumen/
│   ├── tablero/
│   └── shared/loading/
├── guards/
│   └── auth.guard.ts
├── models/
│   ├── user.model.ts
│   ├── validation.model.ts
│   └── planilla.model.ts
└── services/
    ├── auth.service.ts
    └── validation.service.ts
```

## Configuracion del frontend

### angular.json

- Define un unico proyecto SPA llamado `sipro-angular-frontend`.
- El build usa el builder `@angular-devkit/build-angular:application` con `src/main.ts` como entrypoint.
- La salida queda en `dist/sipro-angular-frontend`.
- `development` desactiva optimizaciones y deja sourcemaps para depuracion local.
- `production` aplica `outputHashing` y mantiene budgets para vigilar el peso inicial y los estilos por componente.

### package.json

- `npm start`: levanta `ng serve` en el puerto 4200 usando el proxy local.
- `npm run build`: genera el build de produccion.
- `npm run build:dev`: compila con configuracion de desarrollo para validaciones rapidas.
- `npm test`: ejecuta Karma/Jasmine.

### proxy.conf.json

- Redirige `/api` al backend local en `http://localhost:8080`.
- Redirige `/images` al mismo backend para recursos servidos por Spring Boot.
- `changeOrigin: true` ayuda a evitar problemas de cabeceras cuando el dev server actua como proxy.

### Environments

- `src/environments/environment.ts`: configuracion de desarrollo con `production: false`.
- `src/environments/environment.prod.ts`: configuracion de produccion con `production: true`.
- Ambos usan `apiUrl: '/api'` para mantener el frontend desacoplado de una URL fija y delegar el enrutamiento al proxy o al gateway del ambiente.

### TypeScript y compilacion

- `tsconfig.json` activa modo estricto y varias validaciones extra como `noImplicitOverride`, `noImplicitReturns` y `noFallthroughCasesInSwitch`.
- `tsconfig.app.json` compila la app principal tomando `src/main.ts` como archivo raiz.
- `tsconfig.spec.json` agrega tipos de Jasmine para pruebas unitarias.
- `strictTemplates` sigue en `false`, lo que reduce friccion en plantillas existentes pero deja menos chequeos del compilador Angular.

## Notas operativas

- Los guards de ruta distinguen autenticacion general, permiso de carga, permiso de aprobacion y modulos administrativos (resumen/tablero).
- El frontend no usa NgRx; el estado actual se resuelve con RxJS y modelos tipados.
- El login ya no pide usuario/clave local; toda autenticacion interactiva se hace contra Entra ID.
- El resumen consolidado y la consolidacion manual dependen de que el backend este arriba y el periodo exista en la tabla de consolidaciones.
- El build de produccion actual puede mostrar advertencias por presupuesto de SCSS en algunos componentes; no bloquea la generacion del dist.

## Referencias

- [SECURITY.md](SECURITY.md)