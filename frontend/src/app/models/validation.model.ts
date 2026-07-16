/**
 * Resultado devuelto por la validación de un archivo cargado por el usuario.
 */
export interface ValidationResult {
  status: string;
  mensaje?: string;
  errores: string[];
  loteId?: string;
  sha256?: string;
  archivoTemporalDisponible?: boolean;
  totalRegistros?: number;
  registrosValidos?: number;
  registrosRechazados?: number;
  idLote?: string;
  hashArchivo?: string;
  tiempoEjecucion?: number;
  resumen?: ResumenGeneral;
  // Campos adicionales para el resumen
  resumenDetallado?: ResumenItem[];
}

/**
 * Resumen principal agregado que se muestra al finalizar una validación exitosa.
 */
export interface ResumenGeneral {
  moneda: string;
  registros: number;
  totalVlrIniObl: number;
}

/**
 * Estados de transporte usados por el flujo asíncrono de validación.
 */
export type ValidationJobTransportStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED';

/**
 * Respuesta inicial al crear un job asíncrono de validación.
 */
export interface ValidationJobStartResponse {
  jobId: string;
  status: ValidationJobTransportStatus;
  phase: string;
  message: string;
  progressPercent?: number;
  duplicateRequest: boolean;
}

/**
 * Estado consultable de un job asíncrono mientras se procesa la validación.
 */
export interface ValidationJobStatusResponse {
  jobId: string;
  status: ValidationJobTransportStatus;
  phase: string;
  message: string;
  progressPercent?: number;
  processedRows?: number;
  totalRows?: number;
  updatedAtEpochMs?: number;
  terminal: boolean;
  result?: ValidationResult;
}

/**
 * Fila del resumen detallado agrupado por moneda.
 */
export interface ResumenItem {
  moneda: string;
  cantidad: number;
  total: number;
}

/**
 * Producto disponible para seleccionar en el formulario de carga.
 */
export interface Producto {
  idProducto: string;
  titulo: string;
  idSegmento?: number;
  activo?: boolean;
  nombreArchivoPermitido?: string;
  nombreControlPermitido?: string; // Archivo control para Full IFRS (segmento 2)
  descripcion?: string;
}

/**
 * Segmento asociado a un producto dentro de los catálogos del frontend.
 */
export interface Segmento {
  id: number;
  nombre: string;
  descripcion?: string;
}

/**
 * Datos mínimos requeridos para disparar una validación desde el frontend.
 */
export interface ValidationRequest {
  producto: string;
  fechaCorte: string;
  descripcion?: string;
  usuarioAdmin?: string;
}

/**
 * Respuesta del endpoint /api/config/rango-fecha-corte
 * Contiene la configuración de rango de meses permitidos para el selector de fecha de corte.
 */
export interface RangoFechaCorteResponse {
  mesesPasado: number;
  mesesFuturo: number;
  mesActual: number;
  anioActual: number;
  mesMinimo: number;
  anioMinimo: number;
  mesMaximo: number;
  anioMaximo: number;
}

/**
 * Resumen de cargas del usuario actual (conteos por estado + última carga).
 */
export interface ResumenCargas {
  pendientes: number;
  aprobados: number;
  rechazados: number;
  ultimaCarga: string | null;
}

/**
 * Estado actual de una consolidación manual disparada desde el home o resumen.
 */
export interface ConsolidacionManualStatus {
  periodo: string;
  estado: string;
  mensaje: string;
  terminal: boolean;
  exito: boolean;
  cantidadArchivosConsolidados: number;
  cantidadRegistrosConsolidados: number;
  fechaHoraInicio?: string | null;
  fechaHoraFin?: string | null;
  mensajeError?: string | null;
}

/**
 * Respuesta del endpoint /api/planillas/cargas-pendientes.
 * Productos del mes anterior que el usuario aún no ha cargado.
 */
export interface CargasPendientes {
  fechaMesAnterior: string;     // "2026-02-28" (último día del mes anterior)
  nombreMesAnterior: string;    // "Febrero"
  productosPendientes: ProductoPendiente[];
}

export interface ProductoPendiente {
  idProducto: number;
  tituloProducto: string;
}

/**
 * Respuesta del endpoint /api/planillas/aprobaciones-pendientes.
 * Últimos 3 meses con planillas PENDIENTE de aprobar por el líder.
 */
export interface AprobacionesPendientes {
  meses: MesPendienteAprobacion[];
}

/**
 * Mes con planillas pendientes de aprobación para el líder actual.
 */
export interface MesPendienteAprobacion {
  fechaUltimoDia: string;         // "2026-02-28"
  nombreMes: string;              // "Febrero"
  anio: number;
  productosPendientes: ProductoPendienteAprobacion[];
}

/**
 * Producto o planilla pendiente que alimenta el calendario de aprobación.
 */
export interface ProductoPendienteAprobacion {
  idPlanilla: number;
  idProducto: number;
  tituloProducto: string;
}

