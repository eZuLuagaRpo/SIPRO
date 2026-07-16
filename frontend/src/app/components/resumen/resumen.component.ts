import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Component, DestroyRef, OnDestroy, OnInit, inject } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { LoadingComponent } from '../shared/loading/loading.component';
import { AuthService } from '../../services/auth.service';
import { ValidationService } from '../../services/validation.service';
import {
  ConsolidacionComparacionMetrica,
  ConsolidacionCreffosResumen,
  ConsolidacionMesDisponible,
  ConsolidacionPeriodoAnual,
  ConsolidacionProductoResumen,
  ConsolidacionResumenResponse
} from '../../models/validation.model';
import { User, UsuarioPermisos } from '../../models/user.model';

/**
 * Muestra el resumen consolidado por período y la comparación contra el archivo CREFFSOS.
 */
@Component({
  selector: 'app-resumen',
  standalone: true,
  imports: [CommonModule, RouterModule, LoadingComponent],
  templateUrl: './resumen.component.html',
  styleUrls: ['./resumen.component.scss']
})
export class ResumenComponent implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  private readonly numberFormatter = new Intl.NumberFormat('es-CO');
  private readonly currencyFormatter = new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    currencyDisplay: 'symbol',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
  private dateTimeInterval: ReturnType<typeof setInterval> | null = null;

  currentUser: User | null = null;
  permisos: UsuarioPermisos | null = null;
  sidebarOpen = false;
  subMenuOpen = true;
  currentDateTime = '';
  currentIp = '127.0.0.1';
  loading = false;
  errorMessage = '';

  resumen: ConsolidacionResumenResponse | null = null;
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

        if (!this.resumen && !this.loading) {
          this.cargarResumen();
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

  get productos(): ConsolidacionProductoResumen[] {
    return [...(this.resumen?.productos ?? [])].sort((a, b) =>
      a.nombreProducto.localeCompare(b.nombreProducto, 'es', { sensitivity: 'base' })
    );
  }

  get creffos(): ConsolidacionCreffosResumen | null {
    return this.resumen?.creffos ?? null;
  }

  get metricasComparacion(): ConsolidacionComparacionMetrica[] {
    return (this.resumen?.metricasComparacion ?? []).filter(metrica =>
      metrica.codigo === 'REGISTROS' || metrica.codigo === 'VLRINIOBL');
  }

  get metricasConDiferencia(): number {
    return this.metricasComparacion.filter(metrica => !metrica.coincide).length;
  }

  get totalProductos(): number {
    return this.productos.length;
  }

  get totalRegistrosProductos(): number {
    return this.resumen?.cantidadRegistrosConsolidados ?? 0;
  }

  get totalVlrIniOblProductos(): number {
    return this.resumen?.totalVlrIniObl ?? 0;
  }

  get totalRegistrosFullIfrsProductos(): number {
    return this.productos.reduce((acumulado, producto) =>
      acumulado + (producto.cantidadRegistrosFullIfrs ?? 0), 0);
  }

  get totalRegistrosCreffos(): number {
    return this.creffos?.cantidadRegistros ?? 0;
  }

  get totalRegistrosArchivoControl(): number {
    return this.resumen?.cantidadRegistrosArchivoControl ?? 0;
  }

  get totalVlrIniOblCreffos(): number {
    return this.creffos?.totalVlrIniObl ?? 0;
  }

  get hayComparacionVisible(): boolean {
    return this.metricasComparacion.length > 0;
  }

  get diferenciaRegistros(): number {
    return this.findComparisonMetric('REGISTROS')?.diferencia ?? 0;
  }

  get diferenciaVlrIniObl(): number {
    return this.findComparisonMetric('VLRINIOBL')?.diferencia ?? 0;
  }

  get diferenciaRegistrosFullIfrsControl(): number {
    return this.totalRegistrosFullIfrsProductos - this.totalRegistrosArchivoControl;
  }

  get productoLiderPorRegistros(): ConsolidacionProductoResumen | null {
    if (this.productos.length === 0) {
      return null;
    }

    return [...this.productos].sort((left, right) => right.cantidadRegistros - left.cantidadRegistros)[0] ?? null;
  }

  get productoLiderPorValor(): ConsolidacionProductoResumen | null {
    if (this.productos.length === 0) {
      return null;
    }

    return [...this.productos].sort((left, right) => right.totalVlrIniObl - left.totalVlrIniObl)[0] ?? null;
  }

  get porcentajeLiderRegistros(): number {
    const total = this.resumen?.cantidadRegistrosConsolidados ?? 0;
    const lider = this.productoLiderPorRegistros?.cantidadRegistros ?? 0;

    if (total <= 0) {
      return 0;
    }

    return (lider / total) * 100;
  }

  get porcentajeLiderValor(): number {
    const total = this.resumen?.totalVlrIniObl ?? 0;
    const lider = this.productoLiderPorValor?.totalVlrIniObl ?? 0;

    if (total <= 0) {
      return 0;
    }

    return (lider / total) * 100;
  }

  get estadoResumenEtiqueta(): string {
    const estado = this.resumen?.estadoConsolidacion ?? 'SIN_DATOS';
    switch (estado) {
      case 'COMPLETADO':
        return 'Consolidación lista';
      case 'SIN_DATOS':
        return 'Sin datos';
      default:
        return estado.replace(/_/g, ' ');
    }
  }

  get estadoResumenClase(): string {
    const estado = this.resumen?.estadoConsolidacion ?? 'SIN_DATOS';
    if (estado === 'COMPLETADO') {
      return 'is-success';
    }
    return 'is-muted';
  }

  get fechaActualizacionLabel(): string {
    return this.formatDateTime(this.resumen?.fechaActualizacion ?? null);
  }

  get estadoCreffosEtiqueta(): string {
    switch (this.creffos?.estado) {
      case 'CONSISTENTE':
        return 'Archivo conciliado';
      case 'CON_DIFERENCIAS':
        return 'Archivo con diferencias';
      case 'NO_ENCONTRADO':
        return 'Archivo no encontrado';
      case 'ERROR_LECTURA':
        return 'Error de lectura';
      default:
        return 'Sin comparación';
    }
  }

  get estadoCreffosClase(): string {
    switch (this.creffos?.estado) {
      case 'CONSISTENTE':
        return 'is-success';
      case 'CON_DIFERENCIAS':
      case 'NO_ENCONTRADO':
      case 'ERROR_LECTURA':
        return 'is-warning';
      default:
        return 'is-muted';
    }
  }

  /**
   * Consulta el resumen consolidado para el período seleccionado o para el último disponible.
   */
  cargarResumen(anio?: number, mes?: number): void {
    this.loading = true;
    this.errorMessage = '';

    this.validationService.obtenerResumenConsolidacion(anio, mes).subscribe({
      next: (response) => {
        this.resumen = response;
        this.periodosDisponibles = response.periodosDisponibles ?? [];
        this.selectedYear = response.anioSeleccionado;
        this.selectedMonth = response.mesSeleccionado;
        this.loading = false;
      },
      error: (error) => {
        this.resumen = null;
        this.periodosDisponibles = [];
        this.selectedYear = null;
        this.selectedMonth = null;
        this.errorMessage = error.error?.mensaje || error.message || 'No fue posible cargar el resumen consolidado.';
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
      this.cargarResumen(anio, month);
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

    this.cargarResumen(this.selectedYear, mes);
  }

  toggleSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
  }

  toggleSubMenu(): void {
    this.subMenuOpen = !this.subMenuOpen;
  }

  exportarResumen(): void {
    if (!this.resumen || !this.resumen.hayDatos || this.selectedYear === null || this.selectedMonth === null) {
      return;
    }

    this.validationService.descargarReporteResumenConsolidado(this.selectedYear, this.selectedMonth)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response: HttpResponse<Blob>) => {
          this.errorMessage = '';
          const blob = response.body;
          if (!blob) {
            this.errorMessage = 'No fue posible exportar el consolidado. Respuesta vacía.';
            return;
          }
          const nombreArchivo = this.obtenerNombreArchivoDesdeCabecera(response) ?? this.construirNombreArchivoFallback();
          this.descargarBlobReporte(blob, nombreArchivo);
        },
        error: (error) => {
          console.error('Error exportando resumen consolidado:', error);
          this.errorMessage = 'No fue posible exportar el consolidado. Intente nuevamente.';
        }
      });
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

  trackByProducto(index: number, producto: ConsolidacionProductoResumen): string | number {
    return producto.idProducto ?? `${producto.nombreProducto}-${index}`;
  }

  trackByMetrica(index: number, metrica: ConsolidacionComparacionMetrica): string {
    return metrica.codigo;
  }

  formatNumber(value: number | null | undefined): string {
    return this.numberFormatter.format(value ?? 0);
  }

  formatCurrency(value: number | null | undefined): string {
    return this.currencyFormatter.format(value ?? 0);
  }

  formatSegmentoUnoCantidad(producto: ConsolidacionProductoResumen): string {
    if (this.shouldRenderNoAplicaSegmentoUno(producto) && (producto.cantidadRegistros ?? 0) === 0) {
      return 'No aplica';
    }
    return this.formatNumber(producto.cantidadRegistros);
  }

  formatSegmentoUnoValor(producto: ConsolidacionProductoResumen): string {
    if (this.shouldRenderNoAplicaSegmentoUno(producto) && (producto.totalVlrIniObl ?? 0) === 0) {
      return 'No aplica';
    }
    return this.formatCurrency(producto.totalVlrIniObl);
  }

  formatSegmentoDosCantidad(producto: ConsolidacionProductoResumen): string {
    if (this.shouldRenderNoAplicaSegmentoDos(producto) && (producto.cantidadRegistrosFullIfrs ?? 0) === 0) {
      return 'No aplica';
    }
    return this.formatNumber(producto.cantidadRegistrosFullIfrs);
  }

  formatMetricValue(value: number | null | undefined, tipoValor: string): string {
    return tipoValor === 'currency'
      ? this.formatCurrency(value)
      : this.formatNumber(value);
  }

  formatMetricDifference(value: number | null | undefined, tipoValor: string): string {
    const numericValue = value ?? 0;
    const formatted = this.formatMetricValue(Math.abs(numericValue), tipoValor);
    if (numericValue > 0) {
      return `+${formatted}`;
    }
    if (numericValue < 0) {
      return `-${formatted}`;
    }
    return formatted;
  }

  formatCreffosOrigen(origen: string | null | undefined): string {
    switch (origen) {
      case 'STORAGE':
        return 'Storage consolidado';
      case 'RUTA_COMPARTIDA':
        return 'Ruta compartida';
      case 'SIN_ARCHIVO':
        return 'Sin archivo';
      default:
        return origen || 'No disponible';
    }
  }

  formatDateTime(value: string | null): string {
    if (!value) {
      return 'Sin actualización registrada';
    }

    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return value;
    }

    return new Intl.DateTimeFormat('es-CO', {
      dateStyle: 'long',
      timeStyle: 'short'
    }).format(parsed);
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

  private findComparisonMetric(codigo: string): ConsolidacionComparacionMetrica | undefined {
    return (this.resumen?.metricasComparacion ?? []).find(metrica => metrica.codigo === codigo);
  }

  private shouldRenderNoAplicaSegmentoUno(producto: ConsolidacionProductoResumen): boolean {
    const nombreNormalizado = this.normalizeProductName(producto.nombreProducto);
    return nombreNormalizado === 'recaudos' || nombreNormalizado === 'seguridad';
  }

  private shouldRenderNoAplicaSegmentoDos(producto: ConsolidacionProductoResumen): boolean {
    return this.normalizeProductName(producto.nombreProducto) === 'tipz';
  }

  private normalizeProductName(nombreProducto: string | null | undefined): string {
    return (nombreProducto ?? '').trim().toLowerCase();
  }

  private descargarBlobReporte(blob: Blob, nombre: string): void {
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = nombre;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    window.URL.revokeObjectURL(url);
  }

  private obtenerNombreArchivoDesdeCabecera(response: HttpResponse<Blob>): string | null {
    const contentDisposition = response.headers.get('content-disposition')
      ?? response.headers.get('Content-Disposition');
    if (!contentDisposition) {
      return null;
    }

    const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
    if (utf8Match?.[1]) {
      return decodeURIComponent(utf8Match[1].replace(/"/g, '').trim());
    }

    const simpleMatch = contentDisposition.match(/filename=([^;]+)/i);
    if (simpleMatch?.[1]) {
      return simpleMatch[1].replace(/"/g, '').trim();
    }

    return null;
  }

  private construirNombreArchivoFallback(): string {
    const anio = this.selectedYear ?? new Date().getFullYear();
    const mesNumero = this.selectedMonth ?? (new Date().getMonth() + 1);
    const ultimoDia = new Date(anio, mesNumero, 0);
    const yyyy = ultimoDia.getFullYear();
    const mm = String(ultimoDia.getMonth() + 1).padStart(2, '0');
    const dd = String(ultimoDia.getDate()).padStart(2, '0');
    return `conciliacion_planillas_manuales_${yyyy}${mm}${dd}.xlsx`;
  }
}