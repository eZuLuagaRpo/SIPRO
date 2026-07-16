import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, DestroyRef, OnDestroy, OnInit, inject } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { LoadingComponent } from '../shared/loading/loading.component';
import { AuthService } from '../../services/auth.service';
import { ValidationService } from '../../services/validation.service';
import {
  ConsolidacionMesDisponible,
  ConsolidacionPeriodoAnual,
  EstadoAprobado,
  EstadoCargado,
  TableroControlFila,
  TableroControlResponse
} from '../../models/validation.model';
import { User, UsuarioPermisos } from '../../models/user.model';

/**
 * Tablero de control: muestra el estado de planillas por producto y segmento en el período seleccionado.
 */
@Component({
  selector: 'app-tablero',
  standalone: true,
  imports: [CommonModule, RouterModule, LoadingComponent],
  templateUrl: './tablero.component.html',
  styleUrls: ['./tablero.component.scss']
})
export class TableroComponent implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  private dateTimeInterval: ReturnType<typeof setInterval> | null = null;

  currentUser: User | null = null;
  permisos: UsuarioPermisos | null = null;
  sidebarOpen = false;
  subMenuOpen = true;
  currentDateTime = '';
  currentIp = '127.0.0.1';
  loading = false;
  errorMessage = '';

  tablero: TableroControlResponse | null = null;
  periodosDisponibles: ConsolidacionPeriodoAnual[] = [];
  selectedYear: number | null = null;
  selectedMonth: number | null = null;

  constructor(
    private authService: AuthService,
    private validationService: ValidationService,
    private router: Router
  ) { }

  ngOnInit(): void {
    this.authService.currentUser$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(user => {
        this.currentUser = user;
        this.permisos = this.authService.getPermisos();

        if (!user) {
          return;
        }

        if (!this.puedeVerResumen) {
          this.router.navigate(['/inicio']);
          return;
        }

        if (!this.tablero && !this.loading) {
          this.cargarTablero();
        }
      });

    this.updateDateTime();
    this.dateTimeInterval = setInterval(() => this.updateDateTime(), 60000);
    this.fetchPublicIP();
  }

  ngOnDestroy(): void {
    if (this.dateTimeInterval !== null) {
      clearInterval(this.dateTimeInterval);
      this.dateTimeInterval = null;
    }
  }

  get userName(): string {
    if (this.currentUser) {
      const nombres = this.currentUser.nombres || '';
      const apellidos = this.currentUser.apellidos || '';
      return `${nombres} ${apellidos}`.trim() || this.currentUser.name || 'Usuario';
    }
    return 'Usuario';
  }

  get puedeCargar(): boolean {
    return this.authService.puedeAccederCargaManual();
  }

  get puedeAprobar(): boolean {
    return this.authService.puedeAccederAprobacionManual();
  }

  get puedeVerResumen(): boolean {
    return this.authService.puedeAccederResumenConsolidado();
  }

  get puedeVerAdmin(): boolean {
    return this.authService.puedeAccederPanelAdmin();
  }

  get puedeVerParametros(): boolean {
    return this.authService.puedeAdministrar();
  }

  get tooltipCargar(): string {
    return this.puedeCargar
      ? 'Acceder al módulo de carga de archivos manuales'
      : 'No tiene permisos de carga asignados. Contacte al administrador para solicitar acceso.';
  }

  get tooltipAprobar(): string {
    return this.puedeAprobar
      ? 'Acceder al módulo de aprobación de archivos manuales'
      : 'No tiene permisos de aprobación asignados. Contacte al administrador para solicitar acceso.';
  }

  get tooltipResumen(): string {
    return this.puedeVerResumen
      ? 'Acceder al resumen consolidado'
      : 'El resumen consolidado solo está habilitado para el perfil administrador.';
  }

  get tooltipTablero(): string {
    return this.puedeVerResumen
      ? 'Acceder al tablero de control'
      : 'El tablero de control solo está habilitado para el perfil administrador.';
  }

  get tooltipAdmin(): string {
    return this.puedeVerAdmin
      ? 'Acceder al panel de administrador'
      : 'El panel de administrador solo está habilitado para el perfil administrador.';
  }

  get tooltipParametros(): string {
    return this.puedeVerParametros
      ? 'Acceder al cambio de parámetros'
      : 'El cambio de parámetros solo está habilitado para perfiles administrativos.';
  }

  get tooltipModuloNoDisponible(): string {
    return 'Este módulo está bloqueado para su perfil actual.';
  }

  get mesesDisponibles(): ConsolidacionMesDisponible[] {
    if (this.selectedYear == null) {
      return [];
    }
    return this.periodosDisponibles.find(periodo => periodo.anio === this.selectedYear)?.meses ?? [];
  }

  get filas(): TableroControlFila[] {
    return this.tablero?.filas ?? [];
  }

  /**
   * Consulta el tablero de control para el período seleccionado o para el período actual.
   */
  cargarTablero(anio?: number, mes?: number): void {
    this.loading = true;
    this.errorMessage = '';

    this.validationService.obtenerTableroControl(anio, mes).subscribe({
      next: (response) => {
        this.tablero = response;
        this.periodosDisponibles = response.periodosDisponibles ?? [];
        this.selectedYear = response.anioSeleccionado;
        this.selectedMonth = response.mesSeleccionado;
        this.loading = false;
      },
      error: (error) => {
        this.tablero = null;
        this.periodosDisponibles = [];
        this.selectedYear = null;
        this.selectedMonth = null;
        this.errorMessage = error.error?.mensaje || error.message || 'No fue posible cargar el tablero de control.';
        this.loading = false;
      }
    });
  }

  selectYear(anio: number): void {
    if (this.selectedYear === anio) {
      return;
    }
    const periodo = this.periodosDisponibles.find(item => item.anio === anio);
    const month = periodo?.meses[periodo.meses.length - 1]?.numero;
    if (month != null) {
      this.cargarTablero(anio, month);
    }
  }

  onYearChange(rawYear: string): void {
    const anio = Number(rawYear);
    if (Number.isInteger(anio)) {
      this.selectYear(anio);
    }
  }

  selectMonth(mes: number): void {
    if (this.selectedYear == null || this.selectedMonth === mes) {
      return;
    }
    this.cargarTablero(this.selectedYear, mes);
  }

  toggleSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
  }

  toggleSubMenu(): void {
    this.subMenuOpen = !this.subMenuOpen;
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  onDisabledLinkClick(event: Event, modulo: string): void {
    event.preventDefault();
    event.stopPropagation();
    this.router.navigate(['/inicio']);
  }

  trackByAnio(index: number, periodo: ConsolidacionPeriodoAnual): number {
    return periodo.anio;
  }

  trackByMes(index: number, mes: ConsolidacionMesDisponible): string {
    return mes.periodo;
  }

  trackByFila(index: number, fila: TableroControlFila): number {
    return fila.idProducto;
  }

  etiquetaCargado(estado: EstadoCargado): string {
    switch (estado) {
      case 'ARCHIVO_CARGADO': return 'Archivo Cargado';
      case 'SIN_DATOS': return 'Sin Datos';
      case 'PENDIENTE_CARGA': return 'Pendiente Carga';
      case 'NO_APLICA': return 'No aplica';
      default: return '—';
    }
  }

  claseCargado(estado: EstadoCargado): string {
    switch (estado) {
      case 'ARCHIVO_CARGADO': return 'ts-cargado';
      case 'SIN_DATOS': return 'ts-sin-datos';
      case 'PENDIENTE_CARGA': return 'ts-pendiente';
      case 'NO_APLICA': return 'ts-no-aplica';
      default: return 'ts-muted';
    }
  }

  etiquetaAprobado(estado: EstadoAprobado): string {
    switch (estado) {
      case 'ARCHIVO_APROBADO': return 'Archivo aprobado';
      case 'APROBACION_SIN_DATOS': return 'Aprobación sin Datos';
      case 'PENDIENTE_APROBACION': return 'Pendiente de aprobación';
      case 'ARCHIVO_RECHAZADO': return 'Archivo rechazado';
      case 'RECHAZO_SIN_DATOS': return 'Rechazo sin Datos';
      case 'NO_APLICA': return 'No aplica';
      default: return '—';
    }
  }

  claseAprobado(estado: EstadoAprobado): string {
    switch (estado) {
      case 'ARCHIVO_APROBADO': return 'ts-aprobado';
      case 'APROBACION_SIN_DATOS': return 'ts-aprobado-sin-datos';
      case 'PENDIENTE_APROBACION': return 'ts-pendiente-aprobacion';
      case 'ARCHIVO_RECHAZADO': return 'ts-rechazado';
      case 'RECHAZO_SIN_DATOS': return 'ts-rechazo-sin-datos';
      case 'NO_APLICA': return 'ts-no-aplica';
      default: return 'ts-muted';
    }
  }

  private fetchPublicIP(): void {
    fetch('https://api.ipify.org?format=json')
      .then(res => res.json())
      .then(data => {
        if (data && typeof data.ip === 'string' && data.ip.trim().length > 0) {
          this.currentIp = data.ip.trim();
        }
      })
      .catch(() => {
        this.currentIp = '127.0.0.1';
      });
  }

  private updateDateTime(): void {
    const now = new Date();
    const days = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
    const months = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
      'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];

    const dayName = days[now.getDay()];
    const day = now.getDate();
    const monthName = months[now.getMonth()];
    const year = now.getFullYear();
    let hours = now.getHours();
    const minutes = now.getMinutes().toString().padStart(2, '0');
    const ampm = hours >= 12 ? 'p. m.' : 'a. m.';
    hours = hours % 12 || 12;

    this.currentDateTime = `${dayName}, ${day} de ${monthName} de ${year}, ${hours}:${minutes} ${ampm}`;
  }
}