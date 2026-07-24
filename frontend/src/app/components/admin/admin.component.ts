import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, DestroyRef, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import {
  AdminArchivoEstado,
  AdminDashboardResponse,
  AdminEstadoPeriodoConsolidacion,
  AdminHistoricoConsolidacion,
  AdminLogItem,
  AdminLogLevel,
  AdminLogScope,
  AdminLogsConfig,
  AdminPanelConfig,
  AdminPeriodoDisponible,
  AdminSqlConfig,
  AdminSqlExecuteResponse,
  AdminSqlOperation
} from '../../models/admin.model';
import { User, UsuarioPermisos } from '../../models/user.model';
import { AuthService } from '../../services/auth.service';
import { AdminService } from '../../services/admin.service';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './admin.component.html',
  styleUrls: ['./admin.component.scss']
})
export class AdminComponent implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  private readonly numberFormatter = new Intl.NumberFormat('es-CO');
  private readonly dateTimeFormatter = new Intl.DateTimeFormat('es-CO', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });

  private dateTimeInterval: ReturnType<typeof setInterval> | null = null;
  private dashboardPollingInterval: ReturnType<typeof setInterval> | null = null;
  private logsPollingInterval: ReturnType<typeof setInterval> | null = null;
  private eliminacionProgressTimeout: ReturnType<typeof setTimeout> | null = null;
  private sqlCopyFeedbackTimeout: ReturnType<typeof setTimeout> | null = null;
  private consolidacionActividadProtegida = false;
  private fase2ModalCerradoPorUsuario = false;
  private readonly defaultSqlOperations: AdminSqlOperation[] = ['SELECT', 'UPDATE', 'INSERT'];

  readonly excelIconPath = '/assets/images/icoExcel.png';
  readonly downloadIconPath = '/assets/images/download.png';
  readonly maxObservacionChars = 300;
  readonly deleteConfirmKeyword = 'Eliminar';
  readonly logTerminalFilters: Array<{ label: string; value: AdminLogLevel }> = [
    { label: 'TODOS', value: 'ALL' },
    { label: 'ERROR', value: 'ERROR' },
    { label: 'WARN', value: 'WARN' },
    { label: 'INFO', value: 'INFO' },
    { label: 'DEBUG', value: 'DEBUG' }
  ];

  currentUser: User | null = null;
  permisos: UsuarioPermisos | null = null;
  currentDateTime = '';
  currentIp = '127.0.0.1';
  sidebarOpen = false;
  subMenuOpen = true;

  loadingDashboard = true;
  dashboardError = '';
  dashboardFeedback = '';
  dashboardFeedbackType: 'success' | 'error' = 'success';
  dashboard: AdminDashboardResponse | null = null;
  vistaArchivoActiva: 'pendientes' | 'consolidados' = 'pendientes';
  historicoExpandido = false;

  selectedYear: number | null = null;
  selectedMonth: number | null = null;
  observacionConsolidacion = '';
  confirmacionConsolidacion = false;
  confirmacionConsolidacionParcial = false;
  consolidacionEnCurso = false;
  consolidacionModalVisible = false;
  consolidacionRespuesta = '';
  consolidacionError = '';
  eliminacionModalVisible = false;
  eliminacionConsolidacionId: number | null = null;
  eliminacionConsolidacionPeriodo = '';
  eliminacionMotivo = '';
  eliminacionConfirmacionTexto = '';
  eliminacionResponsabilidadConfirmada = false;
  eliminacionEnCurso = false;
  eliminacionError = '';
  eliminacionProgressVisible = false;

  readonly sqlOperations: AdminSqlOperation[] = ['SELECT', 'UPDATE', 'INSERT', 'INSERT_OVERWRITE'];
  readonly maxSqlChars = 50000;
  sqlOperacion: AdminSqlOperation = 'SELECT';
  sqlTexto = [
    'SELECT *',
    'FROM sipro_parametros_unico',
    'WHERE activo = true',
    'ORDER BY id_parametro ASC'
  ].join('\n');
  sqlJustificacion = '';
  sqlLoading = false;
  sqlResultado: AdminSqlExecuteResponse | null = null;
  sqlError = '';
  showSqlTablesMenu = false;
  sqlTablesCopyFeedback = '';
  sqlTablesCopyFeedbackType: 'success' | 'error' = 'success';

  readonly logLevels: AdminLogLevel[] = ['ALL', 'ERROR', 'WARN', 'INFO', 'DEBUG'];
  logLevel: AdminLogLevel = 'ALL';
  logEntries: AdminLogItem[] = [];
  logAfterId = 0;
  consolidacionLogEntries: AdminLogItem[] = [];
  consolidacionLogAfterId = 0;
  logsLoading = false;
  logsPaused = false;
  logsError = '';
  logsFromDate = '';
  logsToDate = '';

  constructor(
    private authService: AuthService,
    private adminService: AdminService,
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

        if (!this.authService.puedeAccederPanelAdmin()) {
          this.router.navigate(['/inicio']);
          return;
        }

        if (!this.dashboard && !this.loadingDashboard) {
          return;
        }

        if (!this.dashboard) {
          this.cargarDashboard();
        }
      });

    this.updateDateTime();
    this.dateTimeInterval = setInterval(() => this.updateDateTime(), 60000);
    this.fetchPublicIP();
  }

  ngOnDestroy(): void {
    if (this.dateTimeInterval) {
      clearInterval(this.dateTimeInterval);
      this.dateTimeInterval = null;
    }

    this.limpiarTemporizadorEliminacion();
    this.limpiarFeedbackCopiaSql();
    this.detenerPollingDashboard();
    this.detenerPollingLogs();
    this.finalizarActividadProtegidaConsolidacion();
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

  /** Soporte Técnico (id_rol=3): ve la consola SQL y los logs técnicos de este panel. */
  get esAdminTecnico(): boolean {
    return this.authService.esAdminTecnico();
  }

  /** Admin_Permisos (id_rol=6): único perfil que puede ejecutar la consolidación manual. */
  get esAdminPermisos(): boolean {
    return this.authService.puedeEjecutarConsolidacionManual();
  }

  get tooltipCargar(): string {
    return this.puedeCargar
      ? 'Acceder al módulo de carga de archivos manuales'
      : 'La carga manual no está habilitada para el perfil administrador.';
  }

  get tooltipAprobar(): string {
    return this.puedeAprobar
      ? 'Acceder al módulo de aprobación de archivos manuales'
      : 'La aprobación manual no está habilitada para el perfil administrador.';
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

  get yearsDisponibles(): number[] {
    const years = new Set<number>();
    for (const periodo of this.dashboard?.periodosDisponibles ?? []) {
      years.add(periodo.anio);
    }
    return Array.from(years).sort((a, b) => b - a);
  }

  get monthsDisponibles(): AdminPeriodoDisponible[] {
    return (this.dashboard?.periodosDisponibles ?? [])
      .filter(periodo => periodo.anio === this.selectedYear)
      .sort((a, b) => b.mes - a.mes);
  }

  get periodoSeleccionado(): AdminPeriodoDisponible | null {
    return this.monthsDisponibles.find(periodo => periodo.mes === this.selectedMonth) ?? null;
  }

  get periodoSeleccionadoLabel(): string {
    return this.periodoSeleccionado?.etiqueta ?? 'Sin periodo disponible';
  }

  get archivosPendientesCount(): number {
    return (this.dashboard?.archivosAConsolidar?.length ?? 0) + (this.dashboard?.archivosNoBloqueantes?.length ?? 0);
  }

  get archivosConsolidadosCount(): number {
    return this.dashboard?.archivosConsolidados?.length ?? 0;
  }

  get archivosVisibles(): AdminArchivoEstado[] {
    if (!this.dashboard) {
      return [];
    }
    return this.vistaArchivoActiva === 'pendientes'
      ? [...this.dashboard.archivosAConsolidar, ...this.dashboard.archivosNoBloqueantes]
      : this.dashboard.archivosConsolidados;
  }

  get historico(): AdminHistoricoConsolidacion[] {
    return this.dashboard?.historico ?? [];
  }

  get configuracionPanel(): AdminPanelConfig | null {
    return this.dashboard?.configuracion ?? null;
  }

  get estadoPeriodo(): AdminEstadoPeriodoConsolidacion | null {
    return this.dashboard?.estadoPeriodo ?? null;
  }

  get archivosNoBloqueantes(): AdminArchivoEstado[] {
    return this.dashboard?.archivosNoBloqueantes ?? [];
  }

  get mostrarArchivosNoBloqueantes(): boolean {
    return this.vistaArchivoActiva === 'pendientes' && this.archivosNoBloqueantes.length > 0;
  }

  get requiereConfirmacionConsolidacionParcial(): boolean {
    return ((this.estadoPeriodo?.cantidadPlanillasPendientes ?? 0) + (this.estadoPeriodo?.cantidadPlanillasRechazadas ?? 0)) > 0;
  }

  get alertaNoBloqueanteTexto(): string {
    if (this.estadoPeriodo?.mensajeAdvertenciaOperativa) {
      return this.estadoPeriodo.mensajeAdvertenciaOperativa;
    }

    return this.buildResumenNoBloqueanteTexto();
  }

  get confirmacionConsolidacionParcialTexto(): string {
    return `${this.buildResumenNoBloqueanteTexto()} Confirmo que esta ejecución consolidará únicamente los archivos aprobados.`.trim();
  }

  get sqlConfig(): AdminSqlConfig | null {
    return this.configuracionPanel?.sql ?? null;
  }

  get tablasPermitidas(): string[] {
    return this.sqlConfig?.tablasPermitidas ?? [];
  }

  get logsConfig(): AdminLogsConfig | null {
    return this.configuracionPanel?.logs ?? null;
  }

  get estadoConsolidacionTexto(): string {
    return this.dashboard?.estadoConsolidacion?.estado ?? 'SIN EJECUCIÓN';
  }

  get estadoConsolidacionMensaje(): string {
    return this.dashboard?.estadoConsolidacion?.mensaje ?? 'No hay una consolidación manual en ejecución para el periodo activo.';
  }

  get sqlEscritura(): boolean {
    return this.sqlOperacion === 'UPDATE' || this.sqlOperacion === 'INSERT' || this.sqlOperacion === 'INSERT_OVERWRITE';
  }

  get sqlColumns(): string[] {
    return this.sqlResultado?.columnas ?? [];
  }

  get puedeDescargarResultadoSqlCsv(): boolean {
    return Boolean(
      this.sqlResultado
      && this.sqlResultado.tipoOperacion?.toUpperCase() === 'SELECT'
      && this.sqlColumns.length > 0
    );
  }

  get operacionesSqlDisponibles(): AdminSqlOperation[] {
    const configured = this.sqlConfig?.operacionesHabilitadas ?? [];
    const mapped = configured.filter((operation): operation is AdminSqlOperation =>
      this.sqlOperations.includes(operation as AdminSqlOperation)
    );

    return mapped.length > 0 ? mapped : this.defaultSqlOperations;
  }

  get sqlCharacterCount(): number {
    return this.sqlTexto.length;
  }

  get sqlLineNumbers(): number[] {
    return Array.from({ length: Math.max(1, this.sqlTexto.split('\n').length) }, (_, index) => index + 1);
  }

  get observacionConsolidacionCount(): number {
    return this.observacionConsolidacion.length;
  }

  get puedeEjecutarConsolidacion(): boolean {
    return Boolean(
      this.esAdminPermisos
      && this.periodoSeleccionado
      && !this.periodoSeleccionado.consolidado
      && this.estadoPeriodo?.puedeEjecutarManual
      && this.confirmacionConsolidacion
      && (!this.requiereConfirmacionConsolidacionParcial || this.confirmacionConsolidacionParcial)
      && this.observacionConsolidacion.trim()
      && !this.consolidacionEnCurso
    );
  }

  get puedeConfirmarEliminacionConsolidacion(): boolean {
    return Boolean(
      this.eliminacionConsolidacionId
      && this.eliminacionMotivo.trim().length >= 10
      && this.eliminacionResponsabilidadConfirmada
      && this.eliminacionConfirmacionTexto === this.deleteConfirmKeyword
      && !this.eliminacionEnCurso
    );
  }

  get confirmacionAyudaTexto(): string {
    const estadoPeriodo = this.estadoPeriodo;
    const rangoTexto = this.buildRangoConsolidacionTexto();

    if (this.consolidacionEnCurso) {
      return 'La consolidación está en curso.';
    }

    if (!estadoPeriodo) {
      return 'Cargando disponibilidad del periodo.';
    }

    if (!estadoPeriodo.puedeEjecutarManual) {
      return rangoTexto
        ? `${estadoPeriodo.mensajeDisponibilidad} ${rangoTexto}`
        : estadoPeriodo.mensajeDisponibilidad;
    }

    if (!this.observacionConsolidacion.trim()) {
      return `${rangoTexto} Ingresa el motivo obligatorio.`.trim();
    }

    if (!this.confirmacionConsolidacion) {
      return `${rangoTexto} Debes confirmar la ejecución manual.`.trim();
    }

    if (this.requiereConfirmacionConsolidacionParcial && !this.confirmacionConsolidacionParcial) {
      return `${rangoTexto} ${this.confirmacionConsolidacionParcialTexto}`.trim();
    }

    if (estadoPeriodo.ventanaIgnoradaPorConfiguracion) {
      return `${rangoTexto} Bypass DEV activo: la ventana se ignora temporalmente para esta ejecución.`.trim();
    }

    if (estadoPeriodo.sobrescribeConsolidacionExistente) {
      return `${rangoTexto} Ya existe una consolidación para este periodo.`.trim();
    }

    return `${rangoTexto} Listo para ejecutar.`.trim();
  }

  get sqlInfoTitle(): string {
    return this.sqlEscritura ? 'Cambio controlado' : 'Consulta de lectura';
  }

  get sqlInfoDescription(): string {
    if (this.sqlEscritura) {
      return 'Las operaciones de escritura quedan auditadas con usuario, fecha, módulo y justificación obligatoria.';
    }

    const whereHint = this.sqlConfig?.requiereWhereSelect
      ? 'WHERE obligatorio.'
      : 'WHERE recomendado cuando el volumen del dato es alto.';

    return `La operación es de solo lectura. Límite automático: ${this.sqlConfig?.maxFilasSelect ?? 200} filas. ${whereHint}`;
  }

  get sqlWhereNote(): string {
    return this.sqlConfig?.requiereWhereSelect
      ? 'Toda consulta SELECT debe incluir cláusula WHERE en este ambiente.'
      : `Si no informas LIMIT, el backend aplica ${this.sqlConfig?.maxFilasSelect ?? 200} filas máximas.`;
  }

  get puedeEjecutarSql(): boolean {
    if (this.sqlLoading) return false;
    if (this.sqlEscritura && !this.sqlJustificacion.trim()) return false;
    return true;
  }

  get logsStreamingEnabled(): boolean {
    return this.logsConfig?.streamingHabilitado ?? true;
  }

  get logsDownloadEnabled(): boolean {
    return this.logsConfig?.descargaHabilitada ?? true;
  }

  get logErrorCount(): number {
    return this.visibleLogEntries.filter(log => log.level.toUpperCase() === 'ERROR').length;
  }

  get logsHasDateFilter(): boolean {
    return Boolean(this.logsFromDate || this.logsToDate);
  }

  get logErrorSummaryText(): string {
    return this.logsHasDateFilter
      ? `${this.logErrorCount} errores en el rango seleccionado`
      : `${this.logErrorCount} errores en la vista actual`;
  }

  get logCounterText(): string {
    return this.logsHasDateFilter
      ? `${this.visibleLogEntries.length} entradas • rango seleccionado`
      : `${this.visibleLogEntries.length} entradas • streaming en vivo`;
  }

  get visibleLogEntries(): AdminLogItem[] {
    return this.logEntries.filter(log => this.logMatchesDateRange(log));
  }

  get modalLogEntries(): AdminLogItem[] {
    return this.buildModalTerminalEntries(5);
  }

  get logsEmptyStateText(): string {
    return this.logsHasDateFilter
      ? 'Sin eventos para el filtro actual.'
      : 'Sin eventos capturados todavía.';
  }

  get maxVisibleLogEntries(): number {
    return Math.max(
      this.logsConfig?.maximoConsulta ?? 0,
      this.logsConfig?.limiteConsultaPorDefecto ?? 0,
      500
    );
  }

  get modalConsolidacionTerminada(): boolean {
    return this.consolidacionModalVisible && !this.consolidacionEnCurso;
  }

  get modalConsolidacionEstado(): 'running' | 'success' | 'error' {
    if (!this.modalConsolidacionTerminada) {
      return 'running';
    }

    if (this.consolidacionError || this.dashboard?.estadoConsolidacion?.mensajeError) {
      return 'error';
    }

    if (this.dashboard?.estadoConsolidacion?.terminal && this.dashboard?.estadoConsolidacion?.exito === false) {
      return 'error';
    }

    return 'success';
  }

  get modalConsolidacionTitulo(): string {
    if (!this.modalConsolidacionTerminada) {
      return 'Ejecutando consolidación...';
    }

    if (this.dashboard?.estadoConsolidacion?.fase2EnCurso && this.modalConsolidacionEstado !== 'error') {
      return 'Consolidación completada — publicando archivos bloqueados...';
    }

    return this.modalConsolidacionEstado === 'error'
      ? 'Consolidación finalizada con error'
      : 'Consolidación completada';
  }

  get modalConsolidacionMensaje(): string {
    if (this.dashboard?.estadoConsolidacion?.fase2EnCurso
      && this.modalConsolidacionTerminada
      && this.modalConsolidacionEstado !== 'error') {
      return 'El consolidado y el CREFFSOS ya están disponibles. Los archivos bloqueados se están publicando en segundo plano. Puedes cerrar esta ventana y navegar libremente.';
    }

    return this.consolidacionError
      || this.dashboard?.estadoConsolidacion?.mensajeError
      || this.dashboard?.estadoConsolidacion?.mensaje
      || this.consolidacionRespuesta
      || 'Inicializando validación de archivos y construcción del consolidado...';
  }

  get statusBarClass(): string {
    const estado = this.dashboard?.estadoConsolidacion;
    const estadoPeriodo = this.estadoPeriodo;

    if (this.consolidacionError || estado?.mensajeError) {
      return 'error';
    }

    if (!estado) {
      if (estadoPeriodo?.fechaUltimaConsolidacionExitosa) {
        return 'success';
      }
      if (this.requiereConfirmacionConsolidacionParcial) {
        return 'warning';
      }
      if (estadoPeriodo?.ventanaIgnoradaPorConfiguracion) {
        return 'warning';
      }
      if (estadoPeriodo?.estadoVentana === 'ABIERTA') {
        return 'warning';
      }
      if (estadoPeriodo?.estadoVentana === 'CERRADA_EN_RANGO') {
        return 'success';
      }
      if (estadoPeriodo?.estadoVentana === 'EXPIRADA') {
        return 'error';
      }
      return 'neutral';
    }
    if (estado.terminal === false) {
      return 'warning';
    }
    if (estado.exito) {
      return 'success';
    }
    if (estado.mensajeError) {
      return 'error';
    }
    return 'neutral';
  }

  get statusBarMessage(): string {
    const estado = this.dashboard?.estadoConsolidacion;
    const estadoPeriodo = this.estadoPeriodo;

    if (estado && estado.terminal === false) {
      return estado.mensaje || `Consolidación en progreso para ${this.periodoSeleccionadoLabel}`;
    }

    if (estadoPeriodo?.mensajeEstado) {
      return estadoPeriodo.mensajeEstado;
    }

    if (!estado) {
      return `Estado: Sin ejecución registrada - ${this.periodoSeleccionadoLabel}`;
    }

    const fecha = this.formatDateTime(estado.fechaHoraFin || estado.fechaHoraInicio);
    const registros = this.formatNumber(estado.cantidadRegistrosConsolidados);
    return `Estado: ${this.estadoConsolidacionTexto} - ${fecha} - ${registros} registros`;
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  toggleSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
  }

  toggleSubMenu(): void {
    this.subMenuOpen = !this.subMenuOpen;
  }

  toggleSqlTablesMenu(): void {
    this.showSqlTablesMenu = !this.showSqlTablesMenu;
    if (!this.showSqlTablesMenu) {
      this.limpiarFeedbackCopiaSql();
    }
  }

  async copiarTablaPermitida(table: string): Promise<void> {
    const copiado = await this.copiarAlPortapapeles(table);
    this.mostrarFeedbackCopiaSql(
      copiado ? `Tabla copiada: ${table}` : 'No fue posible copiar el nombre de la tabla.',
      copiado ? 'success' : 'error'
    );
  }

  async copiarTablasPermitidas(): Promise<void> {
    if (!this.tablasPermitidas.length) {
      this.mostrarFeedbackCopiaSql('No hay tablas permitidas configuradas para copiar.', 'error');
      return;
    }

    const copiado = await this.copiarAlPortapapeles(this.tablasPermitidas.join('\n'));
    this.mostrarFeedbackCopiaSql(
      copiado ? 'Lista completa copiada al portapapeles.' : 'No fue posible copiar la lista de tablas.',
      copiado ? 'success' : 'error'
    );
  }

  onYearChange(year: number | null): void {
    this.selectedYear = year;
    const month = this.monthsDisponibles[0];
    this.selectedMonth = month?.mes ?? null;
    this.cargarDashboard(this.periodoSeleccionado?.valor ?? undefined, true);
  }

  onMonthChange(month: number | null): void {
    this.selectedMonth = month;
    this.cargarDashboard(this.periodoSeleccionado?.valor ?? undefined, true);
  }

  refrescarDashboard(): void {
    this.cargarDashboard(this.periodoSeleccionado?.valor ?? undefined);
  }

  toggleHistorico(): void {
    this.historicoExpandido = !this.historicoExpandido;
  }

  clearSql(): void {
    this.sqlTexto = '';
    this.sqlResultado = null;
    this.sqlError = '';
  }

  aplicarFiltroLogs(): void {
    this.logsError = '';
  }

  scrollLogsToEnd(): void {
    setTimeout(() => {
      const terminalBody = document.getElementById('adminLogsBody');
      if (terminalBody) {
        terminalBody.scrollTop = terminalBody.scrollHeight;
      }
    });
  }

  getHistoricoRuta(item: AdminHistoricoConsolidacion): string {
    return item.detalleSalida || item.observacion || item.mensajeError || 'Sin ruta disponible';
  }

  puedeEliminarHistorico(item: AdminHistoricoConsolidacion): boolean {
    const estado = (item.estado || '').toUpperCase();
    return estado === 'COMPLETADO' || estado === 'COMPLETADO_CON_ADVERTENCIAS';
  }

  getSqlOperationLabel(operation: AdminSqlOperation): string {
    if (operation === 'INSERT') {
      return 'INSERT INTO';
    }

    if (operation === 'INSERT_OVERWRITE') {
      return 'INSERT OVERWRITE';
    }

    return operation;
  }

  getTerminalPillClass(level: AdminLogLevel): string {
    switch (level) {
      case 'ERROR':
        return 'terminal-pill--error';
      case 'WARN':
        return 'terminal-pill--warn';
      case 'INFO':
        return 'terminal-pill--info';
      case 'DEBUG':
        return 'terminal-pill--debug';
      default:
        return 'terminal-pill--all';
    }
  }

  ejecutarConsolidacion(): void {
    const periodo = this.periodoSeleccionado?.valor;
    if (!periodo) {
      this.consolidacionError = 'No hay un periodo válido para consolidar.';
      this.consolidacionRespuesta = '';
      return;
    }

    if (!this.confirmacionConsolidacion) {
      this.consolidacionError = 'Debes confirmar la ejecución manual antes de continuar.';
      this.consolidacionRespuesta = '';
      return;
    }

    if (this.requiereConfirmacionConsolidacionParcial && !this.confirmacionConsolidacionParcial) {
      this.consolidacionError = 'Confirma que existen pendientes o rechazados y que solo se consolidará lo aprobado.';
      this.consolidacionRespuesta = '';
      return;
    }

    if (!this.observacionConsolidacion.trim()) {
      this.consolidacionError = 'La observación es obligatoria para registrar el motivo de la consolidación manual.';
      this.consolidacionRespuesta = '';
      return;
    }

    this.consolidacionEnCurso = true;
    this.consolidacionModalVisible = true;
    this.consolidacionError = '';
    this.consolidacionRespuesta = '';
    this.dashboardFeedback = '';
    this.reiniciarLogsConsolidacion();
    this.iniciarActividadProtegidaConsolidacion();

    this.adminService.ejecutarConsolidacionManual(periodo, this.observacionConsolidacion.trim()).subscribe({
      next: response => {
        this.consolidacionRespuesta = response.mensaje;
        this.consolidacionError = '';
        this.dashboard = {
          ...(this.dashboard as AdminDashboardResponse),
          estadoConsolidacion: response
        };

        if (response.terminal) {
          this.consolidacionEnCurso = false;
          this.cargarDashboard(periodo, true);
          this.finalizarActividadProtegidaConsolidacion();
          return;
        }

        this.consultarLogs(true, 'CONSOLIDACION');
        this.iniciarPollingDashboard(periodo);
      },
      error: err => {
        const backendMessage = err?.error?.mensaje || err?.error?.message || 'No fue posible iniciar la consolidación manual.';
        this.consolidacionError = backendMessage;
        this.consolidacionRespuesta = '';
        this.consolidacionEnCurso = false;
        this.consolidacionModalVisible = true;
        this.finalizarActividadProtegidaConsolidacion();
      }
    });
  }

  cerrarModalConsolidacion(): void {
    if (this.consolidacionEnCurso) {
      return;
    }

    if (this.dashboard?.estadoConsolidacion?.fase2EnCurso) {
      this.fase2ModalCerradoPorUsuario = true;
    }

    this.cargarDashboard(this.periodoSeleccionado?.valor ?? undefined, true);
    this.consolidacionModalVisible = false;
    this.confirmacionConsolidacion = false;
    this.confirmacionConsolidacionParcial = false;
    this.observacionConsolidacion = '';
    this.consolidacionRespuesta = '';
    this.consolidacionError = '';
  }

  abrirModalEliminarConsolidacion(item: AdminHistoricoConsolidacion): void {
    if (!this.puedeEliminarHistorico(item)) {
      return;
    }

    this.dashboardFeedback = '';
    this.eliminacionConsolidacionId = item.idConsolidacion;
    this.eliminacionConsolidacionPeriodo = item.periodo || 'Sin periodo';
    this.eliminacionModalVisible = true;
    this.eliminacionMotivo = '';
    this.eliminacionConfirmacionTexto = '';
    this.eliminacionResponsabilidadConfirmada = false;
    this.eliminacionEnCurso = false;
    this.eliminacionError = '';
    this.eliminacionProgressVisible = false;
    this.limpiarTemporizadorEliminacion();
  }

  cerrarModalEliminarConsolidacion(): void {
    if (this.eliminacionEnCurso) {
      return;
    }

    this.eliminacionModalVisible = false;
    this.reiniciarEstadoEliminacion();
  }

  confirmarEliminacionConsolidacion(): void {
    const idConsolidacion = this.eliminacionConsolidacionId;
    if (!idConsolidacion) {
      this.eliminacionError = 'No hay una consolidación válida para eliminar.';
      return;
    }

    if (!this.puedeConfirmarEliminacionConsolidacion) {
      this.eliminacionError = 'Completa el motivo, confirma la responsabilidad y escribe exactamente "Eliminar".';
      return;
    }

    const periodoActual = this.periodoSeleccionado?.valor ?? undefined;
    const periodoEliminado = this.eliminacionConsolidacionPeriodo;

    this.eliminacionEnCurso = true;
    this.eliminacionError = '';
    this.dashboardFeedback = '';
    this.iniciarIndicadorEliminacion();

    this.adminService.eliminarConsolidacion(idConsolidacion, {
      motivo: this.eliminacionMotivo.trim(),
      confirmacion: this.eliminacionConfirmacionTexto
    }).subscribe({
      next: response => {
        this.eliminacionEnCurso = false;
        this.limpiarTemporizadorEliminacion();
        this.eliminacionModalVisible = false;
        this.vistaArchivoActiva = 'pendientes';
        this.dashboardFeedbackType = response.mensaje?.includes('advertencias') ? 'error' : 'success';
        this.dashboardFeedback = this.dashboardFeedbackType === 'success'
          ? `Consolidación de ${periodoEliminado} eliminada correctamente.`
          : response.mensaje;
        this.reiniciarEstadoEliminacion();
        this.cargarDashboard(periodoActual, true);
      },
      error: err => {
        this.eliminacionEnCurso = false;
        this.limpiarTemporizadorEliminacion();
        this.eliminacionError = err?.error?.mensaje
          || err?.error?.message
          || 'No fue posible eliminar la consolidación seleccionada.';
      }
    });
  }

  descargarLogConsolidacion(): void {
    const logEntries = this.buildModalTerminalEntries();
    const contenido = logEntries.length
      ? logEntries
        .map(log => `[${log.timestamp}] [${log.level}] ${log.logger} :: ${log.message}`)
        .join('\n')
      : this.modalConsolidacionMensaje;

    const blob = new Blob([contenido], { type: 'text/plain;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `sipro-consolidacion-${new Date().toISOString().replace(/[:.]/g, '-')}.log`;
    anchor.click();
    window.URL.revokeObjectURL(url);
  }

  ejecutarSql(): void {
    if (!this.operacionesSqlDisponibles.includes(this.sqlOperacion)) {
      this.sqlError = `La operación ${this.sqlOperacion} no está habilitada para este ambiente.`;
      this.sqlResultado = null;
      return;
    }

    if (!this.sqlTexto.trim()) {
      this.sqlError = 'Debes ingresar una sentencia SQL.';
      this.sqlResultado = null;
      return;
    }

    if (this.sqlEscritura && !this.sqlJustificacion.trim()) {
      this.sqlError = 'Las operaciones de escritura requieren una justificación.';
      this.sqlResultado = null;
      return;
    }

    this.sqlLoading = true;
    this.sqlError = '';
    this.sqlResultado = null;

    this.adminService.ejecutarSql({
      tipoOperacion: this.sqlOperacion,
      sql: this.sqlTexto,
      justificacion: this.sqlEscritura ? this.sqlJustificacion.trim() : null
    }).subscribe({
      next: response => {
        this.sqlResultado = response;
        this.sqlError = response.exito ? '' : response.mensaje;
        this.sqlLoading = false;

        if (response.exito && this.sqlEscritura) {
          this.cargarDashboard(this.periodoSeleccionado?.valor ?? undefined, true);
        }
      },
      error: err => {
        const backendResponse = err?.error as AdminSqlExecuteResponse | undefined;
        this.sqlResultado = backendResponse ?? null;
        this.sqlError = backendResponse?.mensaje || err?.error?.message || 'No fue posible ejecutar la sentencia.';
        this.sqlLoading = false;
      }
    });
  }

  cambiarOperacionSql(operacion: AdminSqlOperation): void {
    if (!this.operacionesSqlDisponibles.includes(operacion)) {
      return;
    }

    this.sqlOperacion = operacion;
    this.showSqlTablesMenu = false;
    this.limpiarFeedbackCopiaSql();
    this.sqlResultado = null;
    this.sqlError = '';
    if (!this.sqlEscritura) {
      this.sqlJustificacion = '';
    }
  }

  cambiarNivelLog(level: AdminLogLevel): void {
    if (!this.logsStreamingEnabled) {
      return;
    }

    if (this.logLevel === level) {
      return;
    }
    this.logLevel = level;
    this.reiniciarLogs();
  }

  toggleLogsPause(): void {
    if (!this.logsStreamingEnabled) {
      return;
    }

    this.logsPaused = !this.logsPaused;
    if (!this.logsPaused) {
      this.consultarLogs(false);
    }
  }

  limpiarLogs(): void {
    this.logEntries = [];
    this.logsError = '';
  }

  descargarLogs(): void {
    if (!this.logsDownloadEnabled) {
      return;
    }

    const contenido = this.logEntries
      .map(log => `[${log.timestamp}] [${log.level}] ${log.logger} :: ${log.message}`)
      .join('\n');

    const blob = new Blob([contenido || 'Sin logs capturados'], { type: 'text/plain;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `sipro-admin-logs-${new Date().toISOString().replace(/[:.]/g, '-')}.log`;
    anchor.click();
    window.URL.revokeObjectURL(url);
  }

  descargarResultadoSqlCsv(): void {
    if (!this.puedeDescargarResultadoSqlCsv || !this.sqlResultado) {
      return;
    }

    const contenido = this.construirCsvResultadoSql(this.sqlColumns, this.sqlResultado.filas ?? []);
    const blob = new Blob([`\ufeff${contenido}`], { type: 'text/csv;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `sipro-sql-select-${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    anchor.click();
    window.URL.revokeObjectURL(url);
  }

  formatNumber(value: number | null | undefined): string {
    if (value == null) {
      return '0';
    }
    return this.numberFormatter.format(value);
  }

  formatFileSize(bytes: number | null | undefined): string {
    if (bytes == null || bytes <= 0) {
      return 'Sin peso';
    }

    if (bytes < 1024) {
      return `${bytes} B`;
    }
    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} KB`;
    }
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  obtenerNombreArchivoVisible(archivo: AdminArchivoEstado): string {
    return archivo.nombreVisual || archivo.nombreArchivo || 'Archivo sin nombre';
  }

  obtenerPesoArchivoVisible(archivo: AdminArchivoEstado): string {
    if (archivo.noReportaDatos) {
      return '0 KB';
    }

    if (archivo.pesoBytes != null) {
      return this.formatFileSize(archivo.pesoBytes);
    }

    return 'Sin archivo';
  }

  normalizarEstadoArchivo(estado: string | null | undefined): string {
    const lower = (estado || '').toLowerCase().trim();

    if (lower.includes('aprobado') || lower.includes('aprobación sin datos') || lower.includes('aprobacion sin datos')) {
      return 'APROBADO';
    }
    if (lower.includes('rechaz')) {
      return 'RECHAZADO';
    }
    if (lower.includes('pendiente')) {
      return 'PENDIENTE';
    }

    return (estado || 'DESCONOCIDO').toUpperCase();
  }

  formatDateTime(value: string | null | undefined): string {
    if (!value) {
      return 'Sin fecha';
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return this.dateTimeFormatter.format(date);
  }

  formatSqlValue(value: unknown): string {
    if (value == null) {
      return 'null';
    }

    if (typeof value === 'object') {
      try {
        return JSON.stringify(value);
      } catch {
        return '[objeto]';
      }
    }

    return String(value);
  }

  private construirCsvResultadoSql(columnas: string[], filas: Array<Record<string, unknown>>): string {
    const encabezado = columnas.map(columna => this.escaparValorCsv(columna)).join(',');
    const registros = filas.map(fila => columnas
      .map(columna => this.escaparValorCsv(this.serializarValorCsv(fila[columna])))
      .join(','));

    return [encabezado, ...registros].join('\r\n');
  }

  private serializarValorCsv(value: unknown): string {
    if (value == null) {
      return '';
    }

    if (typeof value === 'object') {
      try {
        return JSON.stringify(value);
      } catch {
        return '[objeto]';
      }
    }

    return String(value);
  }

  private escaparValorCsv(value: string): string {
    return `"${value.replace(/"/g, '""')}"`;
  }

  obtenerClaseEstado(estado: string | null | undefined): string {
    const normalized = (estado ?? '').toLowerCase();

    if (normalized.includes('error') || normalized.includes('fall') || normalized.includes('rechaz')) {
      return 'error';
    }

    if (normalized.includes('proceso') || normalized.includes('curso') || normalized.includes('pend')) {
      return 'warning';
    }

    if (normalized.includes('warn') || normalized.includes('advert')) {
      return 'warning';
    }

    if (normalized.includes('ok') || normalized.includes('exit') || normalized.includes('consolid') || normalized.includes('aprob')) {
      return 'success';
    }

    return 'neutral';
  }

  obtenerClaseLog(level: string): string {
    switch (level.toUpperCase()) {
      case 'ERROR':
        return 'log-line--error';
      case 'WARN':
        return 'log-line--warn';
      case 'DEBUG':
        return 'log-line--debug';
      default:
        return 'log-line--info';
    }
  }

  private cargarDashboard(periodo?: string, silent = false): void {
    this.loadingDashboard = !silent;
    this.dashboardError = '';

    this.adminService.obtenerDashboard(periodo).subscribe({
      next: response => {
        const periodoAnterior = this.dashboard?.periodoSeleccionado ?? null;
        this.dashboard = response;
        this.syncPeriodSelection(response.periodoSeleccionado);
        this.syncSqlOperation();

        if (response.periodoSeleccionado !== periodoAnterior) {
          this.confirmacionConsolidacion = false;
          this.confirmacionConsolidacionParcial = false;
        }

        if (((response.estadoPeriodo?.cantidadPlanillasPendientes ?? 0)
          + (response.estadoPeriodo?.cantidadPlanillasRechazadas ?? 0)) === 0) {
          this.confirmacionConsolidacionParcial = false;
        }

        this.loadingDashboard = false;

        if (response.estadoConsolidacion?.terminal === false && this.periodoSeleccionado?.valor) {
          this.consolidacionEnCurso = true;
          this.consolidacionModalVisible = true;
          this.iniciarActividadProtegidaConsolidacion();
          if (!this.consolidacionLogEntries.length) {
            this.consultarLogs(true, 'CONSOLIDACION');
          }
          this.iniciarPollingDashboard(this.periodoSeleccionado.valor);
        } else if (response.estadoConsolidacion?.fase2EnCurso && this.periodoSeleccionado?.valor) {
          this.consolidacionEnCurso = false;
          if (!this.fase2ModalCerradoPorUsuario) {
            this.consolidacionModalVisible = true;
            if (!this.consolidacionLogEntries.length) {
              this.consultarLogs(true, 'CONSOLIDACION');
            }
          }
          this.finalizarActividadProtegidaConsolidacion();
          this.iniciarPollingDashboard(this.periodoSeleccionado.valor);
        } else {
          this.consolidacionEnCurso = false;
          this.fase2ModalCerradoPorUsuario = false;
          this.detenerPollingDashboard();
          this.finalizarActividadProtegidaConsolidacion();
        }

        if (response.configuracion?.logs?.streamingHabilitado) {
          this.iniciarPollingLogs();
        } else {
          this.detenerPollingLogs();
          this.logsLoading = false;
          this.logEntries = [];
          this.logAfterId = 0;
          this.logsPaused = false;
        }
      },
      error: err => {
        this.dashboardError = this.resolveDashboardError(err);
        this.loadingDashboard = false;
      }
    });
  }

  private resolveDashboardError(err: unknown): string {
    const fallback = 'No fue posible cargar el panel de administración.';
    const message = (err as { error?: { message?: unknown } })?.error?.message;

    if (typeof message !== 'string') {
      return fallback;
    }

    const sanitized = message
      .split(/\r?\n/)[0]
      .replace(/<[^>]*>/g, '')
      .trim();

    if (!sanitized || /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(sanitized)) {
      return fallback;
    }

    return sanitized;
  }

  private buildModalTerminalErrorLog(): AdminLogItem | null {
    const mensaje = this.consolidacionError || this.dashboard?.estadoConsolidacion?.mensajeError;
    if (!mensaje) {
      return null;
    }

    const ultimaEntrada = this.visibleLogEntries[this.visibleLogEntries.length - 1];
    const timestamp = ultimaEntrada?.timestamp || this.dateTimeFormatter.format(new Date());

    return {
      id: -1,
      timestamp,
      level: 'ERROR',
      logger: 'CONSOLIDACION_MANUAL',
      thread: 'ui',
      scope: 'CONSOLIDACION',
      message: mensaje
    };
  }

  private buildModalTerminalEntries(limit?: number): AdminLogItem[] {
    const sourceEntries = this.consolidacionLogEntries;
    const baseLogs = typeof limit === 'number'
      ? sourceEntries.slice(-limit)
      : sourceEntries;
    const terminalError = this.buildModalTerminalErrorLog();

    if (!terminalError) {
      return baseLogs;
    }

    const alreadyIncluded = baseLogs.some(log =>
      log.level === 'ERROR' && log.logger === terminalError.logger && log.message === terminalError.message);

    return alreadyIncluded ? baseLogs : [...baseLogs, terminalError];
  }

  private syncPeriodSelection(periodValue?: string | null): void {
    const target = (this.dashboard?.periodosDisponibles ?? []).find(periodo => periodo.valor === periodValue)
      ?? this.dashboard?.periodosDisponibles?.[0]
      ?? null;

    this.selectedYear = target?.anio ?? null;
    this.selectedMonth = target?.mes ?? null;
  }

  private syncSqlOperation(): void {
    if (!this.operacionesSqlDisponibles.includes(this.sqlOperacion)) {
      this.sqlOperacion = this.operacionesSqlDisponibles[0] ?? 'SELECT';
      this.sqlJustificacion = '';
    }
  }

  private reiniciarEstadoEliminacion(): void {
    this.eliminacionConsolidacionId = null;
    this.eliminacionConsolidacionPeriodo = '';
    this.eliminacionMotivo = '';
    this.eliminacionConfirmacionTexto = '';
    this.eliminacionResponsabilidadConfirmada = false;
    this.eliminacionEnCurso = false;
    this.eliminacionError = '';
    this.eliminacionProgressVisible = false;
  }

  private iniciarIndicadorEliminacion(): void {
    this.limpiarTemporizadorEliminacion();
    this.eliminacionProgressVisible = false;
    this.eliminacionProgressTimeout = setTimeout(() => {
      if (this.eliminacionEnCurso) {
        this.eliminacionProgressVisible = true;
      }
    }, 2000);
  }

  private limpiarTemporizadorEliminacion(): void {
    if (this.eliminacionProgressTimeout) {
      clearTimeout(this.eliminacionProgressTimeout);
      this.eliminacionProgressTimeout = null;
    }
    this.eliminacionProgressVisible = false;
  }

  private async copiarAlPortapapeles(text: string): Promise<boolean> {
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text);
        return true;
      }
    } catch {
      return this.copiarAlPortapapelesFallback(text);
    }

    return this.copiarAlPortapapelesFallback(text);
  }

  private copiarAlPortapapelesFallback(text: string): boolean {
    try {
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.setAttribute('readonly', 'true');
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      textarea.style.pointerEvents = 'none';
      document.body.appendChild(textarea);
      textarea.focus();
      textarea.select();
      const copiado = document.execCommand('copy');
      document.body.removeChild(textarea);
      return copiado;
    } catch {
      return false;
    }
  }

  private mostrarFeedbackCopiaSql(mensaje: string, tipo: 'success' | 'error'): void {
    this.limpiarFeedbackCopiaSql();
    this.sqlTablesCopyFeedback = mensaje;
    this.sqlTablesCopyFeedbackType = tipo;
    this.sqlCopyFeedbackTimeout = setTimeout(() => {
      this.sqlTablesCopyFeedback = '';
      this.sqlCopyFeedbackTimeout = null;
    }, 2500);
  }

  private limpiarFeedbackCopiaSql(): void {
    if (this.sqlCopyFeedbackTimeout) {
      clearTimeout(this.sqlCopyFeedbackTimeout);
      this.sqlCopyFeedbackTimeout = null;
    }
    this.sqlTablesCopyFeedback = '';
  }

  private iniciarPollingDashboard(periodo: string): void {
    if (this.dashboardPollingInterval) {
      return;
    }

    this.dashboardPollingInterval = setInterval(() => {
      this.cargarDashboard(periodo, true);
    }, 4000);
  }

  private detenerPollingDashboard(): void {
    if (this.dashboardPollingInterval) {
      clearInterval(this.dashboardPollingInterval);
      this.dashboardPollingInterval = null;
    }
  }

  private iniciarPollingLogs(): void {
    if (!this.logsStreamingEnabled) {
      return;
    }

    if (this.logsPollingInterval) {
      return;
    }

    this.consultarLogs(true, 'ALL');
    if (this.consolidacionModalVisible) {
      this.consultarLogs(true, 'CONSOLIDACION');
    }
    this.logsPollingInterval = setInterval(() => {
      if (!this.logsPaused) {
        this.consultarLogs(false, 'ALL');
      }

      if (this.consolidacionModalVisible) {
        this.consultarLogs(false, 'CONSOLIDACION');
      }
    }, 2500);
  }

  private detenerPollingLogs(): void {
    if (this.logsPollingInterval) {
      clearInterval(this.logsPollingInterval);
      this.logsPollingInterval = null;
    }
  }

  private reiniciarLogs(): void {
    this.logEntries = [];
    this.logAfterId = 0;
    this.logsError = '';
    this.consultarLogs(true, 'ALL');
  }

  private reiniciarLogsConsolidacion(): void {
    this.consolidacionLogEntries = [];
    this.consolidacionLogAfterId = 0;
  }

  private consultarLogs(reset: boolean, scope: AdminLogScope = 'ALL'): void {
    if (!this.logsStreamingEnabled) {
      return;
    }

    if (reset) {
      if (scope === 'CONSOLIDACION') {
        this.consolidacionLogEntries = [];
        this.consolidacionLogAfterId = 0;
      } else {
        this.logEntries = [];
        this.logAfterId = 0;
      }
    }

    const isConsolidacionScope = scope === 'CONSOLIDACION';
    const currentEntries = isConsolidacionScope ? this.consolidacionLogEntries : this.logEntries;
    const currentCursor = isConsolidacionScope ? this.consolidacionLogAfterId : this.logAfterId;
    const previousCursor = currentCursor;
    const requestLimit = (reset || isConsolidacionScope)
      ? this.logsConfig?.maximoConsulta ?? this.logsConfig?.limiteConsultaPorDefecto ?? 500
      : this.logsConfig?.limiteConsultaPorDefecto ?? 500;

    if (!isConsolidacionScope) {
      this.logsLoading = currentEntries.length === 0;
    }

    this.adminService.obtenerLogs(
      currentCursor || undefined,
      isConsolidacionScope ? 'ALL' : this.logLevel,
      requestLimit,
      scope
    ).subscribe({
      next: response => {
        const nuevos = response.items ?? [];
        const latestId = response.latestId ?? currentCursor;
        const cursorId = response.cursorId ?? (nuevos.length ? nuevos[nuevos.length - 1].id : latestId);

        if (!isConsolidacionScope) {
          this.logsLoading = false;
          this.logsError = '';
        }

        if (nuevos.length > 0) {
          const merged = new Map<number, AdminLogItem>();
          [...currentEntries, ...nuevos].forEach(log => merged.set(log.id, log));
          const updatedEntries = Array.from(merged.values())
            .sort((left, right) => left.id - right.id)
            .slice(-this.maxVisibleLogEntries);

          if (isConsolidacionScope) {
            this.consolidacionLogEntries = updatedEntries;
          } else {
            this.logEntries = updatedEntries;
          }

          if (!isConsolidacionScope && !this.logsPaused) {
            this.scrollLogsToEnd();
          }
        }

        if (isConsolidacionScope) {
          this.consolidacionLogAfterId = cursorId;
        } else {
          this.logAfterId = cursorId;
        }

        if (cursorId > previousCursor && cursorId < latestId) {
          this.consultarLogs(false, scope);
        }
      },
      error: err => {
        if (!isConsolidacionScope) {
          this.logsLoading = false;
          this.logsError = err?.error?.message || 'No fue posible consultar los logs del panel.';
        }
      }
    });
  }

  private buildRangoConsolidacionTexto(): string {
    const estadoPeriodo = this.estadoPeriodo;
    if (!estadoPeriodo?.inicioRangoConsolidacion || !estadoPeriodo?.finRangoConsolidacion) {
      return '';
    }

    const prefijo = estadoPeriodo.ventanaIgnoradaPorConfiguracion ? 'Rango teórico' : 'Rango permitido';
    return `${prefijo}: ${this.formatDateTime(estadoPeriodo.inicioRangoConsolidacion)} a ${this.formatDateTime(estadoPeriodo.finRangoConsolidacion)}. Fuente: ${estadoPeriodo.fuenteVentana}.`;
  }

  private buildResumenNoBloqueanteTexto(): string {
    const pendientes = this.estadoPeriodo?.cantidadPlanillasPendientes ?? 0;
    const rechazadas = this.estadoPeriodo?.cantidadPlanillasRechazadas ?? 0;
    const partes: string[] = [];

    if (pendientes > 0) {
      partes.push(`${pendientes} ${pendientes === 1 ? 'pendiente' : 'pendientes'}`);
    }

    if (rechazadas > 0) {
      partes.push(`${rechazadas} ${rechazadas === 1 ? 'rechazada' : 'rechazadas'}`);
    }

    if (partes.length === 0) {
      return 'No hay pendientes ni rechazadas que afecten esta ejecución.';
    }

    return `Hay ${partes.join(' y ')} activas que no bloquean la consolidación; se consolidará solo lo aprobado.`;
  }

  private iniciarActividadProtegidaConsolidacion(): void {
    if (this.consolidacionActividadProtegida) {
      return;
    }
    this.authService.beginProtectedActivity();
    this.consolidacionActividadProtegida = true;
  }

  private finalizarActividadProtegidaConsolidacion(): void {
    if (!this.consolidacionActividadProtegida) {
      return;
    }
    this.authService.endProtectedActivity();
    this.consolidacionActividadProtegida = false;
  }

  private updateDateTime(): void {
    this.currentDateTime = new Intl.DateTimeFormat('es-CO', {
      dateStyle: 'short',
      timeStyle: 'short'
    }).format(new Date());
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

  private logMatchesDateRange(log: AdminLogItem): boolean {
    const logDate = this.parseLogTimestamp(log.timestamp);
    if (!logDate) {
      return true;
    }

    const fromDate = this.logsFromDate ? new Date(`${this.logsFromDate}T00:00:00`) : null;
    const toDate = this.logsToDate ? new Date(`${this.logsToDate}T23:59:59`) : null;

    if (fromDate && logDate < fromDate) {
      return false;
    }

    if (toDate && logDate > toDate) {
      return false;
    }

    return true;
  }

  private parseLogTimestamp(timestamp: string): Date | null {
    const match = timestamp.match(/^(\d{2})\/(\d{2})\/(\d{4}) (\d{2}):(\d{2}):(\d{2})$/);
    if (!match) {
      const parsed = new Date(timestamp);
      return Number.isNaN(parsed.getTime()) ? null : parsed;
    }

    const [, day, month, year, hours, minutes, seconds] = match;
    return new Date(
      Number(year),
      Number(month) - 1,
      Number(day),
      Number(hours),
      Number(minutes),
      Number(seconds)
    );
  }
}