/**
 * Respuesta del endpoint /api/config/ventana-carga
 * Informa si hoy está dentro de la ventana de carga para un periodo.
 */
export interface VentanaCargaResponse {
  dentroDeVentana: boolean;
  tipoVentana: 'REGLA' | 'EXCEPCION' | 'SIN_CONFIGURACION';
  fechaHoraApertura: string;
  fechaHoraCierre: string;
  mensaje: string;
  motivoExcepcion: string | null;
}

/**
 * Respuesta completa del resumen consolidado por período.
 */
export interface ConsolidacionResumenResponse {
  periodosDisponibles: ConsolidacionPeriodoAnual[];
  anioSeleccionado: number;
  mesSeleccionado: number;
  periodoEtiqueta: string;
  hayDatos: boolean;
  estadoConsolidacion: string;
  cantidadArchivosConsolidados: number;
  cantidadRegistrosConsolidados: number;
  cantidadRegistrosArchivoControl: number;
  totalVlrIniObl: number;
  registrosObservados: number;
  productosObservados: number;
  fechaActualizacion: string | null;
  alerta: ConsolidacionAlerta | null;
  creffos: ConsolidacionCreffosResumen | null;
  tieneDiferenciasConciliacion: boolean;
  metricasConDiferencia: number;
  metricasComparacion: ConsolidacionComparacionMetrica[];
  productos: ConsolidacionProductoResumen[];
}

/**
 * Agrupa los meses disponibles de consolidación para un año específico.
 */
export interface ConsolidacionPeriodoAnual {
  anio: number;
  meses: ConsolidacionMesDisponible[];
}

/**
 * Mes navegable en el selector del resumen consolidado.
 */
export interface ConsolidacionMesDisponible {
  numero: number;
  etiqueta: string;
  abreviatura: string;
  periodo: string;
}

/**
 * Mensaje destacado que acompaña el estado general del consolidado.
 */
export interface ConsolidacionAlerta {
  tipo: 'warning' | 'success' | 'info';
  titulo: string;
  mensaje: string;
}

/**
 * Resumen agregado por producto dentro del consolidado del período.
 */
export interface ConsolidacionProductoResumen {
  idProducto: number | null;
  nombreProducto: string;
  cantidadRegistros: number;
  totalVlrIniObl: number;
  cantidadRegistrosFullIfrs: number;
  registrosObservados: number;
  tieneDiscrepancias: boolean;
}

/**
 * Información del archivo CREFFSOS encontrado y comparado contra PostgreSQL.
 */
export interface ConsolidacionCreffosResumen {
  encontrado: boolean;
  nombreArchivo: string;
  formato: string;
  estado: 'CONSISTENTE' | 'CON_DIFERENCIAS' | 'NO_ENCONTRADO' | 'ERROR_LECTURA' | 'SIN_PERIODO' | string;
  origenLectura: 'STORAGE' | 'RUTA_COMPARTIDA' | 'SIN_ARCHIVO' | 'SIN_PERIODO' | string;
  ubicacion: string;
  cantidadColumnasEsperadas: number;
  cantidadColumnasArchivo: number;
  cantidadRegistros: number;
  totalVlrIniObl: number;
  detalle: string;
}

/**
 * Métrica individual comparada entre la consolidación interna y CREFFSOS.
 */
export interface ConsolidacionComparacionMetrica {
  codigo: string;
  etiqueta: string;
  tipoValor: 'integer' | 'currency' | string;
  valorPostgres: number;
  valorCreffos: number;
  diferencia: number;
  coincide: boolean;
}

// =================== TABLERO DE CONTROL ===================

/**
 * Estados posibles de la columna "Cargado" en el tablero de control.
 */
export type EstadoCargado = 'ARCHIVO_CARGADO' | 'SIN_DATOS' | 'PENDIENTE_CARGA' | 'NO_APLICA';

/**
 * Estados posibles de la columna "Aprobado" en el tablero de control.
 */
export type EstadoAprobado =
  | 'ARCHIVO_APROBADO'
  | 'APROBACION_SIN_DATOS'
  | 'PENDIENTE_APROBACION'
  | 'ARCHIVO_RECHAZADO'
  | 'RECHAZO_SIN_DATOS'
  | 'NO_APLICA'
  | null;

/**
 * Fila del tablero de control: estado de planilla por producto en ambos segmentos.
 */
export interface TableroControlFila {
  idProducto: number;
  nombreProducto: string;
  estadoCargadoColgaap: EstadoCargado;
  estadoAprobadoColgaap: EstadoAprobado;
  estadoCargadoFullIfrs: EstadoCargado;
  estadoAprobadoFullIfrs: EstadoAprobado;
}

/**
 * Respuesta completa del endpoint /api/planillas/tablero-control.
 */
export interface TableroControlResponse {
  periodosDisponibles: ConsolidacionPeriodoAnual[];
  anioSeleccionado: number;
  mesSeleccionado: number;
  periodoEtiqueta: string;
  filas: TableroControlFila[];
}
