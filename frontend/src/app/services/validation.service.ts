import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ValidationResult, ValidationJobStartResponse, ValidationJobStatusResponse, Producto, Segmento, RangoFechaCorteResponse, ResumenCargas, CargasPendientes, AprobacionesPendientes, VentanaCargaResponse, ConsolidacionResumenResponse, TableroControlResponse } from '../models/validation.model';
import { Planilla } from '../models/planilla.model';

/**
 * Cliente HTTP del frontend para los endpoints de validación, planillas y consolidación.
 */
@Injectable({
  providedIn: 'root'
})
export class ValidationService {
  constructor(private http: HttpClient) { }

  /**
   * Obtiene el catálogo de productos disponibles para el usuario.
   */
  getProductos(segmentoId?: number | string): Observable<Producto[]> {
    const query = segmentoId !== undefined && segmentoId !== null && `${segmentoId}`.trim().length > 0
      ? `?segmentoId=${encodeURIComponent(segmentoId)}`
      : '';
    return this.http.get<Producto[]>(`${environment.apiUrl}/main/productos${query}`);
  }

  /**
   * Obtiene el catálogo de segmentos parametrizados en backend.
   */
  getSegmentos(): Observable<Segmento[]> {
    return this.http.get<Segmento[]>(`${environment.apiUrl}/main/segmentos`);
  }


  /**
   * Ejecuta la validación síncrona de un archivo cargado.
   */
  validarArchivo(formData: FormData): Observable<ValidationResult> {
    return this.http.post<ValidationResult>(`${environment.apiUrl}/validar`, formData);
  }

  /**
   * Inicia la validación asíncrona y devuelve eventos de progreso del request inicial.
   */
  iniciarValidacionArchivo(formData: FormData): Observable<HttpEvent<ValidationJobStartResponse>> {
    return this.http.post<ValidationJobStartResponse>(`${environment.apiUrl}/validar/async`, formData, {
      observe: 'events',
      reportProgress: true
    });
  }

  /**
   * Consulta el estado actualizado de un job de validación asíncrona.
   */
  obtenerEstadoValidacion(jobId: string): Observable<ValidationJobStatusResponse> {
    return this.http.get<ValidationJobStatusResponse>(`${environment.apiUrl}/validar/jobs/${encodeURIComponent(jobId)}`);
  }

