import { CommonModule } from '@angular/common';
import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AuthService } from '../../services/auth.service';
import { ParametrosService } from '../../services/parametros.service';
import { User } from '../../models/user.model';
import {
  ExcepcionVentanaCarga,
  ExcepcionRequest,
  MesDisponible,
  ReglaVentanaBase,
  ProductoCatalogo,
  ProductoRequest,
  RolAzureResult,
  RolSistema,
  SegmentoSistema,
  UsuarioResumen,
  AsignacionUsuario,
  CambioLiderRequest,
  CambioLiderResultado,
  NuevoUsuarioRequest
} from '../../models/parametros.model';

interface CheckboxProducto {
  idProducto: number;
  titulo: string;
  idSegmento: number;
  permitido: boolean;
}

interface ExcepcionFormState {
  periodoValoracion: string;
  anioCorte: number | null;
  mesCorte: number | null;
  fechaAperturaOverride: string;
  horaAperturaOverride: string;
  fechaCierreOverride: string;
  horaCierreOverride: string;
  motivo: string;
}

interface MesCorteOption {
  valor: number;
  etiqueta: string;
}

@Component({
  selector: 'app-parametros',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './parametros.component.html',
  styleUrls: ['./parametros.component.scss']
})
export class ParametrosComponent implements OnInit {

  private readonly destroyRef = inject(DestroyRef);

  // ── Auth / Layout ────────────────────────────────────────────────────────
  currentUser: User | null = null;
  sidebarOpen = false;
  subMenuOpen = true;

  // ── Feedbacks globales ───────────────────────────────────────────────────
  mensajeGlobal = '';
  mensajeGlobalTipo: 'success' | 'error' | '' = '';
  cargandoInicial = true;

  // ─── Sección 1: Ventana de Carga ──────────────────────────────────────────
  reglaBase: ReglaVentanaBase | null = null;
  excepciones: ExcepcionVentanaCarga[] = [];
  mesesDisponibles: MesDisponible[] = [];
  mostrarHistorial = false;

  readonly mesesCorte: MesCorteOption[] = [
    { valor: 1, etiqueta: 'Enero' },
    { valor: 2, etiqueta: 'Febrero' },
    { valor: 3, etiqueta: 'Marzo' },
    { valor: 4, etiqueta: 'Abril' },
    { valor: 5, etiqueta: 'Mayo' },
    { valor: 6, etiqueta: 'Junio' },
    { valor: 7, etiqueta: 'Julio' },
    { valor: 8, etiqueta: 'Agosto' },
    { valor: 9, etiqueta: 'Septiembre' },
    { valor: 10, etiqueta: 'Octubre' },
    { valor: 11, etiqueta: 'Noviembre' },
    { valor: 12, etiqueta: 'Diciembre' }
  ];

  excepcionForm: ExcepcionFormState = {
    periodoValoracion: '',
    anioCorte: null,
    mesCorte: null,
    fechaAperturaOverride: '',
    horaAperturaOverride: '00:00',
    fechaCierreOverride: '',
    horaCierreOverride: '23:59',
    motivo: ''
  };
  excepcionEditandoPeriodo: string | null = null;
  excepcionFormTitulo = 'Agregar una excepción de fechas';
  guardandoExcepcion = false;
  excepcionPendienteEliminar: ExcepcionVentanaCarga | null = null;
  eliminandoExcepcion = false;

  // ─ Sistema de Notificaciones Toast ─────────────────────────
  toastMensaje: string | null = null;
  toastTipo: 'success' | 'error' = 'success';
  private toastTimeout?: ReturnType<typeof setTimeout>;

  // ─── Sección 2: Asignación de Productos ──────────────────────────────────
  usuarios: UsuarioResumen[] = [];
  segmentos: SegmentoSistema[] = [];
  roles: RolSistema[] = [];
  productos: ProductoCatalogo[] = [];

  usuarioSeleccionadoId: number | null = null;
  usuarioSeleccionado: UsuarioResumen | null = null;
  busquedaUsuarioAsignacion = '';
  mostrarOpcionesUsuarioAsignacion = false;
  rolSeleccionado: number | null = null;
  rolAzureCargando = false;
  rolAzureMensaje: string | null = null;
  checkboxesPorSegmento: Map<number, CheckboxProducto[]> = new Map();
  guardandoAsignacion = false;
  asignacionError = '';
  asignacionExito = '';
  confirmacionAsignacionVisible = false;
  private rolAsignacionInicial: number | null = null;
  private firmaProductosAsignacionInicial = '';

  // ─── Sección 3: Cambio de Líder ───────────────────────────────────────────
  busquedaUsuario = '';
  usuariosSeleccionados: Set<number> = new Set();
  seleccionarTodosLider = false;
  nuevoLiderId: number | null = null;
  pendientesLider: Map<number, number> = new Map();
  guardandoCambioLider = false;
  liderError = '';
  liderExito = '';
  liderTransferidas = 0;
  busquedaLider = '';
  mostrarOpcionesLider = false;
  confirmacionLiderVisible = false;

  // Validación Azure para la Sección 3
  validacionAzureCargadores: Record<string, RolAzureResult> = {};
  validandoAzureCargadores = false;
  validacionAzureCompletada = false;

  // ─── Sección 4: Nuevo Usuario ─────────────────────────────────────────────
  nuevoUsuario: NuevoUsuarioRequest = {
    nombres: '',
    apellidos: '',
    correo: '',
    usuario: '',
    areaNombre: '',
    idRol: 0,
    idsSegmentos: [1, 2],
    idsProductos: [],
    idLider: null
  };
  productoParaAgregar: number | null = null;
  productosSeleccionadosNuevo: ProductoCatalogo[] = [];
  guardandoNuevoUsuario = false;
  nuevoUsuarioError = '';
  nuevoUsuarioExito = '';

  // Buscador de líder para nuevo usuario cargador
  busquedaLiderNuevoUsuario = '';
  mostrarOpcionesLiderNuevoUsuarioFlag = false;
  liderSeleccionadoNuevoUsuario: UsuarioResumen | null = null;

  // ─── Sección 5: Catálogo de Productos ─────────────────────────────────────
  productoForm: ProductoRequest = {
    titulo: '',
    idSegmento: 0,
    activo: true,
    nombreArchivoPermitido: '',
    nombreControlPermitido: ''
  };
  productoEditandoId: number | null = null;
  productoFormTitulo = 'Asistente para Nuevo Producto';
  guardandoProducto = false;
  productoError = '';
  productoExito = '';
  busquedaProducto = '';

  // Modal confirmación de producto
  confirmacionProductoVisible = false;
  confirmacionProductoAccion: 'crear' | 'editar' | 'desactivar' | 'activar' = 'crear';
  confirmacionProductoNombre = '';
  private _productoDesactivarPendiente: ProductoCatalogo | null = null;

  readonly modifyIcon = '/assets/images/modify_edit.png';
  readonly eraseIcon = '/assets/images/erase.png';
  readonly viewIcon = '/assets/images/view.png';
  readonly alertIcon = '/assets/images/Alerta.png';
  readonly okIcon = '/assets/images/Ok.png';
  readonly userIcon = '/assets/images/user.png';
  readonly settingsIcon = '/assets/images/settings.png';
  readonly searchIcon = '/assets/images/search.png';

  constructor(
    private authService: AuthService,
    private parametrosService: ParametrosService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.authService.currentUser$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(user => {
        this.currentUser = user;
        if (!user) {
          this.router.navigate(['/login']);
          return;
        }
        if (!this.authService.puedeAdministrar()) {
          this.router.navigate(['/inicio']);
          return;
        }
      });

    this.cargarDatosIniciales();
  }

  // ── Auth helpers ─────────────────────────────────────────────────────────

  get userName(): string {
    if (this.currentUser) {
      const n = this.currentUser.nombres || '';
      const a = this.currentUser.apellidos || '';
      return `${n} ${a}`.trim() || this.currentUser.name || 'Usuario';
    }
    return 'Usuario';
  }

