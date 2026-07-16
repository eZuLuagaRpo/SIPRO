# Inicio rapido - validation-service

Guia corta para levantar SIPRO localmente con el backend Spring Boot y el frontend Angular.

## Requisitos

- Java 17 disponible para Gradle.
- Node.js 20+ y npm.
- PostgreSQL accesible para el perfil dev.
- Dependencias frontend instaladas.

## Opcion recomendada desde la raiz

```powershell
.\install-dependencies.bat
.\run-fullstack.bat
```

## Opcion manual

### 1. Levantar backend

```powershell
cd backend
.\gradlew.bat :services:validation-service:bootRun
```

Esperar una linea similar a:

```text
Started ValidationServiceApplication in X.XXX seconds
```

### 2. Levantar frontend

```powershell
cd frontend
npm install
npm start
```

### 3. Abrir la aplicacion

- Login: http://localhost:4200/login
- Inicio: http://localhost:4200/inicio
- Resumen: http://localhost:4200/resumen

## Flujo minimo de verificacion

1. Inicia sesion.
2. Entra a Cargar y verifica catalogos de producto/segmento.
3. Valida un archivo por el flujo async.
4. Entra a Aprobacion si el usuario tiene permisos.
5. Entra a Resumen para consultar un periodo consolidado.

## Comandos utiles

```powershell
# Backend
cd backend
.\gradlew.bat :services:validation-service:compileJava
.\gradlew.bat :services:validation-service:test

# Frontend
cd frontend
npm run build
npm test
```

## Problemas comunes

### Puerto 8080 ocupado

```powershell
Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

### Puerto 4200 ocupado

```powershell
Get-NetTCPConnection -LocalPort 4200 -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

### Backend arriba pero frontend sin datos

- Verifica que npm start este corriendo en frontend.
- Verifica proxy.conf.json.
- Confirma que el backend responde en http://localhost:8080/api/auth/health.

### Error con LZ / truststore

- Revisa backend/services/validation-service/src/main/resources/certificates/README.md.
- Si no vas a usar Impala real, trabaja con la configuracion dev/local correspondiente.

## Documentacion complementaria

- [README.md](README.md)
- [LOGIN_README.md](LOGIN_README.md)
- [../../../README.md](../../../README.md)
