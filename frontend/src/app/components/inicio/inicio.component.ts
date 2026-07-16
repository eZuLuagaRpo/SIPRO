import { Component, DestroyRef, OnDestroy, OnInit, inject } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../services/auth.service';
import { ValidationService } from '../../services/validation.service';
import { User, UsuarioPermisos } from '../../models/user.model';
import { ResumenCargas, CargasPendientes, ProductoPendiente, AprobacionesPendientes, MesPendienteAprobacion } from '../../models/validation.model';
import { forkJoin } from 'rxjs';

/**
 * Pantalla inicial que resume el estado operativo del usuario y expone accesos a carga y aprobación.
 */
@Component({
  selector: 'app-inicio',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './inicio.component.html',
  styleUrls: ['./inicio.component.scss']
})
export class InicioComponent implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  /** Nombres de mes usados por updateDateTime() para el reloj del encabezado. */
  private readonly MESES_CONSOLIDACION = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
    'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];

  currentUser: User | null = null;
  permisos: UsuarioPermisos | null = null;
  currentDateTime = '';
  currentIp = '127.0.0.1';
  sidebarOpen = false;
  subMenuOpen = false;

  // Resumen de cargas
  resumenCargas: ResumenCargas | null = null;
  resumenCargando = true;

  // — Cargas pendientes (cargador, mes anterior) —
  cargasPendientes: CargasPendientes | null = null;
  cargasPendientesCargando = true;

  // — Aprobaciones pendientes (líder, últimos 3 meses) —
  aprobacionesPendientes: AprobacionesPendientes | null = null;
  aprobacionesCargando = true;
  aprobacionesMesIdx = 0; // 0=más reciente ... 2=más antiguo

  // Calendario visual
  calMesNombre = '';
  calMesAnio = 0;
  calDias: { dia: number; esUltimo: boolean; esMarcado: boolean; esVacio: boolean }[] = [];
  calWeekdays = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];
  private dateTimeInterval: ReturnType<typeof setInterval> | null = null;

  /** Determina si el usuario es líder/aprobador (para decidir qué vista mostrar) */
  get esAprobador(): boolean {
    if (this.esAdmin) {
      return false;
    }

    return this.puedeAprobar;
  }

  get userName(): string {
    if (this.currentUser) {
      const nombres = this.currentUser.nombres || '';
      const apellidos = this.currentUser.apellidos || '';
      return `${nombres} ${apellidos}`.trim() || this.currentUser.name || 'Usuario';
    }
    return 'Usuario';
  }

  /** Verifica si el usuario puede acceder al módulo de carga */
  get puedeCargar(): boolean {
    return this.authService.puedeAccederCargaManual();
  }

  /** Verifica si el usuario puede acceder al módulo de aprobación */
  get puedeAprobar(): boolean {
    return this.authService.puedeAccederAprobacionManual();
  }

  get puedeVerResumen(): boolean {
    return this.authService.puedeAccederResumenConsolidado();
  }

  get puedeVerAdmin(): boolean {
    return this.authService.puedeAccederPanelAdmin();
  }

  /** Título dinámico de la segunda card según el rol del usuario */
  get tituloSegundaCard(): string {
    if (this.esAdmin) {
      return 'Panel de administrador';
    }

    return this.puedeAprobar ? 'Aprobaciones Pendientes' : 'Cargas Pendientes';
  }

  /** Título dinámico de la card de resumen según el rol */
  get tituloResumenCard(): string {
    if (this.esAdmin) {
      return 'Resumen consolidado';
    }

    return this.esAprobador ? 'Resumen de Aprobaciones' : 'Resumen de Cargas';
  }

  /** Label dinámico para la última actividad según el rol */
  get labelUltimaActividad(): string {
    return this.esAprobador ? 'Última aprobación:' : 'Última carga:';
  }

  /** Tooltip dinámico para el enlace de carga */
  get tooltipCargar(): string {
    return this.puedeCargar
      ? 'Acceder al módulo de carga de archivos manuales'
      : 'No tiene permisos de carga asignados. Contacte al administrador para solicitar acceso.';
  }

  /** Tooltip dinámico para el enlace de aprobación */
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

  get puedeVerParametros(): boolean {
    return this.authService.puedeAdministrar();
  }

  get tooltipParametros(): string {
    return this.puedeVerParametros
      ? 'Acceder al cambio de parámetros'
      : 'El cambio de parámetros solo está habilitado para perfiles administrativos.';
  }

  get tooltipModuloNoDisponible(): string {
    return 'Este módulo está bloqueado para su perfil actual.';
  }

  constructor(
    private authService: AuthService,
    private validationService: ValidationService,
    private router: Router
  ) { }

  ngOnInit() {
    this.authService.currentUser$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(user => {
      this.currentUser = user;
      this.permisos = this.authService.getPermisos();
      if (!user) {
        return;
      }
      if (this.esAdmin) {
        this.resumenCargando = false;
        this.cargasPendientesCargando = false;
        this.aprobacionesCargando = false;
        return;
      }
      if (this.esAprobador) {
        // Aprobador: una sola llamada, misma fuente que /aprobacion
        this.cargarDatosAprobador();
      } else {
        this.cargarResumen();
        this.cargarCargasPendientes();
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

  /**
   * Carga resumen y pendientes del aprobador usando el líder asignado en la planilla.
   */
  private cargarDatosAprobador() {
    this.resumenCargando = true;
    this.aprobacionesCargando = true;

    const idUsuario = this.currentUser?.idUsuario;
    if (!idUsuario) {
      this.resumenCargas = null;
      this.resumenCargando = false;
      this.aprobacionesPendientes = null;
      this.construirCalendarioMes(new Date().getFullYear(), new Date().getMonth(), false);
      this.aprobacionesCargando = false;
      return;
    }

    const resumen$ = this.validationService.obtenerResumenAprobador(Number(idUsuario));
    const aprobaciones$ = this.validationService.obtenerAprobacionesPendientes(Number(idUsuario));

    forkJoin({ resumen: resumen$, aprobaciones: aprobaciones$ }).subscribe({
      next: ({ resumen, aprobaciones }) => {
        // ── Resumen de Aprobaciones ──
        this.resumenCargas = resumen;
        this.resumenCargando = false;

        // ── Aprobaciones Pendientes (últimos 3 meses) ──
        this.aprobacionesPendientes = aprobaciones;
        this.aprobacionesMesIdx = this.obtenerMesInicialAprobador(aprobaciones);
        this.actualizarCalendarioAprobador();
        this.aprobacionesCargando = false;
      },
      error: () => {
        this.resumenCargas = null;
        this.resumenCargando = false;
        this.aprobacionesPendientes = null;
        this.construirCalendarioMes(new Date().getFullYear(), new Date().getMonth(), false);
        this.aprobacionesCargando = false;
      }
    });
  }

  /** Carga el resumen para cargador (correo del usuario que carga) */
  private cargarResumen() {
    const correo = this.currentUser?.correo;
    if (!correo) {
      this.resumenCargando = false;
      return;
    }
    this.resumenCargando = true;
    this.validationService.obtenerResumenCargas(correo).subscribe({
      next: (resumen) => {
        this.resumenCargas = resumen;
        this.resumenCargando = false;
      },
      error: () => {
        this.resumenCargas = null;
        this.resumenCargando = false;
      }
    });
  }

  private cargarCargasPendientes() {
    const idUsuario = this.currentUser?.idUsuario;
    if (!idUsuario) {
      this.cargasPendientesCargando = false;
      this.construirCalendarioMes(new Date().getFullYear(), new Date().getMonth() - 1, false);
      return;
    }
    this.cargasPendientesCargando = true;
    this.validationService.obtenerCargasPendientes(Number(idUsuario)).subscribe({
      next: (resp) => {
        this.cargasPendientes = resp;
        const fecha = new Date(resp.fechaMesAnterior + 'T00:00:00');
        const hayPendientes = resp.productosPendientes && resp.productosPendientes.length > 0;
        this.construirCalendarioMes(fecha.getFullYear(), fecha.getMonth(), hayPendientes);
        this.calMesNombre = resp.nombreMesAnterior;
        this.calMesAnio = fecha.getFullYear();
        this.cargasPendientesCargando = false;
      },
      error: () => {
        this.cargasPendientes = null;
        const hoy = new Date();
        this.construirCalendarioMes(hoy.getFullYear(), hoy.getMonth() - 1, false);
        this.cargasPendientesCargando = false;
      }
    });
  }

  private obtenerMesInicialAprobador(aprobaciones: AprobacionesPendientes | null): number {
    const meses = aprobaciones?.meses ?? [];
    const primerMesConPendientes = meses.findIndex(mes => (mes.productosPendientes?.length ?? 0) > 0);
    return primerMesConPendientes >= 0 ? primerMesConPendientes : 0;
  }

  /** Navegación del calendario del aprobador: mes anterior */
  calNavPrev() {
    if (!this.aprobacionesPendientes) return;
    if (this.aprobacionesMesIdx < this.aprobacionesPendientes.meses.length - 1) {
      this.aprobacionesMesIdx++;
      this.actualizarCalendarioAprobador();
    }
  }

  /** Navegación del calendario del aprobador: mes siguiente */
  calNavNext() {
    if (this.aprobacionesMesIdx > 0) {
      this.aprobacionesMesIdx--;
      this.actualizarCalendarioAprobador();
    }
  }

  get puedeNavPrev(): boolean {
    return !!this.aprobacionesPendientes &&
      this.aprobacionesMesIdx < (this.aprobacionesPendientes.meses.length - 1);
  }

  get puedeNavNext(): boolean {
    return this.aprobacionesMesIdx > 0;
  }

  /** Reconstruye el calendario para el mes seleccionado del aprobador */
  private actualizarCalendarioAprobador() {
    const meses = this.aprobacionesPendientes?.meses;
    if (!meses || meses.length === 0) return;
    const mes = meses[this.aprobacionesMesIdx];
    const fecha = new Date(mes.fechaUltimoDia + 'T00:00:00');
    const hayPendientes = mes.productosPendientes && mes.productosPendientes.length > 0;
    this.calMesNombre = mes.nombreMes;
    this.calMesAnio = mes.anio;
    this.construirCalendarioMes(fecha.getFullYear(), fecha.getMonth(), hayPendientes);
  }

  /** Genera el grid de días de un mes dado, marcando el último día si hayPendientes */
  private construirCalendarioMes(anio: number, mes: number, marcarUltimoDia: boolean) {
    const meses = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
      'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
    // Ajustar si mes es -1 (enero - 1 = diciembre del año anterior)
    if (mes < 0) { mes = 11; anio--; }
    this.calMesNombre = meses[mes];
    this.calMesAnio = anio;

    const diasEnMes = new Date(anio, mes + 1, 0).getDate();
    let primerDiaSemana = new Date(anio, mes, 1).getDay(); // 0=dom
    primerDiaSemana = primerDiaSemana === 0 ? 6 : primerDiaSemana - 1; // 0=lun

    this.calDias = [];
    for (let i = 0; i < primerDiaSemana; i++) {
      this.calDias.push({ dia: 0, esUltimo: false, esMarcado: false, esVacio: true });
    }
    for (let d = 1; d <= diasEnMes; d++) {
      const esUltimo = d === diasEnMes;
      this.calDias.push({
        dia: d,
        esUltimo,
        esMarcado: esUltimo && marcarUltimoDia,
        esVacio: false
      });
    }
  }

  // =================== Getters para la vista (Cargador) ===================

  /** Productos pendientes truncados a 4 para mostrar en la card */
  get productosPendientesTop4(): ProductoPendiente[] {
    return this.cargasPendientes?.productosPendientes?.slice(0, 4) || [];
  }

  get totalProductosPendientes(): number {
    return this.cargasPendientes?.productosPendientes?.length || 0;
  }

  get nombreMesPendiente(): string {
    return this.cargasPendientes?.nombreMesAnterior || '';
  }

  /** Cargador: ¿está al día? (sin productos pendientes) */
  get cargadorAlDia(): boolean {
    return !this.cargasPendientesCargando && this.cargasPendientes != null && this.totalProductosPendientes === 0;
  }

  // =================== Getters para la vista (Aprobador) ===================

  /** Mes actual del aprobador */
  get mesAprobadorActual(): MesPendienteAprobacion | null {
    const meses = this.aprobacionesPendientes?.meses;
    if (!meses || meses.length === 0) return null;
    return meses[this.aprobacionesMesIdx] || null;
  }

  /** Productos del mes seleccionado del aprobador (máx 4) */
  get aprobacionesTop4(): { idPlanilla: number; idProducto: number; tituloProducto: string }[] {
    return this.mesAprobadorActual?.productosPendientes?.slice(0, 4) || [];
  }

  get totalAprobacionesMes(): number {
    return this.mesAprobadorActual?.productosPendientes?.length || 0;
  }

  /** Aprobador: ¿está al día? (ningún mes tiene pendientes) */
  get aprobadorAlDia(): boolean {
    if (this.aprobacionesCargando || !this.aprobacionesPendientes) return false;
    return this.aprobacionesPendientes.meses.every(m => !m.productosPendientes || m.productosPendientes.length === 0);
  }

  /** ¿El mes actual del aprobador tiene pendientes? */
  get mesActualTienePendientes(): boolean {
    return this.totalAprobacionesMes > 0;
  }

  /**
   * Convierte una fecha ISO a texto relativo ("Hace 2 min", "Hace 3 días", etc.)
   */
  get tiempoDesdeUltimaCarga(): string {
    if (!this.resumenCargas?.ultimaCarga) return this.esAprobador ? 'Sin aprobaciones' : 'Sin cargas';

    const ahora = new Date();
    const fecha = new Date(this.resumenCargas.ultimaCarga);
    const diffMs = ahora.getTime() - fecha.getTime();
    const diffSeg = Math.floor(diffMs / 1000);
    const diffMin = Math.floor(diffSeg / 60);
    const diffHoras = Math.floor(diffMin / 60);
    const diffDias = Math.floor(diffHoras / 24);
    const diffMeses = Math.floor(diffDias / 30);
    const diffAnios = Math.floor(diffDias / 365);

    if (diffSeg < 60) return 'Hace un momento';
    if (diffMin < 60) return `Hace ${diffMin} min`;
    if (diffHoras < 24) return `Hace ${diffHoras} hora${diffHoras > 1 ? 's' : ''}`;
    if (diffDias < 30) return `Hace ${diffDias} día${diffDias > 1 ? 's' : ''}`;
    if (diffMeses < 12) return `Hace ${diffMeses} mes${diffMeses > 1 ? 'es' : ''}`;
    return `Hace ${diffAnios} año${diffAnios > 1 ? 's' : ''}`;
  }

  private fetchPublicIP() {
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

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  toggleSidebar() {
    this.sidebarOpen = !this.sidebarOpen;
  }

  toggleSubMenu() {
    this.subMenuOpen = !this.subMenuOpen;
  }

  /**
   * Navega a /cargar solo si el usuario tiene permiso.
   * Si no tiene permiso, no navega (el enlace ya está deshabilitado visualmente).
   */
  navigateToCargar() {
    if (this.puedeCargar) {
      this.router.navigate(['/cargar']);
    }
  }

  /**
   * Navega a /aprobacion solo si el usuario tiene permiso.
   */
  navigateToAprobacion() {
    if (this.puedeAprobar) {
      this.router.navigate(['/aprobacion']);
    }
  }

  /**
   * Maneja click en enlaces deshabilitados.
   * Previene la navegación y muestra feedback si no tiene acceso.
   */
  onDisabledLinkClick(event: Event, modulo: string) {
    event.preventDefault();
    event.stopPropagation();
  }

  /** Indica si el usuario actual es admin (visible para el template). */
  get esAdmin(): boolean {
    return this.authService.puedeAdministrar();
  }

  private updateDateTime() {
    const now = new Date();
    const days = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];

    const dayName = days[now.getDay()];
    const day = now.getDate();
    const monthName = this.MESES_CONSOLIDACION[now.getMonth()];
    const year = now.getFullYear();
    let hours = now.getHours();
    const minutes = now.getMinutes().toString().padStart(2, '0');
    const ampm = hours >= 12 ? 'p.m.' : 'a.m.';
    hours = hours % 12 || 12;

    this.currentDateTime = `${dayName}, ${day} de ${monthName} de ${year}, ${hours}:${minutes} ${ampm}`;
  }
}