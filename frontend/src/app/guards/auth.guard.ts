import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Guard base: verifica que el usuario esté autenticado.
 * Redirige a /login si no lo está.
 */
export const authGuard = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  router.navigate(['/login']);
  return false;
};

/**
 * Guard para la ruta /cargar: requiere autenticación + permiso de carga.
 * Si el usuario no tiene permiso de carga, redirige a /inicio.
 */
export const cargarGuard = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    router.navigate(['/login']);
    return false;
  }

  if (!authService.puedeAccederCargaManual()) {
    router.navigate(['/inicio']);
    return false;
  }

  return true;
};

/**
 * Guard para la ruta /aprobacion: requiere autenticación + permiso de aprobación.
 * Si el usuario no tiene permiso de aprobación, redirige a /inicio.
 */
export const aprobacionGuard = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    router.navigate(['/login']);
    return false;
  }

  if (!authService.puedeAccederAprobacionManual()) {
    router.navigate(['/inicio']);
    return false;
  }

  return true;
};

/**
 * Guard para la ruta /resumen: solo perfil admin.
 */
export const resumenGuard = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    router.navigate(['/login']);
    return false;
  }

  if (!authService.puedeAccederResumenConsolidado()) {
    router.navigate(['/inicio']);
    return false;
  }

  return true;
};

/**
 * Guard para la ruta /admin: requiere autenticación y permisos administrativos.
 */
export const adminGuard = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    router.navigate(['/login']);
    return false;
  }

  if (!authService.puedeAccederPanelAdmin()) {
    router.navigate(['/inicio']);
    return false;
  }

  return true;
};

/**
 * Guard para la ruta /parametros: cualquier perfil administrativo habilitado.
 */
export const parametrosGuard = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    router.navigate(['/login']);
    return false;
  }

  if (!authService.puedeAdministrar()) {
    router.navigate(['/inicio']);
    return false;
  }

  return true;
};

/**
 * Guard para la ruta /tablero: solo perfil administrador funcional.
 */
export const tableroGuard = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    router.navigate(['/login']);
    return false;
  }

  if (!authService.puedeAccederResumenConsolidado()) {
    router.navigate(['/inicio']);
    return false;
  }

  return true;
};