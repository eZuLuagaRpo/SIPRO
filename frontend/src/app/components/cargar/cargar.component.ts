import { Component, DestroyRef, OnDestroy, OnInit, inject } from '@angular/core';
import * as XLSX from 'xlsx';
import { HttpEvent, HttpEventType } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ValidationService } from '../../services/validation.service';
import { AuthService } from '../../services/auth.service';
import { Producto, Segmento, ValidationResult, ValidationJobStartResponse, ValidationJobStatusResponse, RangoFechaCorteResponse, VentanaCargaResponse } from '../../models/validation.model';
import { User, UsuarioPermisos } from '../../models/user.model';
import { Subscription, forkJoin, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LoadingComponent } from '../shared/loading/loading.component';

/**
 * Opción visible en el selector de mes del calendario de fecha de corte.
 */
interface MesOption {
  value: number;
  label: string;
}

/**
 * Administra la carga, validación y envío a aprobación de archivos manuales.
 */
@Component({
  selector: 'app-cargar',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterModule, LoadingComponent],
  templateUrl: './cargar.component.html',
  styleUrls: ['./cargar.component.scss']
})
export class CargarComponent implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  private initialDataLoadedForUserId: string | number | null = null;

  uploadForm: FormGroup;
  productos: Producto[] = [];
  segmentos: Segmento[] = [];
  selectedFile: File | null = null;
  selectedControlFile: File | null = null;
  loading = false; // Controls the loading overlay
  loadingDetail = '';
  loadingProgress: number | null = null;
  loadingIndeterminate = true;
  showErrorModal = false;
  showSuccessModal = false;
  validationResult: ValidationResult | null = null;
  currentUser$ = this.authService.currentUser$;
  currentUser: User | null = null;
  permisos: UsuarioPermisos | null = null;
  isDragging = false;
  isDraggingControl = false;
  sidebarOpen = false;
  subMenuOpen = true;
  currentDateTime = '';
  currentIp = '127.0.0.1';
  fileMsg = '';
  controlFileMsg = '';
  minDate = '';
  showApprovalModal = false;
  showSinDatosConfirmModal = false; // Modal de confirmación para certificación sin datos

  // Estado del checkbox "Sin Datos"
  sinDatos = false;

  // Selector de fecha de corte (solo último día del mes) - Calendario visual
  meses: MesOption[] = [
    { value: 1, label: 'Enero' },
    { value: 2, label: 'Febrero' },
    { value: 3, label: 'Marzo' },
    { value: 4, label: 'Abril' },
    { value: 5, label: 'Mayo' },
    { value: 6, label: 'Junio' },
    { value: 7, label: 'Julio' },
    { value: 8, label: 'Agosto' },
    { value: 9, label: 'Septiembre' },
    { value: 10, label: 'Octubre' },
    { value: 11, label: 'Noviembre' },
    { value: 12, label: 'Diciembre' }
  ];
  aniosDisponibles: number[] = [];
  selectedMes: number | null = null;
  selectedAnio: number | null = null;
  approvalCountdown = 30;

  // Calendario visual
  calendarioAbierto = false;
  calendarioMes: number = new Date().getMonth() + 1; // 1-12
  calendarioAnio: number = new Date().getFullYear();
  calendarioDias: (number | null)[] = [];
  fechaSeleccionada: Date | null = null;
  approvalNombreLider = '';
  approvalCorreoLider = '';
  approvalProductDisplay = '';
  approvalSegmentDisplay = '';
  approvalDateDisplay = '';

  // Configuración de rango de fecha de corte (valores del backend)
  rangoFechaCorte: RangoFechaCorteResponse | null = null;

  // Mensaje dinámico para el loader
  loadingMessage = 'Cargando...';

  activeValidationJobId: string | null = null;

  private approvalInterval: ReturnType<typeof setInterval> | null = null;
  private dateTimeInterval: ReturnType<typeof setInterval> | null = null;
  private validationStatusSubscription: Subscription | null = null;
  private protectedActivityActive = false;

  // Estado de ventana de carga
  ventanaCarga: VentanaCargaResponse | null = null;
  ventanaCargaLoading = false;

  get userName(): string {
    if (this.currentUser) {
      const nombres = this.currentUser.nombres || '';
      const apellidos = this.currentUser.apellidos || '';
      return `${nombres} ${apellidos}`.trim() || this.currentUser.name || 'Usuario';
    }
    return 'Usuario';
  }

  get liderAprobador(): string {
    return this.currentUser?.jefeNombre || 'No asignado';
  }

  get puedeCargar(): boolean {
    return this.authService.puedeAccederCargaManual();
  }

  /** True cuando el usuario tiene permiso de cargar pero no tiene productos asignados en RBAC. */
  get sinProductosAsignados(): boolean {
    const asignados = this.permisos?.productosAsignados ?? [];
    return this.puedeCargar && asignados.length === 0;
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

  get isFullIfrs(): boolean {
    const seg = this.uploadForm.get('segmento')?.value;
    return seg === 2 || seg === '2';
  }

  get canValidate(): boolean {
    if (this.loading || !!this.activeValidationJobId) {
      return false;
    }
    // Bloquear si está fuera de ventana de carga
    if (this.ventanaCarga && !this.ventanaCarga.dentroDeVentana) {
      return false;
    }
    // Modo Sin Datos: solo necesita producto, segmento y fecha
    if (this.sinDatos) {
      return !!(this.uploadForm.get('producto')?.valid &&
        this.uploadForm.get('segmento')?.valid &&
        this.uploadForm.get('fechaCorte')?.valid);
    }
    // Modo normal: formulario completo + archivo válido
    const baseOk = this.uploadForm.valid && this.selectedFile !== null && this.isFileNameValid;
    if (!baseOk) return false;
    // Full IFRS requiere además el archivo control
    if (this.isFullIfrs) {
      return this.selectedControlFile !== null && this.isControlFileNameValid;
    }
    return true;
  }

  /**
   * Valida si el nombre del archivo coincide con el producto Y la fecha de corte seleccionada.
   * Verifica que el archivo tenga el formato correcto: PREFIJO_YYYYMMDD.xlsx
   */
  get isFileNameValid(): boolean {
    if (!this.selectedFile) return true; // Si no hay archivo, no mostrar error

    const idProducto = this.uploadForm.get('producto')?.value;
    const fechaCorte = this.uploadForm.get('fechaCorte')?.value;

    if (!idProducto) return true; // Si no hay producto seleccionado, no validar aún
    if (!fechaCorte) return true; // Si no hay fecha seleccionada, no validar aún

    const selectedProd = this.productos.find(p => p.idProducto == idProducto);
    if (!selectedProd || !selectedProd.nombreArchivoPermitido) return true;

    // Extraer el prefijo esperado (sin la fecha AAAAMMDD)
    const prefijoEsperado = selectedProd.nombreArchivoPermitido.replace('AAAAMMDD', '');

    // Construir el nombre esperado completo
    const fechaFormateada = fechaCorte.replace(/-/g, ''); // YYYY-MM-DD -> YYYYMMDD
    const nombreEsperado = prefijoEsperado + fechaFormateada;

    // Quitar extensión del archivo
    let baseArchivo = this.selectedFile.name;
    if (baseArchivo.toLowerCase().endsWith('.xlsx')) {
      baseArchivo = baseArchivo.substring(0, baseArchivo.length - 5);
    } else if (baseArchivo.toLowerCase().endsWith('.csv')) {
      baseArchivo = baseArchivo.substring(0, baseArchivo.length - 4);
    }

    // Validar que el nombre del archivo coincida exactamente con lo esperado
    return baseArchivo === nombreEsperado;
  }

  /**
   * Mensaje de error cuando el nombre del archivo no es válido.
   */
  get fileNameErrorMessage(): string {
    if (!this.selectedFile) return '';

    const idProducto = this.uploadForm.get('producto')?.value;
    const fechaCorte = this.uploadForm.get('fechaCorte')?.value;

    if (!idProducto || !fechaCorte) return '';

    const selectedProd = this.productos.find(p => p.idProducto == idProducto);
    if (!selectedProd || !selectedProd.nombreArchivoPermitido) return '';

    if (!this.isFileNameValid) {
      const prefijoEsperado = selectedProd.nombreArchivoPermitido.replace('AAAAMMDD', '');
      const fechaFormateada = fechaCorte.replace(/-/g, '');
      const nombreEsperado = prefijoEsperado + fechaFormateada;
      return `El nombre del archivo no coincide. Se espera: ${nombreEsperado}.xlsx o ${nombreEsperado}.csv`;
    }

    return '';
  }

  get isControlFileNameValid(): boolean {
    if (!this.selectedControlFile) return true;
    const idProducto = this.uploadForm.get('producto')?.value;
    const fechaCorte = this.uploadForm.get('fechaCorte')?.value;
    if (!idProducto || !fechaCorte) return true;
    const selectedProd = this.productos.find(p => p.idProducto == idProducto);
    if (!selectedProd || !selectedProd.nombreControlPermitido) return true;
    const fechaFormateada = fechaCorte.replace(/-/g, '');
    const nombreEsperado = selectedProd.nombreControlPermitido.replace('AAAAMMDD', fechaFormateada);
    let base = this.selectedControlFile.name;
    if (base.toLowerCase().endsWith('.txt')) {
      base = base.substring(0, base.length - 4);
    }
    return base === nombreEsperado;
  }

  get controlFileNameErrorMessage(): string {
    if (!this.selectedControlFile) return '';
    const idProducto = this.uploadForm.get('producto')?.value;
    const fechaCorte = this.uploadForm.get('fechaCorte')?.value;
    if (!idProducto || !fechaCorte) return '';
    const selectedProd = this.productos.find(p => p.idProducto == idProducto);
    if (!selectedProd || !selectedProd.nombreControlPermitido) return '';
    if (!this.isControlFileNameValid) {
      const fechaFormateada = fechaCorte.replace(/-/g, '');
      const nombreEsperado = selectedProd.nombreControlPermitido.replace('AAAAMMDD', fechaFormateada);
      return `El nombre del archivo control no coincide. Se espera: ${nombreEsperado}.txt`;
    }
    return '';
  }

  get fileNameHelpText(): string {
    const idProducto = this.uploadForm.get('producto')?.value;
    const fechaCorte = this.uploadForm.get('fechaCorte')?.value;

    if (!idProducto || !fechaCorte) {
      return 'En espera de selección de producto y fecha...';
    }

    // Buscar el objeto producto seleccionado para obtener su nombre/patrón
    const selectedProd = this.productos.find(p => p.idProducto == idProducto);

    if (!selectedProd) {
      return 'Producto no válido...';
    }

    const dateFormatted = fechaCorte.replace(/-/g, '');
    let exampleName = '';

    // Si tenemos el patrón exacto desde BD, lo usamos
    if (selectedProd.nombreArchivoPermitido) {
      exampleName = selectedProd.nombreArchivoPermitido.replace('AAAAMMDD', dateFormatted);
    } else {
      // Fallback: Generar basado en el título
      const productoFormatted = selectedProd.titulo.toUpperCase().replace(/\s+/g, '_');
      exampleName = `${productoFormatted}_${dateFormatted}`;
    }

    let texto = `Para que tu archivo se cargue correctamente en la plataforma, asegúrate de que el nombre esté escrito totalmente en mayúsculas y, si el nombre del producto tiene varias palabras, conéctalas usando guiones bajos (_) en lugar de espacios. No olvides incluir al final la fecha de corte de la información siguiendo el formato (YYYYMMDD); por ejemplo, para este caso el formato correcto debe ser: ${exampleName}`;

    if (this.isFullIfrs && selectedProd.nombreControlPermitido) {
      const ctrlEsperado = selectedProd.nombreControlPermitido.replace('AAAAMMDD', dateFormatted);
      texto += `. Además, para el segmento Full IFRS es obligatorio adjuntar el archivo control con el nombre: ${ctrlEsperado}.txt`;
    }

    return texto;
  }

  constructor(
    private fb: FormBuilder,
    private validationService: ValidationService,
    private authService: AuthService,
    private router: Router
  ) {
    const today = new Date();
    today.setDate(today.getDate() - 7);
    const yyyy = today.getFullYear();
    const mm = String(today.getMonth() + 1).padStart(2, '0');
    const dd = String(today.getDate()).padStart(2, '0');
    this.minDate = `${yyyy}-${mm}-${dd}`;

    // Generar años disponibles (año actual y 5 años hacia atrás/adelante)
    const currentYear = new Date().getFullYear();
    for (let y = currentYear - 5; y <= currentYear + 1; y++) {
      this.aniosDisponibles.push(y);
    }
    // Seleccionar año actual por defecto
    this.selectedAnio = currentYear;

    // Inicializar calendario
    this.generarCalendario();

    // Inicializar formulario reactivo
    this.uploadForm = this.fb.group({
      producto: ['', Validators.required],
      segmento: ['', Validators.required],
      fechaCorte: ['', [Validators.required]],
      descripcion: ['', [Validators.minLength(5), Validators.maxLength(400)]]
    });
  }

  ngOnInit() {
    this.currentUser$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((user: User | null) => {
      const userId = user?.idUsuario ?? null;
      this.currentUser = user;
      this.permisos = this.authService.getPermisos();

      if (!user) {
        return;
      }

      if (!this.puedeCargar) {
        this.router.navigate(['/inicio']);
        return;
      }

      if (userId !== this.initialDataLoadedForUserId) {
        this.initialDataLoadedForUserId = userId;
        this.loadInitialData();
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
    if (this.approvalInterval !== null) {
      clearInterval(this.approvalInterval);
      this.approvalInterval = null;
    }
    this.stopValidationPolling();
    this.endProtectedActivity();
  }

  loadInitialData() {
    this.setLoadingState('Cargando información inicial...', {
      detail: 'Productos, segmentos y rangos de fecha.',
      progress: null,
      indeterminate: true
    });
    forkJoin({
      segmentos: this.validationService.getSegmentos(),
      rangoFechaCorte: this.validationService.getRangoFechaCorte()
    }).subscribe({
      next: (result: { segmentos: Segmento[], rangoFechaCorte: RangoFechaCorteResponse }) => {
        this.productos = [];
        this.segmentos = result.segmentos;
        this.rangoFechaCorte = result.rangoFechaCorte;

        if (this.segmentos.length === 1) {
          const unicoSegmento = this.segmentos[0];
          this.uploadForm.patchValue({ segmento: unicoSegmento.id, producto: '' });
          this.loadProductosPorSegmento(unicoSegmento.id);
        }

        // Delay de 1 segundo extra para dar tiempo a la UI de renderizar
        setTimeout(() => {
          this.clearLoadingState();
        }, 1000);
      },
      error: (error) => {
        console.error('Error cargando datos iniciales', error);
        this.clearLoadingState();
        alert('Error cargando listas desplegables. Por favor intente recargar la página.');
      }
    });
  }

  onSegmentoChange(): void {
    const segmentoId = this.uploadForm.get('segmento')?.value;
    this.uploadForm.patchValue({ producto: '' });
    this.productos = [];
    this.selectedFile = null;
    this.fileMsg = '';
    this.selectedControlFile = null;
    this.controlFileMsg = '';

    if (!segmentoId) {
      return;
    }

    this.loadProductosPorSegmento(segmentoId);
  }

  private loadProductosPorSegmento(segmentoId: number | string): void {
    this.validationService.getProductos(segmentoId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (productos: Producto[]) => {
          this.productos = productos;
        },
        error: (error) => {
          console.error('Error cargando productos por segmento', error);
          this.productos = [];
          alert('No fue posible cargar los productos habilitados para el segmento seleccionado.');
        }
      });
  }

  private getSelectedSegmento(): Segmento | undefined {
    const segmentoId = this.uploadForm.get('segmento')?.value;
    return this.segmentos.find(segmento => `${segmento.id}` === `${segmentoId}`);
  }

  private setLoadingState(
    message: string,
    options?: { detail?: string; progress?: number | null; indeterminate?: boolean }
  ): void {
    this.loading = true;
    this.loadingMessage = message;
    this.loadingDetail = options?.detail ?? '';
    this.loadingProgress = options?.progress ?? null;
    this.loadingIndeterminate = options?.indeterminate ?? true;
  }

  private clearLoadingState(): void {
    this.loading = false;
    this.loadingMessage = 'Cargando...';
    this.loadingDetail = '';
    this.loadingProgress = null;
    this.loadingIndeterminate = true;
  }

  private beginProtectedActivity(): void {
    if (!this.protectedActivityActive) {
      this.authService.beginProtectedActivity();
      this.protectedActivityActive = true;
    }
  }

  private endProtectedActivity(): void {
    if (this.protectedActivityActive) {
      this.authService.endProtectedActivity();
      this.protectedActivityActive = false;
    }
  }

  private stopValidationPolling(): void {
    if (this.validationStatusSubscription) {
      this.validationStatusSubscription.unsubscribe();
      this.validationStatusSubscription = null;
    }
    this.activeValidationJobId = null;
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

  private updateDateTime() {
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
    const ampm = hours >= 12 ? 'p.m.' : 'a.m.';
    hours = hours % 12 || 12;

    this.currentDateTime = `${dayName}, ${day} de ${monthName} de ${year}, ${hours}:${minutes} ${ampm}`;
  }

  toggleSidebar() {
    this.sidebarOpen = !this.sidebarOpen;
  }

  toggleSubMenu() {
    this.subMenuOpen = !this.subMenuOpen;
  }

  // Método eliminado en favor de loadInitialData
  // loadProductos() {
  //   this.validationService.getProductos().subscribe({
  //     next: (productos) => this.productos = productos,
  //     error: (error) => console.error('Error cargando productos', error)
  //   });
  // }

  // loadSegmentos() {
  //   this.validationService.getSegmentos().subscribe({
  //     next: (segmentos) => this.segmentos = segmentos,
  //     error: (error) => console.error('Error cargando segmentos', error)
  //   });
  // }

  async onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      await this.validateAndSetFile(file);
    }
    // Resetear el value del input para que el evento change se dispare
    // incluso si el usuario selecciona el mismo archivo de nuevo.
    input.value = '';
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = true;
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = false;
  }

  async onDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = false;

    if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
      const file = event.dataTransfer.files[0];
      await this.validateAndSetFile(file);
    }
  }

  async validateAndSetFile(file: File): Promise<void> {
    const lowerName = file.name.toLowerCase();
    if (lowerName.endsWith('.xlsx') || lowerName.endsWith('.csv')) {
      // Solo validar extensión aquí. Las validaciones internas de estructura del archivo
      // (múltiples hojas, datos fuera de rango, espacios en control .txt) se realizan
      // en el backend durante la validación completa y se muestran consolidadas en el modal.
      this.selectedFile = file;
      this.fileMsg = '';
    } else {
      const msg = this.isFullIfrs
        ? 'Solo se permiten archivos .xlsx o .csv para la planilla. El archivo control (.txt) debe cargarse en el campo correspondiente.'
        : 'Solo se permiten archivos .xlsx o .csv';
      alert(msg);
    }
  }


  validateAndSetControlFile(file: File) {
    if (file.name.toLowerCase().endsWith('.txt')) {
      this.selectedControlFile = file;
      this.controlFileMsg = '';
    } else {
      alert('El archivo control debe ser un archivo .txt');
    }
  }

  onControlFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.validateAndSetControlFile(input.files[0]);
    }
    input.value = '';
  }

  onControlDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDraggingControl = true;
  }

  onControlDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDraggingControl = false;
  }

  onControlDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDraggingControl = false;
    if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
      this.validateAndSetControlFile(event.dataTransfer.files[0]);
    }
  }

  removeFile() {
    this.selectedFile = null;
    this.fileMsg = '';
  }

  removeControlFile() {
    this.selectedControlFile = null;
    this.controlFileMsg = '';
  }

  onSubmit() {
    if (this.loading || this.activeValidationJobId) {
      return;
    }

    if (this.uploadForm.invalid || !this.selectedFile) {
      alert('Por favor completa los campos requeridos, corrige la descripción si la diligencias y selecciona un archivo');
      return;
    }

    this.beginProtectedActivity();
    this.setLoadingState('Subiendo archivo...', {
      progress: 0,
      indeterminate: false,
      detail: `0 B de ${this.getFileSize(this.selectedFile.size)}`
    });
    const formData = new FormData();
    const idProducto = this.uploadForm.get('producto')?.value;

    // Buscar el nombre del producto seleccionado para mostrarlo (opcional) o enviarlo si se requiere
    const selectedProd = this.productos.find(p => p.idProducto == idProducto);
    const nombreProducto = selectedProd ? selectedProd.titulo : '';
    const selectedSegment = this.getSelectedSegmento();

    formData.append('idProducto', idProducto); // Nuevo campo ID
    formData.append('idSegmento', this.uploadForm.get('segmento')?.value);
    formData.append('producto', nombreProducto); // Enviamos también el nombre por compatibilidad
    formData.append('segmento', selectedSegment?.nombre || '');
    formData.append('fechaCorte', this.uploadForm.get('fechaCorte')?.value);
    formData.append('descripcion', this.uploadForm.get('descripcion')?.value || '');
    formData.append('usuarioAdmin', this.currentUser?.usuario || this.userName);
    formData.append('archivo', this.selectedFile, this.selectedFile.name);

    if (this.isFullIfrs && this.selectedControlFile) {
      formData.append('archivoControl', this.selectedControlFile, this.selectedControlFile.name);
    }

    this.validationService.iniciarValidacionArchivo(formData)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (event: HttpEvent<ValidationJobStartResponse>) => {
        if (event.type === HttpEventType.Sent) {
          this.setLoadingState('Subiendo archivo...', {
            progress: 0,
            indeterminate: false,
            detail: `0 B de ${this.getFileSize(this.selectedFile?.size || 0)}`
          });
          return;
        }

        if (event.type === HttpEventType.UploadProgress) {
          const total = event.total ?? this.selectedFile?.size ?? 0;
          const loaded = event.loaded;
          const progress = total > 0 ? Math.round((loaded / total) * 100) : null;
          this.setLoadingState('Subiendo archivo...', {
            progress,
            indeterminate: false,
            detail: `${this.getFileSize(loaded)} de ${this.getFileSize(total)}`
          });
          return;
        }

        if (event.type === HttpEventType.Response) {
          const response = event.body;
          if (!response?.jobId) {
            this.stopValidationPolling();
            this.clearLoadingState();
            this.endProtectedActivity();
            alert('No se recibió identificador de job para la validación. Intente nuevamente.');
            return;
          }

          this.activeValidationJobId = response.jobId;
          this.setLoadingState(
            response.duplicateRequest
              ? 'Ya existe una validación en curso para este archivo.'
              : 'Archivo recibido. Validando información...',
            {
              progress: null,
              indeterminate: true,
              detail: response.message
            }
          );
          this.startValidationPolling(response.jobId);
        }
      },
      error: (error: any) => {
        this.stopValidationPolling();
        this.clearLoadingState();
        this.endProtectedActivity();
        alert('Error al procesar el archivo: ' + (error.error?.message || 'Error desconocido'));
      }
    });
  }

  private startValidationPolling(jobId: string): void {
    this.validationStatusSubscription?.unsubscribe();
    this.validationStatusSubscription = timer(0, 2000).pipe(
      takeUntilDestroyed(this.destroyRef),
      switchMap(() => this.validationService.obtenerEstadoValidacion(jobId))
    ).subscribe({
      next: (status: ValidationJobStatusResponse) => {
        this.updateLoadingFromValidationJob(status);

        if (status.terminal) {
          this.finalizeValidationJob(status);
        }
      },
      error: (error: any) => {
        this.stopValidationPolling();
        this.clearLoadingState();
        this.endProtectedActivity();
        alert('Error consultando el estado de la validación: ' + (error.error?.message || error.message || 'Error desconocido'));
      }
    });
  }

  private updateLoadingFromValidationJob(status: ValidationJobStatusResponse): void {
    const detailParts: string[] = [];
    const totalRows = typeof status.totalRows === 'number' && status.totalRows > 0
      ? status.totalRows
      : null;

    if (typeof status.processedRows === 'number' && status.processedRows > 0) {
      const processedLabel = status.processedRows.toLocaleString('es-CO');
      if (totalRows) {
        detailParts.push(`Filas procesadas: ${processedLabel} de ${totalRows.toLocaleString('es-CO')}`);
      } else {
        detailParts.push(`Filas procesadas: ${processedLabel}`);
      }
    }
    if (status.message) {
      detailParts.push(status.message);
    }

    this.setLoadingState(this.getValidationLoadingMessage(status), {
      progress: status.progressPercent ?? null,
      indeterminate: status.progressPercent == null,
      detail: detailParts.join(' | ')
    });
  }

  private getValidationLoadingMessage(status: ValidationJobStatusResponse): string {
    switch (status.phase) {
      case 'RECEIVED':
      case 'PREPARING':
        return 'Preparando validación...';
      case 'VALIDATING':
        return 'Validando reglas del archivo...';
      case 'SUMMARIZING':
        return 'Consolidando resultados...';
      case 'DONE':
        return 'Finalizando validación...';
      default:
        return 'Procesando archivo...';
    }
  }

  private finalizeValidationJob(status: ValidationJobStatusResponse): void {
    const result = status.result;

    this.stopValidationPolling();
    this.setLoadingState('Validación completada', {
      progress: 100,
      indeterminate: false,
      detail: status.message || 'Preparando resultado final...'
    });

    window.setTimeout(() => {
      this.clearLoadingState();
      this.endProtectedActivity();
      this.authService.extendSession();

      if (!result) {
        alert('La validación finalizó sin resultado disponible. Intente nuevamente.');
        return;
      }

      this.validationResult = result;
      this.showErrorModal = false;
      this.showSuccessModal = false;

      if (result.status === 'ERROR' || (result.errores && result.errores.length > 0)) {
        this.showErrorModal = true;
        return;
      }

      this.showSuccessModal = true;
    }, 650);
  }

  closeErrorModal() {
    this.showErrorModal = false;
  }

  closeSuccessModal() {
    this.showSuccessModal = false;
    // No limpiar el formulario - permitir que el usuario corrija y vuelva a validar
  }

  requestApproval() {
    const canReuseValidatedUpload = !!this.validationResult?.archivoTemporalDisponible && !!this.validationResult?.loteId;

    if ((!this.selectedFile && !canReuseValidatedUpload) || !this.currentUser) {
      alert('Error: Datos incompletos para la solicitud.');
      return;
    }

    this.beginProtectedActivity();
    this.setLoadingState('Enviando solicitud...', {
      detail: canReuseValidatedUpload
        ? 'Usando el archivo ya validado para registrar la planilla y notificar al líder. No se volverá a validar ni a cargar desde tu navegador.'
        : 'Registrando la planilla y notificando al líder aprobador.',
      progress: null,
      indeterminate: true
    });

    // Buscar el objeto producto seleccionado
    const selectedProd = this.productos.find(p => p.idProducto == this.uploadForm.get('producto')?.value);

    // Obtener nombre y aplicar Title Case (Capitalizar cada palabra)
    let nombreProducto = selectedProd ? selectedProd.titulo : '';
    if (nombreProducto) {
      nombreProducto = this.toTitleCase(nombreProducto);
    }

    const selectedSegment = this.getSelectedSegmento();
    const nombreSegmento = selectedSegment?.nombre || '';

    // Datos del JSON
    const requestData = {
      usuario: this.currentUser.usuario,
      idProducto: selectedProd ? selectedProd.idProducto : null,
      idSegmento: selectedSegment ? selectedSegment.id : null,
      producto: nombreProducto, // Enviamos nombre formateado (Ej: Canales De Distribución)
      segmento: nombreSegmento,
      descripcion: this.uploadForm.get('descripcion')?.value || '',
      fechaCorte: this.uploadForm.get('fechaCorte')?.value,
      nombreArchivo: this.selectedFile?.name || null,
      pesoArchivo: this.selectedFile?.size ?? null,
      validacionLoteId: canReuseValidatedUpload ? this.validationResult?.loteId : null,
      sinDatos: false // Flag explícito para flujo normal con archivo
    };

    // Crear FormData multipart
    const formData = new FormData();
    formData.append('datos', JSON.stringify(requestData));
    if (!canReuseValidatedUpload && this.selectedFile) {
      formData.append('archivo', this.selectedFile);
    }
    // Full IFRS (id_segmento = 2): enviar también el archivo de control
    if (this.isFullIfrs && this.selectedControlFile) {
      formData.append('archivoControl', this.selectedControlFile);
    }

    // Guardar datos para el modal de éxito antes de enviar
    this.approvalProductDisplay = nombreProducto;
    this.approvalSegmentDisplay = nombreSegmento;
    this.approvalDateDisplay = this.uploadForm.get('fechaCorte')?.value;

    this.validationService.solicitarAprobacion(formData).subscribe({
      next: (response: any) => {
        // Transición fluida: Cerrar spinner, abrir modal éxito
        this.clearLoadingState();
        this.endProtectedActivity();
        this.showSuccessModal = false; // Cerrar el modal de "Validación correcta" si estaba abierto

        // Capturar datos del líder desde la respuesta del backend
        this.approvalNombreLider = response.nombreLider || this.currentUser?.jefeNombre || 'No asignado';
        this.approvalCorreoLider = response.correoLider || 'N/A';

        this.showApprovalModal = true;
        this.startApprovalCountdown();
      },
      error: (error: any) => {
        this.clearLoadingState();
        this.endProtectedActivity();
        const mensajeBase = error.error?.mensaje || error.message || 'Error desconocido';
        const mensajeReintento = canReuseValidatedUpload
          ? ' El archivo validado sigue disponible temporalmente en el servidor. Puedes reintentar la solicitud sin volver a cargarlo ni revalidarlo.'
          : '';
        alert('Error al enviar la solicitud: ' + mensajeBase + mensajeReintento);
      }
    });
  }

  /**
   * Convierte un texto a Title Case (Capitaliza primera letra de cada palabra)
   * Ej: "canales de distribución" -> "Canales De Distribución"
   */
  private toTitleCase(str: string): string {
    return str.toLowerCase().split(' ').map(word => {
      return word.charAt(0).toUpperCase() + word.slice(1);
    }).join(' ');
  }

  startApprovalCountdown() {
    this.approvalCountdown = 30;
    this.approvalInterval = setInterval(() => {
      this.approvalCountdown--;
      if (this.approvalCountdown <= 0) {
        const approvalInterval = this.approvalInterval;
        if (approvalInterval !== null) {
          clearInterval(approvalInterval);
        }
        this.closeApprovalModal();
      }
    }, 1000);
  }

  closeApprovalModal() {
    if (this.approvalInterval !== null) {
      clearInterval(this.approvalInterval);
      this.approvalInterval = null;
    }
    this.showApprovalModal = false;
    this.resetForm();
    this.router.navigate(['/inicio']);
  }

  downloadErrors() {
    if (!this.validationResult?.errores || this.validationResult.errores.length === 0) {
      return;
    }

    if (this.validationResult.loteId) {
      this.validationService.descargarErrores(this.validationResult.loteId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (blob: Blob) => this.downloadBlob(blob, 'txt'),
          error: () => this.downloadErrorsFallback()
        });
      return;
    }

    this.downloadErrorsFallback();
  }

  private downloadErrorsFallback(): void {
    if (!this.validationResult?.errores || this.validationResult.errores.length === 0) {
      return;
    }

    const txtContent = [
      'REPORTE DE ERRORES DE VALIDACION SIPRO',
      '====================================',
      '',
      'Revise las observaciones listadas a continuacion, haga los ajustes necesarios y vuelva a cargar el archivo.',
      '',
      ...this.validationResult.errores.map((error, index) => `${index + 1}. ${error}`)
    ].join('\n');

    const blob = new Blob([txtContent], { type: 'text/plain;charset=utf-8;' });
    this.downloadBlob(blob, 'txt');
  }

  private downloadBlob(blob: Blob, extension: 'txt'): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;

    const now = new Date();
    const timestamp = now.getFullYear().toString() +
      (now.getMonth() + 1).toString().padStart(2, '0') +
      now.getDate().toString().padStart(2, '0') +
      now.getHours().toString().padStart(2, '0') +
      now.getMinutes().toString().padStart(2, '0') +
      now.getSeconds().toString().padStart(2, '0');

    const userPart = this.currentUser?.usuario?.toUpperCase().trim() || 'USUARIO';
    a.download = `ERRORES_${userPart}_${timestamp}.${extension}`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  getTotalCantidad(): number {
    if (!this.validationResult?.resumenDetallado) return 0;
    return this.validationResult.resumenDetallado.reduce((sum: number, item: any) => sum + (item.cantidad || 0), 0);
  }

  getTotalValor(): number {
    if (!this.validationResult?.resumenDetallado) return 0;
    return this.validationResult.resumenDetallado.reduce((sum: number, item: any) => sum + (item.total || 0), 0);
  }

  resetForm() {
    this.uploadForm.reset();
    this.selectedFile = null;
    this.validationResult = null;
    this.sinDatos = false; // Resetear checkbox sin datos
    this.ventanaCarga = null; // Resetear estado ventana de carga
    // Resetear selectores de fecha de corte
    this.selectedMes = null;
    this.selectedAnio = new Date().getFullYear();
    // Resetear calendario visual
    this.fechaSeleccionada = null;
    this.calendarioAbierto = false;
    this.calendarioMes = new Date().getMonth() + 1;
    this.calendarioAnio = new Date().getFullYear();
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  goBack() {
    this.router.navigate(['/inicio']);
  }

  onDisabledLinkClick(event: Event, modulo: string) {
    event.preventDefault();
    event.stopPropagation();
    this.router.navigate(['/inicio']);
  }

  getFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
  }

  /**
   * Calcula el último día del mes, considerando años bisiestos.
   * @param mes Número del mes (1-12)
   * @param anio Año (ej: 2026)
   * @returns Último día del mes (28, 29, 30 o 31)
   */
  getUltimoDiaMes(mes: number, anio: number): number {
    // El truco: crear fecha del día 0 del mes siguiente = último día del mes actual
    return new Date(anio, mes, 0).getDate();
  }

  /**
   * Verifica si un año es bisiesto.
   */
  esBisiesto(anio: number): boolean {
    return (anio % 4 === 0 && anio % 100 !== 0) || (anio % 400 === 0);
  }

  /**
   * Actualiza el campo fechaCorte cuando se selecciona mes o año.
   * Calcula automáticamente el último día del mes seleccionado.
   */
  onFechaCorteChange(): void {
    if (this.selectedMes && this.selectedAnio) {
      const ultimoDia = this.getUltimoDiaMes(this.selectedMes, this.selectedAnio);
      const mesStr = String(this.selectedMes).padStart(2, '0');
      const diaStr = String(ultimoDia).padStart(2, '0');
      const fechaFormateada = `${this.selectedAnio}-${mesStr}-${diaStr}`;
      this.uploadForm.get('fechaCorte')?.setValue(fechaFormateada);
    } else {
      this.uploadForm.get('fechaCorte')?.setValue('');
    }
  }

  /**
   * Obtiene el texto descriptivo del último día seleccionado.
   */
  get fechaCorteDisplay(): string {
    if (!this.fechaSeleccionada) {
      return '';
    }
    const dia = this.fechaSeleccionada.getDate();
    const mes = this.fechaSeleccionada.getMonth() + 1;
    const anio = this.fechaSeleccionada.getFullYear();
    const mesNombre = this.meses.find(m => m.value === mes)?.label || '';
    return `${dia} de ${mesNombre} de ${anio}`;
  }

  // ==================== CALENDARIO VISUAL ====================

  /**
   * Abre/cierra el calendario desplegable.
   */
  toggleCalendario(): void {
    this.calendarioAbierto = !this.calendarioAbierto;
    if (this.calendarioAbierto) {
      // Si hay fecha seleccionada, navegar a ese mes
      if (this.fechaSeleccionada) {
        this.calendarioMes = this.fechaSeleccionada.getMonth() + 1;
        this.calendarioAnio = this.fechaSeleccionada.getFullYear();
      }
      this.generarCalendario();
    }
  }

  /**
   * Cierra el calendario (para usar con click fuera).
   */
  cerrarCalendario(): void {
    this.calendarioAbierto = false;
  }

  /**
   * Navega al mes anterior o siguiente.
   */
  navegarMes(direccion: number): void {
    this.calendarioMes += direccion;

    if (this.calendarioMes > 12) {
      this.calendarioMes = 1;
      this.calendarioAnio++;
    } else if (this.calendarioMes < 1) {
      this.calendarioMes = 12;
      this.calendarioAnio--;
    }

    this.generarCalendario();
  }

  /**
   * Genera el array de días para el mes actual del calendario.
   * Incluye nulls para los espacios vacíos al inicio.
   */
  generarCalendario(): void {
    this.calendarioDias = [];

    // Primer día del mes (0 = Domingo, 6 = Sábado)
    const primerDia = new Date(this.calendarioAnio, this.calendarioMes - 1, 1).getDay();

    // Último día del mes
    const ultimoDia = this.getUltimoDiaMes(this.calendarioMes, this.calendarioAnio);

    // Agregar espacios vacíos para los días antes del 1
    for (let i = 0; i < primerDia; i++) {
      this.calendarioDias.push(null);
    }

    // Agregar todos los días del mes
    for (let dia = 1; dia <= ultimoDia; dia++) {
      this.calendarioDias.push(dia);
    }
  }

  /**
   * Obtiene el nombre del mes (1-12).
   */
  getNombreMes(mes: number): string {
    return this.meses.find(m => m.value === mes)?.label || '';
  }

  /**
   * Verifica si el mes/año del calendario está dentro del rango permitido.
   * El rango es calculado por el backend basado en la configuración de la tabla
   * sipro_parametros_rango_habilitado.
   * 
   * Si no hay configuración cargada, usa valores por defecto (1 mes atrás, 2 meses adelante).
   */
  esMesEnRango(mes: number, anio: number): boolean {
    // Usar valores del backend si están disponibles
    if (this.rangoFechaCorte) {
      const valorActual = anio * 12 + mes;
      const valorMinimo = this.rangoFechaCorte.anioMinimo * 12 + this.rangoFechaCorte.mesMinimo;
      const valorMaximo = this.rangoFechaCorte.anioMaximo * 12 + this.rangoFechaCorte.mesMaximo;

      return valorActual >= valorMinimo && valorActual <= valorMaximo;
    }

    // Fallback: valores por defecto si no hay configuración del backend
    const hoy = new Date();
    const mesActual = hoy.getMonth() + 1; // 1-12
    const anioActual = hoy.getFullYear();

    // Valores por defecto: 1 mes atrás, 2 meses adelante
    const mesesPasado = 1;
    const mesesFuturo = 2;

    // Calcular mes mínimo
    let mesMinimo = mesActual - mesesPasado;
    let anioMinimo = anioActual;
    if (mesMinimo < 1) {
      mesMinimo = 12 + mesMinimo;
      anioMinimo--;
    }

    // Calcular mes máximo
    let mesMaximo = mesActual + mesesFuturo;
    let anioMaximo = anioActual;
    if (mesMaximo > 12) {
      mesMaximo = mesMaximo - 12;
      anioMaximo++;
    }

    // Convertir a valor comparable (anio * 12 + mes)
    const valorActual = anio * 12 + mes;
    const valorMinimo = anioMinimo * 12 + mesMinimo;
    const valorMaximo = anioMaximo * 12 + mesMaximo;

    return valorActual >= valorMinimo && valorActual <= valorMaximo;
  }

  /**
   * Verifica si un día específico es el último día del mes Y está en el rango permitido.
   */
  esUltimoDiaMes(dia: number | null): boolean {
    if (dia === null) return false;

    const ultimoDia = this.getUltimoDiaMes(this.calendarioMes, this.calendarioAnio);

    // Debe ser el último día del mes Y el mes debe estar en el rango permitido
    return dia === ultimoDia && this.esMesEnRango(this.calendarioMes, this.calendarioAnio);
  }

  /**
   * Verifica si se puede navegar al mes anterior.
   */
  puedeNavgarMesAnterior(): boolean {
    let mesAnterior = this.calendarioMes - 1;
    let anioAnterior = this.calendarioAnio;
    if (mesAnterior < 1) {
      mesAnterior = 12;
      anioAnterior--;
    }
    return this.esMesEnRango(mesAnterior, anioAnterior);
  }

  /**
   * Verifica si se puede navegar al mes siguiente.
   */
  puedeNavegarMesSiguiente(): boolean {
    let mesSiguiente = this.calendarioMes + 1;
    let anioSiguiente = this.calendarioAnio;
    if (mesSiguiente > 12) {
      mesSiguiente = 1;
      anioSiguiente++;
    }
    return this.esMesEnRango(mesSiguiente, anioSiguiente);
  }

  /**
   * Verifica si un día específico está seleccionado.
   */
  esDiaSeleccionado(dia: number | null): boolean {
    if (dia === null || !this.fechaSeleccionada) return false;
    return (
      this.fechaSeleccionada.getDate() === dia &&
      this.fechaSeleccionada.getMonth() + 1 === this.calendarioMes &&
      this.fechaSeleccionada.getFullYear() === this.calendarioAnio
    );
  }

  /**
   * Selecciona un día del calendario (solo permite último día del mes).
   */
  seleccionarDia(dia: number | null): void {
    if (dia === null || !this.esUltimoDiaMes(dia)) return;

    // Crear fecha seleccionada
    this.fechaSeleccionada = new Date(this.calendarioAnio, this.calendarioMes - 1, dia);

    // Actualizar valores legacy para compatibilidad
    this.selectedMes = this.calendarioMes;
    this.selectedAnio = this.calendarioAnio;

    // Formatear fecha para el formulario (YYYY-MM-DD)
    const mesStr = String(this.calendarioMes).padStart(2, '0');
    const diaStr = String(dia).padStart(2, '0');
    const fechaFormateada = `${this.calendarioAnio}-${mesStr}-${diaStr}`;
    this.uploadForm.get('fechaCorte')?.setValue(fechaFormateada);

    // Cerrar calendario
    this.calendarioAbierto = false;

    // Validar ventana de carga para la fecha seleccionada
    this.validarVentanaCargaParaFecha(fechaFormateada);
  }

  /**
   * Consulta al backend si la fecha de corte está dentro de la ventana de carga.
   */
  private validarVentanaCargaParaFecha(fechaCorte: string): void {
    this.ventanaCargaLoading = true;
    this.ventanaCarga = null;

    this.validationService.validarVentanaCarga(fechaCorte).subscribe({
      next: (response: VentanaCargaResponse) => {
        this.ventanaCarga = response;
        this.ventanaCargaLoading = false;
      },
      error: () => {
        // En caso de error de red, no bloquear la carga
        this.ventanaCarga = null;
        this.ventanaCargaLoading = false;
      }
    });
  }

  // ==================== FUNCIONALIDAD SIN DATOS ====================

  /**
   * Maneja el cambio del checkbox "Sin Datos".
   * Al marcar, limpia el archivo seleccionado.
   */
  onSinDatosChange(): void {
    if (this.sinDatos) {
      // Limpiar archivo y estados relacionados
      this.selectedFile = null;
      this.fileMsg = '';

      // Limpiar descripción para evitar envíos accidentales
      this.uploadForm.get('descripcion')?.setValue('');
      this.uploadForm.get('descripcion')?.markAsUntouched();
      this.uploadForm.get('descripcion')?.markAsPristine();
    }
  }

  /**
   * Abre el modal de confirmación para certificación sin datos.
   */
  openSinDatosConfirmModal(): void {
    this.showSinDatosConfirmModal = true;
  }

  /**
   * Cierra el modal de confirmación sin datos.
   */
  closeSinDatosConfirmModal(): void {
    this.showSinDatosConfirmModal = false;
  }

  /**
   * Obtiene el texto de certificación para el modal de confirmación sin datos.
   */
  get sinDatosCertificacionTexto(): string {
    const nombreProducto = this.getProductoDisplay();
    const nombreSegmento = this.getSelectedSegmento()?.nombre || '';
    const fechaCorte = this.fechaCorteDisplay || '';
    const nombreUsuario = this.userName;

    return `Yo, ${nombreUsuario}, certifico que para el producto "${nombreProducto}" y segmento "${nombreSegmento}", correspondiente a la fecha de corte ${fechaCorte}, no existen operaciones ni datos a reportar para este periodo.`;
  }

  /**
   * Obtiene el nombre del producto seleccionado formateado.
   */
  private getProductoDisplay(): string {
    const idProducto = this.uploadForm.get('producto')?.value;
    const selectedProd = this.productos.find(p => p.idProducto == idProducto);
    if (!selectedProd) return '';
    return this.toTitleCase(selectedProd.titulo);
  }

  /**
   * Confirma la solicitud de aprobación sin datos.
   */
  confirmSinDatosApproval(): void {
    if (!this.currentUser) {
      alert('Error: Usuario no identificado.');
      return;
    }

    this.beginProtectedActivity();

    // Cerrar modal de confirmación y mostrar spinner
    this.showSinDatosConfirmModal = false;
    this.setLoadingState('Enviando solicitud...', {
      detail: 'Registrando certificación sin datos.',
      progress: null,
      indeterminate: true
    });

    const nombreProducto = this.getProductoDisplay();
    const selectedSegment = this.getSelectedSegmento();
    const nombreSegmento = selectedSegment?.nombre || '';

    // Datos para la solicitud sin datos
    const requestData = {
      usuario: this.currentUser.usuario,
      idProducto: this.uploadForm.get('producto')?.value,
      idSegmento: selectedSegment?.id ?? null,
      producto: nombreProducto,
      segmento: nombreSegmento,
      descripcion: this.sinDatosCertificacionTexto,
      fechaCorte: this.uploadForm.get('fechaCorte')?.value,
      nombreArchivo: null,
      pesoArchivo: 0,
      sinDatos: true
    };

    // Crear FormData
    const formData = new FormData();
    formData.append('datos', JSON.stringify(requestData));

    // Guardar datos para el modal de éxito
    this.approvalProductDisplay = nombreProducto;
    this.approvalSegmentDisplay = nombreSegmento;
    this.approvalDateDisplay = this.uploadForm.get('fechaCorte')?.value;

    this.validationService.solicitarAprobacion(formData).subscribe({
      next: (response: any) => {
        this.clearLoadingState();
        this.endProtectedActivity();

        // Capturar datos del líder desde la respuesta del backend
        this.approvalNombreLider = response.nombreLider || this.currentUser?.jefeNombre || 'No asignado';
        this.approvalCorreoLider = response.correoLider || 'N/A';

        this.showApprovalModal = true;
        this.startApprovalCountdown();
      },
      error: (error: any) => {
        this.clearLoadingState();
        this.endProtectedActivity();
        alert('Error al enviar la solicitud: ' + (error.error?.mensaje || error.message));
      }
    });
  }
}