import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { InicioComponent } from './components/inicio/inicio.component';
import { CargarComponent } from './components/cargar/cargar.component';
import { AprobacionComponent } from './components/aprobacion/aprobacion.component';
import { authGuard, cargarGuard, aprobacionGuard } from './guards/auth.guard';

/**
 * Mapa de rutas usado por el esquema con AppRoutingModule.
 */
const routes: Routes = [
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
  { path: '**', redirectTo: '/login' }
];

/**
 * Envuelve la configuración clásica de rutas para el bootstrap basado en módulo.
 */
@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
