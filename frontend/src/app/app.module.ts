import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { LoginComponent } from './components/login/login.component';
import { InicioComponent } from './components/inicio/inicio.component';
import { CargarComponent } from './components/cargar/cargar.component';
import { AprobacionComponent } from './components/aprobacion/aprobacion.component';

/**
 * Módulo de compatibilidad que agrupa el bootstrap y las dependencias base del frontend.
 */
@NgModule({
  declarations: [],
  imports: [
    BrowserModule,
    CommonModule,
    AppRoutingModule,
    HttpClientModule,
    ReactiveFormsModule,
    AppComponent,
    LoginComponent,
    InicioComponent,
    CargarComponent,
    AprobacionComponent
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule { }
