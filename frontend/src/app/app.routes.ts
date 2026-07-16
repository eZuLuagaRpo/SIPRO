import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { InicioComponent } from './components/inicio/inicio.component';
import { CargarComponent } from './components/cargar/cargar.component';
import { AprobacionComponent } from './components/aprobacion/aprobacion.component';
import { ResumenComponent } from './components/resumen/resumen.component';
import { authGuard, cargarGuard, aprobacionGuard, resumenGuard, adminGuard, parametrosGuard, tableroGuard } from './guards/auth.guard';

/**
 * Configuración de rutas para el esquema standalone usado por el frontend Angular.
 */
export const routes: Routes = [
    { path: '', redirectTo: '/login', pathMatch: 'full' },
    { path: 'login', component: LoginComponent },
    {
        path: 'inicio',
        component: InicioComponent,
        canActivate: [authGuard]
    },
    {
        path: 'cargar',
        component: CargarComponent,
        canActivate: [cargarGuard]
    },
    {
        path: 'aprobacion',
        component: AprobacionComponent,
        canActivate: [aprobacionGuard]
    },
    {
        path: 'resumen',
        component: ResumenComponent,
        canActivate: [resumenGuard]
    },
    {
        path: 'admin',
        loadComponent: () => import('./components/admin/admin.component').then(m => m.AdminComponent),
        canActivate: [adminGuard]
    },
    {
        path: 'parametros',
        loadComponent: () => import('./components/parametros/parametros.component').then(m => m.ParametrosComponent),
        canActivate: [parametrosGuard]
    },
    {
        path: 'tablero',
        loadComponent: () => import('./components/tablero/tablero.component').then(m => m.TableroComponent),
        canActivate: [tableroGuard]
    },
    { path: '**', redirectTo: '/login' }
];