  get puedeCargar(): boolean { return this.authService.puedeAccederCargaManual(); }
  get puedeAprobar(): boolean { return this.authService.puedeAccederAprobacionManual(); }
  get puedeVerResumen(): boolean { return this.authService.puedeAccederResumenConsolidado(); }
  get puedeVerAdmin(): boolean { return this.authService.puedeAccederPanelAdmin(); }
  get puedeVerParametros(): boolean { return this.authService.puedeAdministrar(); }

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
    return 'Módulo de cambio de parámetros del sistema (activo).';
  }

  get tooltipModuloNoDisponible(): string {
    return 'Este módulo está bloqueado para su perfil actual.';
  }

  toggleSidebar(): void { this.sidebarOpen = !this.sidebarOpen; }
  toggleSubMenu(): void { this.subMenuOpen = !this.subMenuOpen; }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  // ── Carga inicial ─────────────────────────────────────────────────────────

  private cargarDatosIniciales(): void {
    this.cargandoInicial = true;
    const erroresCarga: string[] = [];

    forkJoin({
      reglaBase: this.cargarConFallback(this.parametrosService.getReglaBase(), null, 'regla base de ventana', erroresCarga),
      excepciones: this.cargarConFallback(this.parametrosService.getExcepciones(), [], 'excepciones de ventana', erroresCarga),
      meses: this.cargarConFallback(this.parametrosService.getMesesDisponibles(), [], 'meses disponibles', erroresCarga),
      usuarios: this.cargarConFallback(this.parametrosService.getUsuarios(), [], 'usuarios', erroresCarga),
      productos: this.cargarConFallback(this.parametrosService.getProductos(), [], 'productos', erroresCarga),
      roles: this.cargarConFallback(this.parametrosService.getRoles(), [], 'roles', erroresCarga),
      segmentos: this.cargarConFallback(this.parametrosService.getSegmentos(), [], 'segmentos', erroresCarga)
    }).subscribe({
      next: ({ reglaBase, excepciones, meses, usuarios, productos, roles, segmentos }) => {
        this.reglaBase = reglaBase;
        this.excepciones = excepciones;
        this.mesesDisponibles = meses;
        this.usuarios = this.normalizarUsuarios(usuarios);
        this.productos = productos;
        this.roles = this.normalizarRoles(roles);
        this.segmentos = this.normalizarSegmentos(segmentos);

        if (!this.usuarioSeleccionadoId && this.usuarios.length > 0) {
          this.usuarioSeleccionadoId = this.usuarios[0].idUsuario;
          this.onUsuarioChange();
        }

        this.cargandoInicial = false;

        if (erroresCarga.length > 0) {
          this.mostrarMensajeGlobal(
            `La pantalla cargó parcialmente. Revise: ${erroresCarga.join(', ')}.`,
            'error'
          );
        }
      },
      error: () => {
        this.mostrarMensajeGlobal('No fue posible cargar los datos del módulo.', 'error');
        this.cargandoInicial = false;
      }
    });
  }

  private cargarConFallback<T>(
    request: Observable<T>,
    fallback: T,
    nombreFuente: string,
    erroresCarga: string[]
  ): Observable<T> {
    return request.pipe(
      catchError(() => {
        erroresCarga.push(nombreFuente);
        return of(fallback);
      })
    );
  }

  // ── Sección 1: Excepciones de Ventana ────────────────────────────────────

  get periodoReferenciaReglaBaseLabel(): string {
    const referencia = this.obtenerFechaReferenciaReglaBase();
    return new Intl.DateTimeFormat('es-CO', {
      month: 'long',
      year: 'numeric'
    }).format(referencia);
  }

  get reglaInicioLabel(): string {
    if (!this.reglaBase) return '—';
    return `${this.formatearFechaReglaBase(this.reglaBase.offsetDiasApertura)} a las ${this.reglaBase.horaApertura.slice(0, 5)}`;
  }

  get reglaFinLabel(): string {
    if (!this.reglaBase) return '—';
    return `${this.formatearFechaReglaBase(this.reglaBase.offsetDiasCierre)} a las ${this.reglaBase.horaCierre.slice(0, 5)}`;
  }

  get reglaInicioAyuda(): string {
    if (!this.reglaBase) return '—';
    return this.construirAyudaReglaBase(this.reglaBase.offsetDiasApertura);
  }

  get reglaFinAyuda(): string {
    if (!this.reglaBase) return '—';
    return this.construirAyudaReglaBase(this.reglaBase.offsetDiasCierre);
  }

  get aniosCorteDisponibles(): number[] {
    const actual = new Date().getFullYear();
    const years = new Set<number>([actual, actual + 1]);

    for (const mes of this.mesesDisponibles) {
      const referencia = this.obtenerReferenciaDesdePeriodo(mes.valor);
      if (referencia) {
        years.add(referencia.getFullYear());
      }
    }

    if (this.excepcionForm.anioCorte) {
      years.add(this.excepcionForm.anioCorte);
    }

    return Array.from(years).sort((a, b) => a - b);
  }

  get mesesCorteDisponibles(): MesCorteOption[] {
    return this.mesesCorte;
  }

  get minFechaInicio(): string {
    if (!this.excepcionForm.anioCorte || !this.excepcionForm.mesCorte) {
      return '';
    }
    const primerDia = new Date(this.excepcionForm.anioCorte, this.excepcionForm.mesCorte - 1, 1);
    return this.formatearFechaIso(primerDia);
  }

  get maxFechaInicio(): string {
    if (!this.excepcionForm.anioCorte || !this.excepcionForm.mesCorte) {
      return '';
    }
    const ultimoDia = new Date(this.excepcionForm.anioCorte, this.excepcionForm.mesCorte, 0);
    return this.formatearFechaIso(ultimoDia);
  }

  get minFechaFin(): string {
    if (!this.excepcionForm.anioCorte || !this.excepcionForm.mesCorte) {
      return '';
    }
    const primerDiaMesSiguiente = new Date(this.excepcionForm.anioCorte, this.excepcionForm.mesCorte, 1);
    return this.formatearFechaIso(primerDiaMesSiguiente);
  }

  get maxFechaFin(): string {
    if (!this.excepcionForm.anioCorte || !this.excepcionForm.mesCorte) {
      return '';
    }
    const ultimoDiaMesSiguiente = new Date(this.excepcionForm.anioCorte, this.excepcionForm.mesCorte + 1, 0);
    return this.formatearFechaIso(ultimoDiaMesSiguiente);
  }

  get puedeGuardarExcepcion(): boolean {
    const formulario = this.excepcionForm;
    return Boolean(
      formulario.anioCorte &&
      formulario.mesCorte &&
      formulario.fechaAperturaOverride &&
      formulario.horaAperturaOverride &&
      formulario.fechaCierreOverride &&
      formulario.horaCierreOverride &&
      formulario.motivo.trim()
    );
  }

  editarExcepcion(exc: ExcepcionVentanaCarga): void {
    this.excepcionEditandoPeriodo = exc.periodoValoracion;
    this.excepcionFormTitulo = 'Actualizar una excepción de fechas';
    const referencia = this.obtenerReferenciaDesdePeriodo(exc.periodoValoracion);
    this.excepcionForm = {
      periodoValoracion: exc.periodoValoracion,
      anioCorte: referencia?.getFullYear() ?? null,
      mesCorte: referencia ? referencia.getMonth() + 1 : null,
      fechaAperturaOverride: exc.fechaAperturaOverride ?? '',
      horaAperturaOverride: exc.horaAperturaOverride ?? '00:00',
      fechaCierreOverride: exc.fechaCierreOverride ?? '',
      horaCierreOverride: exc.horaCierreOverride ?? '23:59',
      motivo: exc.motivo
    };
    document.getElementById('excepcionCard')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  cancelarEdicionExcepcion(): void {
    this.excepcionEditandoPeriodo = null;
    this.excepcionFormTitulo = 'Agregar una excepción de fechas';
    this.excepcionForm = {
      periodoValoracion: '',
      anioCorte: null,
      mesCorte: null,
      fechaAperturaOverride: '',
      horaAperturaOverride: '00:00',
      fechaCierreOverride: '',
      horaCierreOverride: '23:59',
      motivo: ''
    };
  }

  guardarExcepcion(): void {
    const f = this.excepcionForm;
    if (!this.puedeGuardarExcepcion) {
      this.mostrarToast('Complete todos los campos antes de guardar.', 'error');
      return;
    }

    const periodoValoracion = this.construirPeriodoValoracion(f.anioCorte, f.mesCorte);
    if (!periodoValoracion) {
      this.mostrarToast('Seleccione el año de corte y el mes de corte.', 'error');
      return;
    }

    const req: ExcepcionRequest = {
      periodoValoracion,
      fechaAperturaOverride: f.fechaAperturaOverride,
      horaAperturaOverride: f.horaAperturaOverride,
      fechaCierreOverride: f.fechaCierreOverride,
      horaCierreOverride: f.horaCierreOverride,
      motivo: f.motivo.trim()
    };

    this.guardandoExcepcion = true;

    const obs = this.excepcionEditandoPeriodo
      ? this.parametrosService.actualizarExcepcion(this.excepcionEditandoPeriodo, req)
      : this.parametrosService.crearExcepcion(req);

    obs.subscribe({
      next: res => {
        this.guardandoExcepcion = false;
        if (res.success) {
          this.mostrarToast(res.mensaje || 'Guardado correctamente.', 'success');
          this.cancelarEdicionExcepcion();
          this.recargarExcepciones();
        } else {
          this.mostrarToast(res.mensaje || 'Error al guardar.', 'error');
        }
      },
      error: () => {
        this.guardandoExcepcion = false;
        this.mostrarToast('Error de conexión al guardar la excepción.', 'error');
      }
    });
  }

  eliminarExcepcion(exc: ExcepcionVentanaCarga): void {
    this.excepcionPendienteEliminar = exc;
  }

  cerrarModalEliminarExcepcion(): void {
    if (this.eliminandoExcepcion) {
      return;
    }

    this.excepcionPendienteEliminar = null;
  }

  confirmarEliminarExcepcion(): void {
    const excepcion = this.excepcionPendienteEliminar;
    if (!excepcion) {
      return;
    }

    this.eliminandoExcepcion = true;
    this.parametrosService.eliminarExcepcion(excepcion.periodoValoracion).subscribe({
      next: res => {
        this.eliminandoExcepcion = false;
        if (res.success) {
          this.excepciones = this.excepciones.filter(e => e.periodoValoracion !== excepcion.periodoValoracion);
          if (this.excepcionEditandoPeriodo === excepcion.periodoValoracion) {
            this.cancelarEdicionExcepcion();
          }
          this.excepcionPendienteEliminar = null;
          this.mostrarMensajeGlobal('Excepción eliminada.', 'success');
        } else {
          this.excepcionPendienteEliminar = null;
          this.mostrarMensajeGlobal(res.mensaje || 'No fue posible eliminar.', 'error');
        }
      },
      error: () => {
        this.eliminandoExcepcion = false;
        this.excepcionPendienteEliminar = null;
        this.mostrarMensajeGlobal('Error de conexión al eliminar.', 'error');
      }
    });
  }

  private recargarExcepciones(): void {
    this.parametrosService.getExcepciones().subscribe(exc => this.excepciones = exc);
  }

  onAnioCorteChange(value: number | string | null): void {
    this.excepcionForm.anioCorte = this.normalizarNumeroSeleccionado(value);
    this.actualizarPeriodoValoracionDesdeCorte();
  }

  onMesCorteChange(value: number | string | null): void {
    this.excepcionForm.mesCorte = this.normalizarNumeroSeleccionado(value);
    this.actualizarPeriodoValoracionDesdeCorte();
  }

  mostrarToast(mensaje: string, tipo: 'success' | 'error'): void {
    if (this.toastTimeout) {
      clearTimeout(this.toastTimeout);
    }
    this.toastMensaje = mensaje;
    this.toastTipo = tipo;
    this.toastTimeout = setTimeout(() => {
      this.cerrarToast();
    }, 4000);
  }

  cerrarToast(): void {
    this.toastMensaje = null;
    if (this.toastTimeout) {
      clearTimeout(this.toastTimeout);
    }
  }

  // ── Sección 2: Asignación de Productos ───────────────────────────────────

  onUsuarioChange(): void {
    if (!this.usuarioSeleccionadoId) {
      this.usuarioSeleccionado = null;
      this.busquedaUsuarioAsignacion = '';
      this.rolSeleccionado = null;
      this.rolAzureCargando = false;
      this.rolAzureMensaje = null;
      this.construirCheckboxes([]);
      this.asignacionError = '';
      this.asignacionExito = '';
      this.actualizarEstadoInicialAsignacion(null);
      return;
    }

    this.usuarioSeleccionado = this.usuarios.find(u => u.idUsuario === Number(this.usuarioSeleccionadoId)) ?? null;
    this.sincronizarBusquedaUsuarioSeleccionado();
    // El rol se obtiene en tiempo real desde Azure Entra ID (ver llamada abajo)
    this.rolSeleccionado = null;
    this.rolAzureCargando = true;
    this.rolAzureMensaje = null;
    this.asignacionError = '';
    this.asignacionExito = '';
    this.construirCheckboxes([]);

    // Consultar rol en Azure Entra ID en tiempo real
    this.parametrosService.getRolAzureUsuario(Number(this.usuarioSeleccionadoId)).subscribe({
      next: resultado => {
        this.rolAzureCargando = false;
        if (resultado.encontrado && resultado.idRol != null) {
          this.rolSeleccionado = resultado.idRol;
          this.rolAzureMensaje = null;
        } else {
          this.rolSeleccionado = this.usuarioSeleccionado?.idRolActual ?? null;
          this.rolAzureMensaje = resultado.mensaje ?? 'No se encontró rol en Azure Entra ID.';
        }
        this.cargarProductosAsignados();
      },
      error: () => {
        this.rolAzureCargando = false;
        // Fallback: usar el rol almacenado localmente en SIPRO
        this.rolSeleccionado = this.usuarioSeleccionado?.idRolActual ?? null;
        this.rolAzureMensaje = 'No fue posible consultar Azure Entra ID. Se muestra el último rol conocido.';
        this.cargarProductosAsignados();
      }
    });
  }

  /** Carga los productos asignados al usuario seleccionado desde SIPRO. */
  private cargarProductosAsignados(): void {
    this.parametrosService.getAsignacionUsuario(Number(this.usuarioSeleccionadoId)).subscribe({
      next: asig => {
        const permitidos = new Set<number>(
          asig.productosPorSegmento.flatMap(ps => ps.idsProductosPermitidos)
        );
        this.construirCheckboxes([...permitidos]);
        this.actualizarEstadoInicialAsignacion(asig.idRol ?? this.rolSeleccionado);
      },
      error: () => {
        this.construirCheckboxes([]);
        this.actualizarEstadoInicialAsignacion(this.rolSeleccionado);
      }
    });
  }

  get usuariosAsignacionFiltrados(): UsuarioResumen[] {
    const termino = this.normalizarTextoBusqueda(this.busquedaUsuarioAsignacion);
    const seleccionado = this.normalizarTextoBusqueda(this.usuarioSeleccionado?.nombreCompleto ?? '');

    if (!termino || termino === seleccionado) {
      return this.usuarios.slice(0, 8);
    }

    return this.usuarios
      .filter(usuario => this.construirTextoBusquedaUsuario(usuario).includes(termino))
      .slice(0, 8);
  }

  actualizarBusquedaUsuarioAsignacion(valor: string): void {
    this.busquedaUsuarioAsignacion = valor;
    this.mostrarOpcionesUsuarioAsignacion = true;
  }

  seleccionarUsuarioAsignacion(usuario: UsuarioResumen): void {
    this.usuarioSeleccionadoId = usuario.idUsuario;
    this.usuarioSeleccionado = usuario;
    this.mostrarOpcionesUsuarioAsignacion = false;
    this.sincronizarBusquedaUsuarioSeleccionado();
    this.onUsuarioChange();
  }

  restaurarBusquedaUsuarioAsignacion(): void {
    setTimeout(() => {
      this.mostrarOpcionesUsuarioAsignacion = false;
      this.sincronizarBusquedaUsuarioSeleccionado();
    }, 120);
  }

  textoUsuario(valor: string | null | undefined, fallback = '—'): string {
    const texto = this.normalizarTextoLegible(valor);
    return texto || fallback;
  }

  get segmentosUsuarioSeleccionado(): SegmentoSistema[] {
    if (!this.usuarioSeleccionado) {
      return [];
    }

    const idsSegmento = new Set(this.usuarioSeleccionado.segmentos ?? []);
    return this.segmentos.filter(segmento => idsSegmento.has(segmento.id));
  }

  get rolSeleccionadoActual(): RolSistema | null {
    if (!this.rolSeleccionado) {
      return null;
    }

    return this.roles.find(rol => rol.idRol === Number(this.rolSeleccionado)) ?? null;
  }

  get puedeConfigurarProductosPorSegmento(): boolean {
    const rol = this.rolSeleccionadoActual;
    if (!rol) return false;

    // Si el backend ya expone el flag real de la tabla, usarlo
    if (rol.cargarArchivos != null) {
      return rol.cargarArchivos === 1;
    }

    // Fallback por código de rol (mientras el backend no esté desplegado)
    const codigoRol = this.textoUsuario(rol.rol, '').toUpperCase();
    return codigoRol.includes('CARGADOR') || codigoRol.startsWith('SIPRO_USUARIO_');
  }

  get puedeGuardarAsignacion(): boolean {
    return !!this.usuarioSeleccionadoId && !!this.rolSeleccionado && this.hayCambiosAsignacion;
  }

  get hayCambiosAsignacion(): boolean {
    if (!this.usuarioSeleccionadoId || !this.rolSeleccionado) {
      return false;
    }
    // El rol no es modificable desde esta sección (lo asigna Azure Entra ID).
    // Solo se detectan cambios en la selección de productos y segmentos.
    return this.obtenerFirmaProductosAsignacion() !== this.firmaProductosAsignacionInicial;
  }

  get totalProductosPermitidosSeleccionados(): number {
    if (!this.puedeConfigurarProductosPorSegmento) {
      return 0;
    }

    return this.segmentos.reduce((total, segmento) => {
      return total + (this.checkboxesPorSegmento.get(segmento.id) ?? []).filter(item => item.permitido).length;
    }, 0);
  }

  nombreSegmentoVisual(segmento: SegmentoSistema): string {
    if (segmento.id === 1) {
      return 'COLGAAP';
    }

    if (segmento.id === 2) {
      return 'FULL IFRS';
    }

    return this.textoUsuario(segmento.nombre);
  }

  nombreRolVisual(rol: RolSistema | null | undefined): string {
    if (!rol) {
      return 'Sin rol';
    }

    const codigoRol = this.textoUsuario(rol.rol, '');
    const rolDesdeCodigo = this.formatearNombreDesdeCodigoRol(codigoRol);

    return rolDesdeCodigo || this.textoUsuario(rol.perfil);
  }

  private construirCheckboxes(permitidos: number[]): void {
    const set = new Set(permitidos);
    const mapa = new Map<number, CheckboxProducto[]>();
    for (const seg of this.segmentos) {
      const items = this.productos
        .filter(p => p.idSegmento === seg.id)
        .map(p => ({ idProducto: p.idProducto, titulo: p.titulo, idSegmento: p.idSegmento, permitido: set.has(p.idProducto) }));
      mapa.set(seg.id, items);
    }
    this.checkboxesPorSegmento = mapa;
  }

  seleccionarProductosSegmento(idSegmento: number, todos: boolean): void {
    (this.checkboxesPorSegmento.get(idSegmento) ?? []).forEach(item => {
      item.permitido = todos;
    });
  }

  // onRolSeleccionadoChange ya no se usa: el rol viene de Azure Entra ID y no es editable.

  solicitarGuardarAsignacion(): void {
    if (!this.usuarioSeleccionadoId || !this.rolSeleccionado) {
      this.asignacionError = !this.usuarioSeleccionadoId
        ? 'Seleccione un usuario.'
        : 'El usuario aún no tiene rol asignado desde Azure Entra ID. Debe iniciar sesión primero.';
      return;
    }

    if (!this.hayCambiosAsignacion) {
      this.asignacionError = 'No hay cambios para guardar.';
      return;
    }

    // Para roles cargadores: obligatorio tener al menos 1 producto seleccionado
    if (this.puedeConfigurarProductosPorSegmento && this.totalProductosPermitidosSeleccionados === 0) {
      this.asignacionError = 'El rol cargador requiere al menos un producto asignado. Seleccione productos antes de guardar.';
      return;
    }

    this.confirmacionAsignacionVisible = true;
  }

  cerrarConfirmacionAsignacion(): void {
    if (this.guardandoAsignacion) {
      return;
    }

    this.confirmacionAsignacionVisible = false;
  }

  confirmarGuardarAsignacion(): void {
    if (!this.usuarioSeleccionadoId || !this.rolSeleccionado) {
      this.asignacionError = 'Seleccione un usuario y un rol.';
      return;
    }

    const productosPorSegmento = this.segmentos.map(seg => ({
      idSegmento: seg.id,
      idsProductosPermitidos: (this.checkboxesPorSegmento.get(seg.id) ?? []).filter(i => i.permitido).map(i => i.idProducto)
    }));
    const req: AsignacionUsuario = { idRol: Number(this.rolSeleccionado), productosPorSegmento };
    this.guardandoAsignacion = true;
    this.asignacionError = '';
    this.asignacionExito = '';

    this.parametrosService.guardarAsignacionUsuario(Number(this.usuarioSeleccionadoId), req).subscribe({
      next: res => {
        this.guardandoAsignacion = false;
        this.confirmacionAsignacionVisible = false;
        if (res.success) {
          this.asignacionExito = res.mensaje || 'Asignación guardada.';
          this.recargarUsuarios();
          this.actualizarEstadoInicialAsignacion(this.rolSeleccionado);
        } else {
          this.asignacionError = res.mensaje || 'Error al guardar.';
        }
      },
      error: error => {
        this.guardandoAsignacion = false;
        this.confirmacionAsignacionVisible = false;
        this.asignacionError = error?.error?.message
          || error?.error?.mensaje
          || error?.message
          || 'Error de conexión.';
      }
    });
  }

  private recargarUsuarios(): void {
    this.parametrosService.getUsuarios().subscribe(u => {
      this.usuarios = this.normalizarUsuarios(u);
      this.usuarioSeleccionado = this.usuarios.find(usuario => usuario.idUsuario === this.usuarioSeleccionadoId) ?? this.usuarioSeleccionado;
      this.sincronizarBusquedaUsuarioSeleccionado();
    });
  }

  private sincronizarBusquedaUsuarioSeleccionado(): void {
    this.busquedaUsuarioAsignacion = this.usuarioSeleccionado
      ? this.textoUsuario(this.usuarioSeleccionado.nombreCompleto, '')
      : '';
  }

  private construirTextoBusquedaUsuario(usuario: UsuarioResumen): string {
    return [usuario.nombreCompleto, usuario.usuario, usuario.correo, usuario.areaNombre]
      .map(valor => this.normalizarTextoBusqueda(valor))
      .join(' ');
  }

  private normalizarTextoBusqueda(valor: string | null | undefined): string {
    return this.normalizarTextoLegible(valor).toLowerCase();
  }

  private normalizarUsuarios(usuarios: UsuarioResumen[]): UsuarioResumen[] {
    return usuarios.map(usuario => ({
      ...usuario,
      usuario: this.textoUsuario(usuario.usuario, ''),
      nombres: this.textoUsuario(usuario.nombres, ''),
      apellidos: this.textoUsuario(usuario.apellidos, ''),
      nombreCompleto: this.textoUsuario(usuario.nombreCompleto, ''),
      correo: this.textoUsuario(usuario.correo, ''),
      areaNombre: this.textoUsuario(usuario.areaNombre, ''),
      cargoJefe: this.textoUsuario(usuario.cargoJefe, ''),
      nombreLider: this.textoUsuario(usuario.nombreLider, ''),
      nombreRolActual: this.textoUsuario(usuario.nombreRolActual, '')
    }));
  }

  private normalizarRoles(roles: RolSistema[]): RolSistema[] {
    return roles.map(rol => ({
      ...rol,
      rol: this.textoUsuario(rol.rol, ''),
      perfil: this.textoUsuario(rol.perfil, ''),
      descripcion: this.textoUsuario(rol.descripcion, ''),
      cargarArchivos: rol.cargarArchivos ?? null,
      aprobar: rol.aprobar ?? null,
      modificarParametros: rol.modificarParametros ?? null
    }));
  }

  private formatearNombreDesdeCodigoRol(codigoRol: string): string {
    const base = codigoRol
      .replace(/^SIPRO_/i, '')
      .replace(/_/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();

    if (!base) {
      return '';
    }

    return base
      .toLowerCase()
      .split(' ')
      .filter(Boolean)
      .map(palabra => palabra.charAt(0).toUpperCase() + palabra.slice(1))
      .join(' ');
  }

  private normalizarSegmentos(segmentos: SegmentoSistema[]): SegmentoSistema[] {
    return segmentos.map(segmento => ({
      ...segmento,
      nombre: this.textoUsuario(segmento.nombre, '')
    }));
  }

  private actualizarEstadoInicialAsignacion(idRol: number | null): void {
    this.rolAsignacionInicial = idRol;
    this.firmaProductosAsignacionInicial = this.obtenerFirmaProductosAsignacion();
  }

  private obtenerFirmaProductosAsignacion(): string {
    return Array.from(this.checkboxesPorSegmento.entries())
      .sort(([segmentoA], [segmentoB]) => segmentoA - segmentoB)
      .map(([idSegmento, items]) => {
        const ids = items
          .filter(item => item.permitido)
          .map(item => item.idProducto)
          .sort((a, b) => a - b)
          .join(',');

        return `${idSegmento}:${ids}`;
      })
      .join('|');
  }

  private normalizarTextoLegible(valor: string | null | undefined): string {
    const texto = (valor ?? '').trim();
    if (!texto) {
      return '';
    }

    if (!/[ÃÂâ]/.test(texto)) {
      return texto;
    }

    try {
      const bytes = Uint8Array.from(texto, caracter => caracter.charCodeAt(0));
      const reparado = new TextDecoder('utf-8').decode(bytes).trim();
      return reparado || texto;
    } catch {
      return texto;
    }
  }

  // ── Sección 3: Cambio de Líder ───────────────────────────────────────────

  get usuariosFiltrados(): UsuarioResumen[] {
    // Solo rol 1 (Usuario Cargador) — únicos con líder asignado
    const cargadores = this.usuarios.filter(u => u.idRolActual === 1);
    const q = this.busquedaUsuario.toLowerCase();
    return q
      ? cargadores.filter(u =>
          u.nombreCompleto.toLowerCase().includes(q) ||
          u.usuario.toLowerCase().includes(q) ||
          (u.nombreLider ?? '').toLowerCase().includes(q))
      : cargadores;
  }

  get usuariosAprobadores(): UsuarioResumen[] {
    // Solo rol 2 (SIPRO_Usuario_Aprobador) puede ser líder aprobador de cargadores.
    // Los roles 3 (SIPRO_Soporte_Tecnico), 5 (SIPRO_Auditoria) y 6 (SIPRO_Admin_Permisos)
    // no gestionan aprobaciones operativas.
    return this.usuarios.filter(u => u.idRolActual === 2);
  }

  get usuariosLiderFiltrados(): UsuarioResumen[] {
    const q = this.busquedaLider.toLowerCase();
    return q
      ? this.usuariosAprobadores.filter(u =>
          u.nombreCompleto.toLowerCase().includes(q) ||
          u.usuario.toLowerCase().includes(q))
      : this.usuariosAprobadores;
  }

  actualizarBusquedaLider(valor: string): void {
    this.busquedaLider = valor;
    this.nuevoLiderId = null;
    this.mostrarOpcionesLider = true;
  }

  restaurarBusquedaLider(): void {
    setTimeout(() => {
      this.mostrarOpcionesLider = false;
      if (!this.nuevoLiderId) {
        const lider = this.usuariosAprobadores.find(u => u.idUsuario === this.nuevoLiderId);
        this.busquedaLider = lider ? lider.nombreCompleto : '';
      }
    }, 150);
  }

  seleccionarLider(u: UsuarioResumen): void {
    this.nuevoLiderId = u.idUsuario;
    this.busquedaLider = u.nombreCompleto;
    this.mostrarOpcionesLider = false;
  }

  /**
   * Consulta en Azure Entra ID el grupo real de todos los cargadores visibles
   * y determina si coincide con el rol asignado en PostgreSQL.
   */
  verificarRolesAzureCargadores(): void {
    const cargadores = this.usuarios.filter(u => u.idRolActual === 1);
    if (cargadores.length === 0) return;

    this.validandoAzureCargadores = true;
    this.validacionAzureCompletada = false;
    const ids = cargadores.map(u => u.idUsuario);

    this.parametrosService.validarRolesAzureMasivo(ids)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: resp => {
          this.validacionAzureCargadores = resp.validaciones ?? {};
          this.validandoAzureCargadores = false;
          this.validacionAzureCompletada = true;
        },
        error: () => {
          this.validandoAzureCargadores = false;
          this.mostrarToast('No fue posible consultar Azure Entra ID. Verifique la configuración.', 'error');
        }
      });
  }

  /**
   * Retorna el estado del badge Azure para un cargador dado su ID:
   * - 'ok': rol en Azure coincide con el rol en PostgreSQL
   * - 'discrepancia': rol en Azure existe pero es diferente al de PostgreSQL
   * - 'sin-grupo': usuario no tiene ningún grupo SIPRO en Azure
   * - 'sin-verificar': aún no se ha consultado Azure
   */
  estadoBadgeAzure(idUsuario: number): 'ok' | 'discrepancia' | 'sin-grupo' | 'sin-verificar' {
    const resultado = this.validacionAzureCargadores[String(idUsuario)];
    if (!resultado) return 'sin-verificar';
    if (!resultado.encontrado) return 'sin-grupo';
    const u = this.usuarios.find(x => x.idUsuario === idUsuario);
    if (!u) return 'sin-verificar';
    // Comparar idRol de Azure con idRolActual de PostgreSQL
    return resultado.idRol === u.idRolActual ? 'ok' : 'discrepancia';
  }

  tooltipBadgeAzure(idUsuario: number): string {
    const resultado = this.validacionAzureCargadores[String(idUsuario)];
    if (!resultado) return 'Pendiente de verificar con Azure';
    if (!resultado.encontrado) return resultado.mensaje ?? 'Sin grupo SIPRO en Azure Entra ID';
    const u = this.usuarios.find(x => x.idUsuario === idUsuario);
    const rolAzure = resultado.nombreRol ?? resultado.grupoAd ?? '?';
    if (!u || resultado.idRol === u.idRolActual) {
      return `Azure confirma: ${rolAzure}`;
    }
    return `Discrepancia: Azure tiene "${rolAzure}" pero PostgreSQL tiene "${u.nombreRolActual ?? '?'}"`;
  }

  get seleccionadosCount(): number {
    return this.usuariosSeleccionados.size;
  }

  toggleSeleccionUsuario(id: number): void {
    if (this.usuariosSeleccionados.has(id)) {
      this.usuariosSeleccionados.delete(id);
    } else {
      this.usuariosSeleccionados.add(id);
    }
  }

  isUsuarioSeleccionado(id: number): boolean {
    return this.usuariosSeleccionados.has(id);
  }

  toggleSeleccionarTodos(): void {
    this.seleccionarTodosLider = !this.seleccionarTodosLider;
    if (this.seleccionarTodosLider) {
      this.usuariosFiltrados.forEach(u => this.usuariosSeleccionados.add(u.idUsuario));
    } else {
      this.usuariosSeleccionados.clear();
    }
  }

  get liderSeleccionadoTienePendientes(): boolean {
    // Verificar si alguno de los usuarios seleccionados tiene su líder actual con pendientes
    const seleccionados = this.usuariosFiltrados.filter(u => this.usuariosSeleccionados.has(u.idUsuario));
    return seleccionados.some(u => u.idLider != null && (this.pendientesLider.get(u.idLider) ?? 0) > 0);
  }

  usuarioLiderLogin(idLider: number | null): string {
    if (!idLider) return '';
    return this.usuarios.find(u => u.idUsuario === idLider)?.usuario ?? '';
  }

  abrirConfirmacionLider(): void {
    if (!this.nuevoLiderId || this.usuariosSeleccionados.size === 0) {
      this.liderError = 'Seleccione al menos un usuario y un nuevo líder.';
      return;
    }
    this.liderError = '';
    this.liderExito = '';
    this.confirmacionLiderVisible = true;
  }

  cerrarConfirmacionLider(): void {
    if (this.guardandoCambioLider) return;
    this.confirmacionLiderVisible = false;
  }

  aplicarCambioLider(): void {
    if (!this.nuevoLiderId || this.usuariosSeleccionados.size === 0) {
      this.liderError = 'Seleccione al menos un usuario y un nuevo líder.';
      return;
    }

    const req: CambioLiderRequest = {
      idsUsuariosAfectados: [...this.usuariosSeleccionados],
      idNuevoLider: Number(this.nuevoLiderId)
    };

    this.guardandoCambioLider = true;
    this.liderError = '';
    this.liderExito = '';

    this.parametrosService.aplicarCambioLider(req).subscribe({
      next: (res: CambioLiderResultado) => {
        this.guardandoCambioLider = false;
        this.confirmacionLiderVisible = false;
        if (res.success) {
          this.liderTransferidas = res.totalTransferidas ?? 0;
          this.liderExito = res.mensaje || 'Líder actualizado correctamente.';
          this.usuariosSeleccionados.clear();
          this.seleccionarTodosLider = false;
          this.nuevoLiderId = null;
          this.busquedaLider = '';
          this.recargarUsuarios();
        } else {
          this.liderError = res.mensaje || 'Error al aplicar el cambio.';
        }
      },
      error: () => { this.guardandoCambioLider = false; this.confirmacionLiderVisible = false; this.liderError = 'Error de conexión al aplicar el cambio de líder.'; }
    });
  }

  // ── Sección 4: Nuevo Usuario ──────────────────────────────────────────────

  agregarProductoNuevoUsuario(): void {
    const idsSegmentos = new Set(this.nuevoUsuario.idsSegmentos);
    if (this.productoParaAgregar == null || idsSegmentos.size === 0) {
      return;
    }

    const producto = this.productos.find(p =>
      p.idProducto === this.productoParaAgregar &&
      idsSegmentos.has(p.idSegmento)
    );
    if (!producto) {
      this.productoParaAgregar = null;
      return;
    }

    const yaSeleccionado = this.productosSeleccionadosNuevo.some(p => p.idProducto === producto.idProducto);
    if (!yaSeleccionado) {
      this.productosSeleccionadosNuevo = [...this.productosSeleccionadosNuevo, producto];
      this.sincronizarIdsProductosNuevoUsuario();
    }

    this.productoParaAgregar = null;
  }

  quitarProductoNuevoUsuario(id: number): void {
    this.productosSeleccionadosNuevo = this.productosSeleccionadosNuevo.filter(p => p.idProducto !== id);
    this.sincronizarIdsProductosNuevoUsuario();
  }

  quitarProductoNuevoUsuarioByTitulo(titulo: string): void {
    this.productosSeleccionadosNuevo = this.productosSeleccionadosNuevo.filter(p => p.titulo !== titulo);
    this.sincronizarIdsProductosNuevoUsuario();
  }

  limpiarProductosNuevoUsuario(): void {
    this.productosSeleccionadosNuevo = [];
    this.sincronizarIdsProductosNuevoUsuario();
  }

  onCorreoNuevoUsuarioChange(correo: string): void {
    this.nuevoUsuario.correo = correo;
    const atIndex = correo.indexOf('@');
    this.nuevoUsuario.usuario = atIndex > 0 ? correo.substring(0, atIndex).toLowerCase() : '';
  }

  toggleSegmentoNuevoUsuario(id: number): void {
    const idx = this.nuevoUsuario.idsSegmentos.indexOf(id);
    if (idx >= 0) {
      this.nuevoUsuario.idsSegmentos.splice(idx, 1);
    } else {
      this.nuevoUsuario.idsSegmentos.push(id);
    }

    this.sincronizarProductosNuevoUsuarioConSegmentos();
  }

  isSegmentoSeleccionado(id: number): boolean {
    return this.nuevoUsuario.idsSegmentos.includes(id);
  }

  get productosDisponiblesNuevoUsuario(): ProductoCatalogo[] {
    const idsSegmentos = new Set(this.nuevoUsuario.idsSegmentos);
    const idsSeleccionados = new Set(this.nuevoUsuario.idsProductos);

    if (idsSegmentos.size === 0) {
      return [];
    }

    return this.productos.filter(producto => {
      const perteneceASegmento = idsSegmentos.has(producto.idSegmento);
      return perteneceASegmento && !idsSeleccionados.has(producto.idProducto);
    });
  }

  productoNuevoUsuarioLabel(producto: ProductoCatalogo): string {
    return `${producto.titulo} (${this.nombreSegmento(producto.idSegmento)})`;
  }

  // ── Getters para sección 4 ────────────────────────────────────────────────

  get rolNuevoUsuarioEsCargador(): boolean {
    if (!this.nuevoUsuario.idRol) return false;
    const rol = this.roles.find(r => r.idRol === this.nuevoUsuario.idRol);
    return rol?.cargarArchivos === 1;
  }

  get lideresDisponiblesNuevoUsuarioFiltrados(): UsuarioResumen[] {
    // Solo aprobadores (aprobar = 1, id_rol = 2) pueden ser líder de un cargador
    const termino = this.busquedaLiderNuevoUsuario.trim().toLowerCase();
    return this.usuarios.filter(u => {
      const rolU = this.roles.find(r => r.idRol === u.idRolActual);
      if (!rolU || rolU.aprobar !== 1) return false;
      if (!termino) return true;
      return (u.nombreCompleto ?? '').toLowerCase().includes(termino)
          || (u.correo ?? '').toLowerCase().includes(termino)
          || (u.usuario ?? '').toLowerCase().includes(termino);
    });
  }

  onBusquedaLiderNuevoUsuarioChange(valor: string): void {
    this.busquedaLiderNuevoUsuario = valor;
    this.liderSeleccionadoNuevoUsuario = null;
    this.nuevoUsuario.idLider = null;
    this.mostrarOpcionesLiderNuevoUsuarioFlag = true;
  }

  seleccionarLiderNuevoUsuario(u: UsuarioResumen): void {
    this.liderSeleccionadoNuevoUsuario = u;
    this.nuevoUsuario.idLider = u.idUsuario;
    this.busquedaLiderNuevoUsuario = `${u.nombreCompleto} — ${u.correo}`;
    this.mostrarOpcionesLiderNuevoUsuarioFlag = false;
  }

  restaurarBusquedaLiderNuevoUsuario(): void {
    setTimeout(() => {
      this.mostrarOpcionesLiderNuevoUsuarioFlag = false;
      if (!this.liderSeleccionadoNuevoUsuario) {
        this.busquedaLiderNuevoUsuario = '';
        this.nuevoUsuario.idLider = null;
      }
    }, 200);
  }

  registrarNuevoUsuario(): void {
    const u = this.nuevoUsuario;
    // Validaciones comunes
    if (!u.nombres.trim() || !u.apellidos.trim() || !u.correo.trim()
        || !u.usuario.trim() || !u.areaNombre.trim() || !u.idRol) {
      this.nuevoUsuarioError = 'Complete todos los campos obligatorios (nombres, apellidos, correo, área y rol).';
      return;
    }

    // Correo duplicado (case-insensitive) contra lista local
    const correoDuplicado = this.usuarios.some(
      usr => usr.correo.trim().toLowerCase() === u.correo.trim().toLowerCase()
    );
    if (correoDuplicado) {
      this.nuevoUsuarioError = 'Ya existe un usuario registrado con ese correo corporativo.';
      return;
    }

    // Validaciones específicas por rol
    if (this.rolNuevoUsuarioEsCargador) {
      if (!u.idLider) {
        this.nuevoUsuarioError = 'El líder aprobador es obligatorio para usuarios cargadores.';
        return;
      }
      if (u.idsSegmentos.length === 0) {
        this.nuevoUsuarioError = 'Debe seleccionar al menos un segmento para el usuario cargador.';
        return;
      }
      if (u.idsProductos.length === 0) {
        this.nuevoUsuarioError = 'Debe seleccionar al menos un producto para el usuario cargador.';
        return;
      }
    }

    this.guardandoNuevoUsuario = true;
    this.nuevoUsuarioError = '';
    this.nuevoUsuarioExito = '';

    this.parametrosService.registrarNuevoUsuario(u).subscribe({
      next: res => {
        this.guardandoNuevoUsuario = false;
        if (res.success) {
          this.nuevoUsuarioExito = res.mensaje || 'Usuario registrado exitosamente.';
          this.resetNuevoUsuario();
          this.recargarUsuarios();
        } else {
          this.nuevoUsuarioError = res.mensaje || 'Error al registrar usuario.';
        }
      },
      error: () => { this.guardandoNuevoUsuario = false; this.nuevoUsuarioError = 'Error de conexión.'; }
    });
  }

  private resetNuevoUsuario(): void {
    this.nuevoUsuario = {
      nombres: '', apellidos: '', correo: '', usuario: '',
      areaNombre: '', idRol: 0, idsSegmentos: [1, 2], idsProductos: [], idLider: null
    };
    this.productoParaAgregar = null;
    this.productosSeleccionadosNuevo = [];
    this.busquedaLiderNuevoUsuario = '';
    this.liderSeleccionadoNuevoUsuario = null;
    this.mostrarOpcionesLiderNuevoUsuarioFlag = false;
  }

  private sincronizarIdsProductosNuevoUsuario(): void {
    this.nuevoUsuario.idsProductos = this.productosSeleccionadosNuevo.map(p => p.idProducto);
  }

  private sincronizarProductosNuevoUsuarioConSegmentos(): void {
    const idsSegmentos = new Set(this.nuevoUsuario.idsSegmentos);
    this.productosSeleccionadosNuevo = this.productosSeleccionadosNuevo.filter(p => idsSegmentos.has(p.idSegmento));
    this.sincronizarIdsProductosNuevoUsuario();

    if (this.productoParaAgregar != null) {
      const productoDisponible = this.productosDisponiblesNuevoUsuario.some(p => p.idProducto === this.productoParaAgregar);
      if (!productoDisponible) {
        this.productoParaAgregar = null;
      }
    }
  }

  // ── Sección 5: Catálogo de Productos ─────────────────────────────────────

  editarProducto(p: ProductoCatalogo): void {
    this.productoEditandoId = p.idProducto;
    this.productoFormTitulo = `Editar: ${p.titulo}`;
    this.productoForm = {
      titulo: this.normalizarTituloProducto(p.titulo),
      idSegmento: p.idSegmento,
      activo: p.activo,
      nombreArchivoPermitido: p.nombreArchivoPermitido,
      nombreControlPermitido: p.nombreControlPermitido
    };
    this.productoError = '';
    this.productoExito = '';
  }

  cancelarEdicionProducto(): void {
    this.productoEditandoId = null;
    this.productoFormTitulo = 'Asistente para Nuevo Producto';
    this.productoForm = { titulo: '', idSegmento: 0, activo: true, nombreArchivoPermitido: '', nombreControlPermitido: '' };
    this.productoError = '';
  }

  get requiereNombreControlProducto(): boolean {
    return Number(this.productoForm.idSegmento) === 2;
  }

  get productosFiltrados(): ProductoCatalogo[] {
    const q = this.busquedaProducto.trim().toLowerCase();
    if (!q) return this.productos;
    return this.productos.filter(p =>
      p.titulo.toLowerCase().includes(q) ||
      (p.nombreArchivoPermitido ?? '').toLowerCase().includes(q)
    );
  }

  get productoFormCompleto(): boolean {
    const titulo = (this.productoForm.titulo ?? '').trim();
    const idSegmento = Number(this.productoForm.idSegmento) || 0;
    const nombreArchivoPermitido = (this.productoForm.nombreArchivoPermitido ?? '').trim();
    const nombreControlPermitido = (this.productoForm.nombreControlPermitido ?? '').trim();

    return Boolean(
      titulo &&
      idSegmento &&
      nombreArchivoPermitido &&
      (!this.requiereNombreControlProducto || nombreControlPermitido)
    );
  }

  onTituloProductoChange(valor: string): void {
    this.productoForm.titulo = this.normalizarTituloProducto(valor);
  }

  onNombreArchivoPermitidoChange(valor: string): void {
    this.productoForm.nombreArchivoPermitido = this.normalizarNombreTecnicoProducto(valor);
  }

  onNombreControlPermitidoChange(valor: string): void {
    this.productoForm.nombreControlPermitido = this.normalizarNombreTecnicoProducto(valor);
  }

  onSegmentoProductoChange(valor: number | string): void {
    this.productoForm.idSegmento = Number(valor) || 0;
    if (!this.requiereNombreControlProducto) {
      this.productoForm.nombreControlPermitido = '';
    }
  }

  private normalizarTituloProducto(valor: string): string {
    const texto = (valor ?? '').trimStart();
    if (!texto) {
      return '';
    }

    return texto
      .split(/(\s+)/)
      .map((fragmento, index) => this.normalizarFragmentoTituloProducto(fragmento, index === 0))
      .join('');
  }

  private normalizarFragmentoTituloProducto(fragmento: string, esPrimerFragmento: boolean): string {
    if (!fragmento || /^\s+$/.test(fragmento)) {
      return fragmento;
    }

    if (/^[\p{Lu}0-9]{2,4}$/u.test(fragmento)) {
      return fragmento;
    }

    const texto = fragmento.toLowerCase();
    if (!esPrimerFragmento) {
      return texto;
    }

    return `${texto.charAt(0).toUpperCase()}${texto.slice(1)}`;
  }

  private normalizarNombreTecnicoProducto(valor: string): string {
    // Convertir a mayúsculas y colapsar guiones bajos múltiples a uno solo.
    // Ejemplo: "TEXT__TEXT" -> "TEXT_TEXT"
    return (valor ?? '').toUpperCase().replace(/_+/g, '_');
  }

  private terminaConMascaraFecha(valor: string): boolean {
    return valor.trim().endsWith('_AAAAMMDD');
  }

  private empiezaConCtrlGuion(valor: string): boolean {
    return valor.trim().toUpperCase().startsWith('CTRL-');
  }

  private construirProductoRequestValidado(): ProductoRequest | null {
    const titulo = this.normalizarTituloProducto(this.productoForm.titulo).trim();
    const idSegmento = Number(this.productoForm.idSegmento) || 0;
    const nombreArchivoPermitido = this.normalizarNombreTecnicoProducto(this.productoForm.nombreArchivoPermitido).trim();
    const nombreControlPermitido = this.requiereNombreControlProducto
      ? this.normalizarNombreTecnicoProducto(this.productoForm.nombreControlPermitido).trim()
      : '';

    this.productoForm.titulo = titulo;
    this.productoForm.idSegmento = idSegmento;
    this.productoForm.nombreArchivoPermitido = nombreArchivoPermitido;
    this.productoForm.nombreControlPermitido = nombreControlPermitido;

    if (!titulo || !idSegmento || !nombreArchivoPermitido || (this.requiereNombreControlProducto && !nombreControlPermitido)) {
      this.productoError = 'Complete todos los campos obligatorios.';
      return null;
    }

    if (!this.terminaConMascaraFecha(nombreArchivoPermitido)) {
      this.productoError = 'El Nombre Archivo Permitido debe terminar estrictamente en _AAAAMMDD.';
      return null;
    }

    // Validar formato: solo letras mayúsculas, dígitos y guiones bajos simples (TEXT_TEXT, nunca TEXT__TEXT)
    const patronNombreArchivo = /^[A-Z0-9]+(_[A-Z0-9]+)*$/;
    if (!patronNombreArchivo.test(nombreArchivoPermitido)) {
      this.productoError = 'El Nombre Archivo Permitido solo admite letras mayúsculas, dígitos y guiones bajos simples entre palabras (TEXT_TEXT_AAAAMMDD).';
      return null;
    }

    // Validar unicidad local: (nombreArchivoPermitido + idSegmento) como llave de negocio
    const duplicado = this.productos.find(p =>
      p.nombreArchivoPermitido === nombreArchivoPermitido &&
      p.idSegmento === idSegmento &&
      p.idProducto !== (this.productoEditandoId ?? -1)
    );
    if (duplicado) {
      this.productoError = `Ya existe el producto «${duplicado.titulo}» con ese nombre de archivo en ese segmento.`;
      return null;
    }

    if (this.requiereNombreControlProducto && !this.empiezaConCtrlGuion(nombreControlPermitido)) {
      this.productoError = 'El Nombre Control Permitido debe iniciar estrictamente con CTRL-.';
      return null;
    }

    if (this.requiereNombreControlProducto && !this.terminaConMascaraFecha(nombreControlPermitido)) {
      this.productoError = 'El Nombre Control Permitido debe terminar estrictamente en _AAAAMMDD.';
      return null;
    }

    return {
      titulo,
      idSegmento,
      activo: this.productoForm.activo,
      nombreArchivoPermitido,
      nombreControlPermitido
    };
  }

  solicitarGuardarProducto(): void {
    const req = this.construirProductoRequestValidado();
    if (!req) {
      return;
    }

    this.productoError = '';
    this.confirmacionProductoAccion = this.productoEditandoId ? 'editar' : 'crear';
    this.confirmacionProductoNombre = req.titulo;
    this.confirmacionProductoVisible = true;
  }

  solicitarDesactivarProducto(p: ProductoCatalogo): void {
    this._productoDesactivarPendiente = p;
    this.confirmacionProductoAccion = p.activo ? 'desactivar' : 'activar';
    this.confirmacionProductoNombre = p.titulo;
    this.productoError = '';
    this.productoExito = '';
    this.confirmacionProductoVisible = true;
  }

  cerrarConfirmacionProducto(): void {
    this.confirmacionProductoVisible = false;
    this._productoDesactivarPendiente = null;
  }

  confirmarOperacionProducto(): void {
    if (this.confirmacionProductoAccion === 'desactivar') {
      this._ejecutarDesactivarProducto();
    } else {
      this.guardarProducto();
    }
  }

  private _ejecutarDesactivarProducto(): void {
    const p = this._productoDesactivarPendiente;
    if (!p) return;

    this.guardandoProducto = true;
    const req: ProductoRequest = {
      titulo: p.titulo,
      idSegmento: p.idSegmento,
      activo: !p.activo,
      nombreArchivoPermitido: p.nombreArchivoPermitido,
      nombreControlPermitido: p.nombreControlPermitido
    };

    this.parametrosService.actualizarProducto(p.idProducto, req).subscribe({
      next: res => {
        this.guardandoProducto = false;
        this.confirmacionProductoVisible = false;
        this._productoDesactivarPendiente = null;
        if (res.success) {
          this.productoExito = res.mensaje || `Producto ${req.activo ? 'activado' : 'desactivado'} correctamente.`;
          this.parametrosService.getProductos().subscribe(lista => this.productos = lista);
        } else {
          this.productoError = res.mensaje || 'Error al cambiar estado del producto.';
        }
      },
      error: () => {
        this.guardandoProducto = false;
        this.confirmacionProductoVisible = false;
        this.productoError = 'Error de conexión.';
      }
    });
  }

  guardarProducto(): void {
    const req = this.construirProductoRequestValidado();
    if (!req) {
      return;
    }

    this.guardandoProducto = true;
    this.productoError = '';
    this.productoExito = '';

    const obs = this.productoEditandoId
      ? this.parametrosService.actualizarProducto(this.productoEditandoId, req)
      : this.parametrosService.crearProducto(req);

    obs.subscribe({
      next: res => {
        this.guardandoProducto = false;
        this.confirmacionProductoVisible = false;
        if (res.success) {
          this.productoExito = res.mensaje || 'Producto guardado.';
          this.cancelarEdicionProducto();
          this.parametrosService.getProductos().subscribe(p => this.productos = p);
        } else {
          this.productoError = res.mensaje || 'Error al guardar producto.';
        }
      },
      error: (err: any) => {
        this.guardandoProducto = false;
        this.confirmacionProductoVisible = false;
        const detalle: string = err?.error?.mensaje || err?.error?.message || err?.message || '';
        if (detalle.toLowerCase().includes('duplicada') || detalle.toLowerCase().includes('duplicate') || detalle.toLowerCase().includes('already exists') || err?.status === 409) {
          this.productoError = 'Ya existe un producto con ese ID. La secuencia de la base de datos está desincronizada. Ejecute en el panel SQL: SELECT setval(\'productos_id_producto_seq\', (SELECT MAX(id_producto) FROM productos)); e intente de nuevo.';
        } else if (detalle) {
          this.productoError = detalle;
        } else {
          this.productoError = 'Error de conexión al guardar. Verifique que el backend esté activo e intente nuevamente.';
        }
      }
    });
  }

  nombreSegmento(idSegmento: number): string {
    return this.segmentos.find(s => s.id === idSegmento)?.nombre ?? '—';
  }

  // ── Helpers generales ─────────────────────────────────────────────────────

  private mostrarMensajeGlobal(msg: string, tipo: 'success' | 'error'): void {
    this.mensajeGlobal = msg;
    this.mensajeGlobalTipo = tipo;
    setTimeout(() => { this.mensajeGlobal = ''; this.mensajeGlobalTipo = ''; }, 4000);
  }

  tipoExcepcion(exc: ExcepcionVentanaCarga): 'Ampliación' | 'Reducción' | 'Sin datos' {
    const ventanaBase = this.resolverVentanaBasePorPeriodo(exc.periodoValoracion);
    const ventanaExcepcion = this.resolverVentanaExcepcion(exc);

    if (!ventanaBase || !ventanaExcepcion) {
      return 'Sin datos';
    }

    const duracionBase = ventanaBase.fin.getTime() - ventanaBase.inicio.getTime();
    const duracionExcepcion = ventanaExcepcion.fin.getTime() - ventanaExcepcion.inicio.getTime();

    return duracionExcepcion >= duracionBase ? 'Ampliación' : 'Reducción';
  }

  formatFechaHora(fecha: string | null, hora: string | null): string {
    if (!fecha) return '—';
    const [y, m, d] = fecha.split('-');
    return `${d}/${m}/${y} ${hora ?? ''}`.trim();
  }

  usuarioCreacion(idUsuario?: number, creadoPorUsuario?: string): string {
    if (creadoPorUsuario?.trim()) {
      return creadoPorUsuario.trim();
    }

    if (idUsuario == null) {
      return '—';
    }

    return this.usuarios.find(usuario => usuario.idUsuario === idUsuario)?.usuario ?? `Usuario #${idUsuario}`;
  }

  areasUnicas(): string[] {
    return [...new Set(this.usuarios.map(u => u.areaNombre).filter(Boolean))];
  }

  private obtenerFechaReferenciaReglaBase(): Date {
    if (this.excepcionForm.anioCorte && this.excepcionForm.mesCorte) {
      return new Date(this.excepcionForm.anioCorte, this.excepcionForm.mesCorte - 1, 1);
    }

    const hoy = new Date();
    return new Date(hoy.getFullYear(), hoy.getMonth(), 1);
  }

  private resolverFechaReglaBase(offset: number): Date {
    return this.resolverFechaReglaBaseDesdeReferencia(this.obtenerFechaReferenciaReglaBase(), offset);
  }

  private resolverFechaReglaBaseDesdeReferencia(referencia: Date, offset: number): Date {
    const inicioMes = new Date(referencia.getFullYear(), referencia.getMonth(), 1);

    if (offset < 0) {
      const ultimoDiaMes = new Date(inicioMes.getFullYear(), inicioMes.getMonth() + 1, 0);
      ultimoDiaMes.setDate(ultimoDiaMes.getDate() + offset);
      return ultimoDiaMes;
    }

    if (offset === 0) {
      return new Date(inicioMes.getFullYear(), inicioMes.getMonth() + 1, 0);
    }

    return new Date(inicioMes.getFullYear(), inicioMes.getMonth() + 1, offset);
  }

  private formatearFechaReglaBase(offset: number): string {
    return new Intl.DateTimeFormat('es-CO', {
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    }).format(this.resolverFechaReglaBase(offset));
  }

  private construirAyudaReglaBase(offset: number): string {
    const mesReferencia = this.periodoReferenciaReglaBaseLabel;

    if (offset < 0) {
      const dias = Math.abs(offset);
      return `Equivale a ${dias} día${dias === 1 ? '' : 's'} antes del último día del mes de corte de referencia (${mesReferencia}).`;
    }

    if (offset === 0) {
      return `Equivale al último día del mes de corte de referencia (${mesReferencia}).`;
    }

    return `Equivale a ${offset} día${offset === 1 ? '' : 's'} después del cierre del mes de corte de referencia (${mesReferencia}).`;
  }

  private resolverVentanaBasePorPeriodo(periodoValoracion: string): { inicio: Date; fin: Date } | null {
    if (!this.reglaBase) {
      return null;
    }

    const referencia = this.obtenerReferenciaDesdePeriodo(periodoValoracion);
    if (!referencia) {
      return null;
    }

    const fechaInicio = this.resolverFechaReglaBaseDesdeReferencia(referencia, this.reglaBase.offsetDiasApertura);
    const fechaFin = this.resolverFechaReglaBaseDesdeReferencia(referencia, this.reglaBase.offsetDiasCierre);
    const inicio = this.combinarFechaHoraLocal(this.formatearFechaIso(fechaInicio), this.reglaBase.horaApertura);
    const fin = this.combinarFechaHoraLocal(this.formatearFechaIso(fechaFin), this.reglaBase.horaCierre);

    if (!inicio || !fin) {
      return null;
    }

    return { inicio, fin };
  }

  private resolverVentanaExcepcion(exc: ExcepcionVentanaCarga): { inicio: Date; fin: Date } | null {
    const inicio = this.combinarFechaHoraLocal(exc.fechaAperturaOverride, exc.horaAperturaOverride);
    const fin = this.combinarFechaHoraLocal(exc.fechaCierreOverride, exc.horaCierreOverride);

    if (!inicio || !fin) {
      return null;
    }

    return { inicio, fin };
  }

  private obtenerReferenciaDesdePeriodo(periodoValoracion: string): Date | null {
    const [anio, mes] = periodoValoracion.split('-').map(Number);
    if (!anio || !mes) {
      return null;
    }

    return new Date(anio, mes - 1, 1);
  }

  private actualizarPeriodoValoracionDesdeCorte(): void {
    this.excepcionForm.periodoValoracion = this.construirPeriodoValoracion(
      this.excepcionForm.anioCorte,
      this.excepcionForm.mesCorte
    ) ?? '';
  }

  private construirPeriodoValoracion(anio: number | null, mes: number | null): string | null {
    if (!anio || !mes) {
      return null;
    }

    return this.formatearFechaIso(new Date(anio, mes, 0));
  }

  private normalizarNumeroSeleccionado(value: number | string | null): number | null {
    if (value === null || value === '') {
      return null;
    }

    const normalizado = Number(value);
    return Number.isNaN(normalizado) ? null : normalizado;
  }

  private combinarFechaHoraLocal(fecha: string | null | undefined, hora: string | null | undefined): Date | null {
    if (!fecha || !hora) {
      return null;
    }

    const [anio, mes, dia] = fecha.split('-').map(Number);
    const [horas, minutos] = hora.split(':').map(Number);

    if ([anio, mes, dia, horas, minutos].some(Number.isNaN)) {
      return null;
    }

    return new Date(anio, mes - 1, dia, horas, minutos);
  }

  private formatearFechaIso(fecha: Date): string {
    const anio = fecha.getFullYear();
    const mes = String(fecha.getMonth() + 1).padStart(2, '0');
    const dia = String(fecha.getDate()).padStart(2, '0');
    return `${anio}-${mes}-${dia}`;
  }
}