import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AsignacionUsuario,
  CambioLiderRequest,
  CambioLiderResultado,
  ExcepcionRequest,
  ExcepcionVentanaCarga,
  MesDisponible,
  NuevoUsuarioRequest,
  OperacionResultado,
  ProductoCatalogo,
  ProductoRequest,
  ReglaVentanaBase,
  RolAzureResult,
  RolSistema,
  SegmentoSistema,
  UsuarioResumen
} from '../models/parametros.model';

@Injectable({
  providedIn: 'root'
})
export class ParametrosService {

  private readonly base = `${environment.apiUrl}/parametros`;

  constructor(private http: HttpClient) {}

  // ── Ventana de Carga ──────────────────────────────────────────────────────

  getReglaBase(): Observable<ReglaVentanaBase> {
    return this.http.get<ReglaVentanaBase>(`${this.base}/ventana-carga/regla-base`);
  }

  getExcepciones(): Observable<ExcepcionVentanaCarga[]> {
    return this.http.get<ExcepcionVentanaCarga[]>(`${this.base}/ventana-carga/excepciones`);
  }

  crearExcepcion(req: ExcepcionRequest): Observable<OperacionResultado> {
    return this.http.post<OperacionResultado>(`${this.base}/ventana-carga/excepciones`, req);
  }

  actualizarExcepcion(periodo: string, req: ExcepcionRequest): Observable<OperacionResultado> {
    return this.http.put<OperacionResultado>(`${this.base}/ventana-carga/excepciones/${periodo}`, req);
  }

  eliminarExcepcion(periodo: string): Observable<OperacionResultado> {
    return this.http.delete<OperacionResultado>(`${this.base}/ventana-carga/excepciones/${periodo}`);
  }

  getMesesDisponibles(): Observable<MesDisponible[]> {
    return this.http.get<MesDisponible[]>(`${this.base}/ventana-carga/meses-disponibles`);
  }

  // ── Usuarios ──────────────────────────────────────────────────────────────

  getUsuarios(): Observable<UsuarioResumen[]> {
    return this.http.get<UsuarioResumen[]>(`${this.base}/usuarios`);
  }

  getAsignacionUsuario(idUsuario: number): Observable<AsignacionUsuario> {
    return this.http.get<AsignacionUsuario>(`${this.base}/usuarios/${idUsuario}/asignacion`);
  }

  guardarAsignacionUsuario(idUsuario: number, req: AsignacionUsuario): Observable<OperacionResultado> {
    return this.http.put<OperacionResultado>(`${this.base}/usuarios/${idUsuario}/asignacion`, req);
  }

  /** Consulta el rol del usuario en Azure Entra ID en tiempo real usando credenciales de aplicación. */
  getRolAzureUsuario(idUsuario: number): Observable<RolAzureResult> {
    return this.http.get<RolAzureResult>(`${this.base}/usuarios/${idUsuario}/rol-azure`);
  }

  /**
   * Consulta masiva de roles Azure para una lista de IDs de usuarios.
   * Retorna un mapa { [idUsuario: string]: RolAzureResult } dentro de { validaciones: ... }.
   * Usado en Sección 3 para validar si los cargadores tienen el grupo SIPRO activo en Entra ID.
   */
  validarRolesAzureMasivo(ids: number[]): Observable<{ validaciones: Record<string, RolAzureResult> }> {
    return this.http.get<{ validaciones: Record<string, RolAzureResult> }>(
      `${this.base}/usuarios/validacion-azure?ids=${ids.join(',')}`
    );
  }

  verificarPendientesLider(idLider: number): Observable<{ tienePendientes: boolean; cantidad: number }> {
    return this.http.get<{ tienePendientes: boolean; cantidad: number }>(
      `${this.base}/usuarios/${idLider}/pendientes-lider`
    );
  }

  aplicarCambioLider(req: CambioLiderRequest): Observable<CambioLiderResultado> {
    return this.http.put<CambioLiderResultado>(`${this.base}/usuarios/cambio-lider`, req);
  }

  registrarNuevoUsuario(req: NuevoUsuarioRequest): Observable<OperacionResultado> {
    return this.http.post<OperacionResultado>(`${this.base}/usuarios`, req);
  }

  // ── Productos ─────────────────────────────────────────────────────────────

  getProductos(): Observable<ProductoCatalogo[]> {
    return this.http.get<ProductoCatalogo[]>(`${this.base}/productos`);
  }

  crearProducto(req: ProductoRequest): Observable<OperacionResultado> {
    return this.http.post<OperacionResultado>(`${this.base}/productos`, req);
  }

  actualizarProducto(idProducto: number, req: ProductoRequest): Observable<OperacionResultado> {
    return this.http.put<OperacionResultado>(`${this.base}/productos/${idProducto}`, req);
  }

  // ── Catálogos ─────────────────────────────────────────────────────────────

  getRoles(): Observable<RolSistema[]> {
    return this.http.get<RolSistema[]>(`${this.base}/roles`);
  }

  getSegmentos(): Observable<SegmentoSistema[]> {
    return this.http.get<SegmentoSistema[]>(`${this.base}/segmentos`);
  }
}