  /**
   * Envía la planilla ya validada para iniciar su flujo de aprobación.
   */
  solicitarAprobacion(formData: FormData): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/planillas/solicitar`, formData);
  }

  /**
   * Descarga el archivo TXT con el detalle de errores de una validación.
   */
  descargarErrores(loteId: string): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/lotes/${encodeURIComponent(loteId)}/errores`, {
      responseType: 'blob'
    });
  }

  obtenerEstadisticas(): Observable<any> {
    return this.http.get(`${environment.apiUrl}/estadisticas`);
  }

  // =================== MÉTODOS DE APROBACIÓN ===================

  /**
   * Lista todas las planillas activas disponibles para administración o consulta general.
   */
  listarTodasPlanillas(): Observable<Planilla[]> {
    return this.http.get<Planilla[]>(`${environment.apiUrl}/planillas/todas`);
  }

  /**
   * Lista planillas activas visibles para el aprobador actual.
   * El backend deriva el líder desde la sesión autenticada.
   */
  listarPlanillasPorLider(idLider: number): Observable<Planilla[]> {
    void idLider;
    return this.http.get<Planilla[]>(`${environment.apiUrl}/planillas/por-lider`);
  }

  /**
   * Lista planillas visibles para el aprobador según sus permisos RBAC y su identidad autenticada.
   */
  listarPlanillasParaAprobador(idUsuario: number): Observable<Planilla[]> {
    void idUsuario;
    return this.http.get<Planilla[]>(`${environment.apiUrl}/planillas/para-aprobador`);
  }

  /**
   * Lista solo las planillas pendientes visibles para el líder autenticado.
   */
  listarPlanillasPendientes(correoLider?: string): Observable<Planilla[]> {
    void correoLider;
    return this.http.get<Planilla[]>(`${environment.apiUrl}/planillas/pendientes`);
  }

  /**
   * Consulta el detalle completo de una planilla específica.
   */
  obtenerDetallePlanilla(id: number): Observable<Planilla> {
    return this.http.get<Planilla>(`${environment.apiUrl}/planillas/${id}`);
  }

  /**
   * Aprueba una planilla con el usuario autenticado en backend.
   */
  aprobarPlanilla(id: number, usuarioAprobador?: string, idUsuarioAprobador?: number): Observable<any> {
    void usuarioAprobador;
    void idUsuarioAprobador;
    return this.http.put<any>(`${environment.apiUrl}/planillas/${id}/aprobar`, {});
  }

  /**
   * Rechaza una planilla enviando solo el motivo; el backend resuelve el usuario autenticado.
   */
  rechazarPlanilla(id: number, motivo: string, usuarioRechazo: string, idUsuarioRechazo?: number): Observable<any> {
    void usuarioRechazo;
    void idUsuarioRechazo;
    return this.http.put<any>(`${environment.apiUrl}/planillas/${id}/rechazar`, {
      motivo
    });
  }

  /**
   * Descarga el archivo adjunto original de una planilla.
   */
  descargarAdjuntoPlanilla(id: number): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/planillas/${id}/descargar`, {
      responseType: 'blob'
    });
  }

  // =================== CONFIGURACIÓN DEL SISTEMA ===================

  /**
   * Obtiene la configuración del rango de fechas de corte permitidas.
   * Este método consume el endpoint /api/config/rango-fecha-corte
   */
  getRangoFechaCorte(): Observable<RangoFechaCorteResponse> {
    return this.http.get<RangoFechaCorteResponse>(`${environment.apiUrl}/config/rango-fecha-corte`);
  }

  /**
   * Obtiene el resumen de cargas del usuario autenticado.
   */
  obtenerResumenCargas(correo: string): Observable<ResumenCargas> {
    void correo;
    return this.http.get<ResumenCargas>(`${environment.apiUrl}/planillas/resumen`);
  }

  /**
   * Obtiene el resumen de aprobaciones del usuario aprobador autenticado.
   */
  obtenerResumenAprobador(idUsuario: number): Observable<ResumenCargas> {
    void idUsuario;
    return this.http.get<ResumenCargas>(`${environment.apiUrl}/planillas/resumen-aprobador`);
  }

  /**
   * Obtiene los productos pendientes de carga del mes anterior para el usuario autenticado.
   */
  obtenerCargasPendientes(idUsuario: number): Observable<CargasPendientes> {
    void idUsuario;
    return this.http.get<CargasPendientes>(`${environment.apiUrl}/planillas/cargas-pendientes`);
  }

  /**
   * Obtiene las planillas pendientes de aprobación del líder autenticado.
   */
  obtenerAprobacionesPendientes(idUsuario: number): Observable<AprobacionesPendientes> {
    void idUsuario;
    return this.http.get<AprobacionesPendientes>(`${environment.apiUrl}/planillas/aprobaciones-pendientes`);
  }

  /**
   * Valida si la fecha/hora actual está dentro de la ventana de carga
   * para un periodo de valoración (fecha de corte).
   * @param fechaCorte Fecha en formato yyyy-MM-dd (último día del mes)
   */
  validarVentanaCarga(fechaCorte: string): Observable<VentanaCargaResponse> {
    return this.http.get<VentanaCargaResponse>(
      `${environment.apiUrl}/config/ventana-carga?fechaCorte=${encodeURIComponent(fechaCorte)}`
    );
  }

  /**
   * Recupera el resumen consolidado filtrando opcionalmente por año y mes.
   */
  obtenerResumenConsolidacion(anio?: number, mes?: number): Observable<ConsolidacionResumenResponse> {
    const queryParams: string[] = [];

    if (anio != null) {
      queryParams.push(`anio=${encodeURIComponent(String(anio))}`);
    }

    if (mes != null) {
      queryParams.push(`mes=${encodeURIComponent(String(mes))}`);
    }

    const query = queryParams.length > 0 ? `?${queryParams.join('&')}` : '';
    return this.http.get<ConsolidacionResumenResponse>(`${environment.apiUrl}/main/consolidacion/resumen${query}`);
  }

  /**
   * Recupera los registros consolidados detallados de un período para exportación Excel.
   */
  obtenerDetalleConsolidado(anio?: number, mes?: number): Observable<any[]> {
    const queryParams: string[] = [];

    if (anio != null) {
      queryParams.push(`anio=${encodeURIComponent(String(anio))}`);
    }

    if (mes != null) {
      queryParams.push(`mes=${encodeURIComponent(String(mes))}`);
    }

    const query = queryParams.length > 0 ? `?${queryParams.join('&')}` : '';
    return this.http.get<any[]>(`${environment.apiUrl}/main/consolidacion/detalle-diferencia${query}`);
  }

  /**
   * Recupera el tablero de control con el estado de planillas por producto y segmento.
   */
  obtenerTableroControl(anio?: number, mes?: number): Observable<TableroControlResponse> {
    const queryParams: string[] = [];
    if (anio != null) queryParams.push(`anio=${encodeURIComponent(String(anio))}`);
    if (mes != null) queryParams.push(`mes=${encodeURIComponent(String(mes))}`);
    const query = queryParams.length > 0 ? `?${queryParams.join('&')}` : '';
    return this.http.get<TableroControlResponse>(`${environment.apiUrl}/planillas/tablero-control${query}`);
  }

  /**
   * Descarga el reporte Excel del resumen consolidado del período.
   */
  descargarReporteResumenConsolidado(anio?: number, mes?: number): Observable<HttpResponse<Blob>> {
    const queryParams: string[] = [];

    if (anio != null) {
      queryParams.push(`anio=${encodeURIComponent(String(anio))}`);
    }

    if (mes != null) {
      queryParams.push(`mes=${encodeURIComponent(String(mes))}`);
    }

    const query = queryParams.length > 0 ? `?${queryParams.join('&')}` : '';
    return this.http.get(`${environment.apiUrl}/main/consolidacion/resumen/reporte${query}`, {
      responseType: 'blob',
      observe: 'response'
    });
  }
}