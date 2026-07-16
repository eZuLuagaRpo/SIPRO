/**
 * Modelos para el módulo de Cambio de Parámetros (SIPRO_Admin_Funcional).
 */

// ─── Ventana de Carga ────────────────────────────────────────────────────────

export interface ReglaVentanaBase {
  reglaId: number;
  offsetDiasApertura: number;
  horaApertura: string; // HH:mm
  offsetDiasCierre: number;
  horaCierre: string; // HH:mm
}

export interface ExcepcionVentanaCarga {
  periodoValoracion: string; // yyyy-MM-dd (último día del mes)
  periodoLabel?: string; // "Mayo 2026"
  fechaAperturaOverride: string | null;
  horaAperturaOverride: string | null;
  fechaCierreOverride: string | null;
  horaCierreOverride: string | null;
  motivo: string;
  creadoPorId?: number;
  creadoPorUsuario?: string;
  creadoEn?: string;
  tipo?: 'Ampliación' | 'Reducción' | 'Acortamiento';
}

export interface ExcepcionRequest {
  periodoValoracion: string;
  fechaAperturaOverride: string;
  horaAperturaOverride: string;
  fechaCierreOverride: string;
  horaCierreOverride: string;
  motivo: string;
}

// ─── Meses disponibles para excepción ────────────────────────────────────────

export interface MesDisponible {
  valor: string;   // yyyy-MM-dd (último día del mes)
  label: string;   // "Mayo 2026"
}

// ─── Usuarios ────────────────────────────────────────────────────────────────

export interface UsuarioResumen {
  idUsuario: number;
  usuario: string;
  nombres: string;
  apellidos: string;
  nombreCompleto: string;
  correo: string;
  areaNombre: string;
  cargoJefe: string;
  idLider: number | null;
  nombreLider: string;
  idRolActual: number | null;
  nombreRolActual: string;
  grupoAdEsperado: string | null;
  segmentos: number[];
}

export interface AsignacionUsuario {
  idRol: number;
  productosPorSegmento: ProductosPorSegmento[];
}

export interface ProductosPorSegmento {
  idSegmento: number;
  idsProductosPermitidos: number[];
}

// ─── Líder ───────────────────────────────────────────────────────────────────

export interface CambioLiderRequest {
  idsUsuariosAfectados: number[];
  idNuevoLider: number;
}

// ─── Registro de nuevo usuario ────────────────────────────────────────────────

export interface NuevoUsuarioRequest {
  nombres: string;
  apellidos: string;
  correo: string;
  usuario: string;
  areaNombre: string;
  idRol: number;
  idsSegmentos: number[];
  idsProductos: number[];
  /** Obligatorio cuando el rol es cargador. ID del líder aprobador asignado. */
  idLider?: number | null;
}

// ─── Productos ───────────────────────────────────────────────────────────────

export interface ProductoCatalogo {
  idProducto: number;
  titulo: string;
  idSegmento: number;
  nombreSegmento?: string;
  activo: boolean;
  nombreArchivoPermitido: string;
  nombreControlPermitido: string;
}

export interface ProductoRequest {
  titulo: string;
  idSegmento: number;
  activo: boolean;
  nombreArchivoPermitido: string;
  nombreControlPermitido: string;
}

// ─── Roles y segmentos ────────────────────────────────────────────────────────

export interface RolSistema {
  idRol: number;
  rol: string;
  perfil: string;
  descripcion: string;
  cargarArchivos?: number | null;
  aprobar?: number | null;
  modificarParametros?: number | null;
}

export interface SegmentoSistema {
  id: number;
  nombre: string;
}

// ─── Rol Azure en tiempo real ─────────────────────────────────────────────────

export interface RolAzureResult {
  encontrado: boolean;
  idRol: number | null;
  nombreRol: string | null;
  grupoAd: string | null;
  correoConsultado?: string | null;
  mensaje?: string | null;
}

// ─── Respuestas genéricas ────────────────────────────────────────────────────

export interface OperacionResultado {
  success: boolean;
  mensaje: string;
}

/** Detalle de una planilla transferida al nuevo líder tras un cambio masivo. */
export interface PlanillaTransferida {
  producto: string;
  fechaCorte: string;
  estadoPlanilla: string;
  nombreCargador: string;
}

/** Resultado de la operación de cambio masivo de líder. */
export interface CambioLiderResultado {
  success: boolean;
  mensaje: string;
  totalTransferidas: number;
  detalles: PlanillaTransferida[];
}
