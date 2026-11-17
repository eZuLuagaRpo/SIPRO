import { ConsolidacionManualStatus } from './validation.model';

export type AdminSqlOperation = 'SELECT' | 'UPDATE' | 'INSERT' | 'INSERT_OVERWRITE' | 'CREATE_INDEX';
export type AdminLogLevel = 'ALL' | 'ERROR' | 'WARN' | 'INFO' | 'DEBUG';
export type AdminLogScope = 'ALL' | 'CONSOLIDACION';

export interface AdminDashboardResponse {
  periodosDisponibles: AdminPeriodoDisponible[];
  periodoSeleccionado: string | null;
  estadoConsolidacion: ConsolidacionManualStatus | null;
  estadoPeriodo: AdminEstadoPeriodoConsolidacion | null;
  archivosAConsolidar: AdminArchivoEstado[];
  archivosNoBloqueantes: AdminArchivoEstado[];
  archivosConsolidados: AdminArchivoEstado[];
  diagnosticoDisponibilidad: AdminDiagnosticoDisponibilidad | null;
  historico: AdminHistoricoConsolidacion[];
  configuracion: AdminPanelConfig;
}

export interface AdminEstadoPeriodoConsolidacion {
  estadoVentana: string;
  fuenteVentana: string;
  fechaHoraCierreVentana: string | null;
  inicioRangoConsolidacion: string | null;
  finRangoConsolidacion: string | null;
  motivoExcepcion: string | null;
  mensajeEstado: string;
  mensajeDisponibilidad: string;
  puedeEjecutarManual: boolean;
  consolidacionEnCurso: boolean;
  ventanaIgnoradaPorConfiguracion: boolean;
  sobrescribeConsolidacionExistente: boolean;
  fechaUltimaConsolidacionExitosa: string | null;
  cantidadArchivosUltimaConsolidacion: number | null;
  cantidadRegistrosUltimaConsolidacion: number | null;
  rutaArchivoCreffos: string | null;
  cantidadPlanillasPendientes: number | null;
  cantidadPlanillasRechazadas: number | null;
  mensajeAdvertenciaOperativa: string | null;
}

export interface AdminPeriodoDisponible {
  valor: string;
  anio: number;
  mes: number;
  etiqueta: string;
  consolidado: boolean;
}

export interface AdminArchivoEstado {
  id: number | null;
  nombreArchivo: string | null;
  producto: string | null;
  pesoBytes: number | null;
  cantidadRegistros: number | null;
  estado: string;
  detalleEstado: string | null;
  rutaArchivo: string | null;
  noReportaDatos: boolean;
  descripcionSinDatos: string | null;
  nombreVisual: string | null;
}

export interface AdminDiagnosticoDisponibilidad {
  totalArchivosStorageAprobados: number | null;
  totalArchivosElegibles: number | null;
  archivosDescartados: AdminArchivoEstado[];
}

export interface AdminHistoricoConsolidacion {
  idConsolidacion: number;
  periodo: string | null;
  estado: string;
  fechaHoraInicio: string | null;
  fechaHoraFin: string | null;
  cantidadArchivos: number | null;
  cantidadRegistros: number | null;
  detalleSalida: string | null;
  observacion: string | null;
  mensajeError: string | null;
}

export interface AdminPanelConfig {
  sql: AdminSqlConfig;
  logs: AdminLogsConfig;
}

export interface AdminSqlConfig {
  operacionesHabilitadas: string[];
  tablasPermitidas: string[];
  maxFilasSelect: number;
  requiereWhereSelect: boolean;
  requiereWhereUpdate: boolean;
}

export interface AdminLogsConfig {
  streamingHabilitado: boolean;
  descargaHabilitada: boolean;
  limiteConsultaPorDefecto: number;
  maximoConsulta: number;
}

export interface AdminSqlExecuteRequest {
  tipoOperacion: AdminSqlOperation;
  sql: string;
  justificacion?: string | null;
}

export interface AdminSqlExecuteResponse {
  exito: boolean;
  tipoOperacion: string;
  mensaje: string;
  filasAfectadas: number | null;
  columnas: string[];
  filas: Array<Record<string, unknown>>;
  sqlEjecutada: string | null;
  resultadoTruncado: boolean;
  duracionMs?: number;
  sqlState?: string | null;
  tipoError?: string | null;
}

export interface AdminDeleteConsolidacionRequest {
  motivo: string;
  confirmacion: string;
}

export interface AdminDeleteConsolidacionResponse {
  exito: boolean;
  mensaje: string;
  registrosEliminados: number;
  archivosEliminados: number;
}

export interface AdminLogStreamResponse {
  latestId: number;
  cursorId: number;
  totalBuffered: number;
  items: AdminLogItem[];
  instanceId?: string;
  capturadoEn?: string;
  oldestId?: number;
  entriesLost?: boolean;
}

export interface AdminLogItem {
  id: number;
  timestamp: string;
  level: string;
  logger: string;
  thread: string;
  scope: string;
  message: string;
  executionId?: string;
  detalleError?: string | null;
}

export interface AdminDiagnosticResponse {
  instanceId: string;
  inicioProceso: string;
  version: string;
  fechaBuild: string | null;
  javaVersion: string;
  capturadoEn: string;
  alcance: string;
  ejecucion: {
    executionId: string;
    periodo: string;
    estado: string;
    solicitadoEn: string;
    iniciadoEn: string | null;
    terminadoEn: string | null;
    ultimaEtapa: string | null;
    ultimaActividad: string;
    milisegundosSinActividad: number;
    threadId: number | null;
    thread: string | null;
    detalleError?: string | null;
  } | null;
  executor: {
    activos: number;
    poolSize: number;
    corePoolSize: number;
    maxPoolSize: number;
    enCola: number;
    completadas: number;
  };
  hilos: Array<{
    id: number;
    nombre: string;
    estado: string;
    lock: string | null;
    propietarioLock: string | null;
    stack: string[];
  }>;
}