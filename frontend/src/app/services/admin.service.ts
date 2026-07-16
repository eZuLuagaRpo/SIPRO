import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AdminDeleteConsolidacionRequest,
  AdminDeleteConsolidacionResponse,
  AdminDashboardResponse,
  AdminLogLevel,
  AdminLogScope,
  AdminLogStreamResponse,
  AdminSqlExecuteRequest,
  AdminSqlExecuteResponse
} from '../models/admin.model';
import { ConsolidacionManualStatus } from '../models/validation.model';

@Injectable({
  providedIn: 'root'
})
export class AdminService {
  constructor(private http: HttpClient) { }

  obtenerDashboard(periodo?: string): Observable<AdminDashboardResponse> {
    let params = new HttpParams();
    if (periodo) {
      params = params.set('periodo', periodo);
    }
    return this.http.get<AdminDashboardResponse>(`${environment.apiUrl}/admin/dashboard`, { params });
  }

  ejecutarConsolidacionManual(periodo: string, observacion: string): Observable<ConsolidacionManualStatus> {
    const params = new HttpParams()
      .set('periodo', periodo)
      .set('observacion', observacion);

    return this.http.post<ConsolidacionManualStatus>(`${environment.apiUrl}/admin/consolidacion/manual`, null, { params });
  }

  eliminarConsolidacion(
    idConsolidacion: number,
    request: AdminDeleteConsolidacionRequest
  ): Observable<AdminDeleteConsolidacionResponse> {
    return this.http.request<AdminDeleteConsolidacionResponse>(
      'DELETE',
      `${environment.apiUrl}/admin/consolidacion/${idConsolidacion}`,
      { body: request }
    );
  }

  ejecutarSql(request: AdminSqlExecuteRequest): Observable<AdminSqlExecuteResponse> {
    return this.http.post<AdminSqlExecuteResponse>(`${environment.apiUrl}/admin/sql/execute`, request);
  }

  obtenerLogs(
    afterId?: number,
    level: AdminLogLevel = 'ALL',
    limit = 200,
    scope: AdminLogScope = 'ALL'
  ): Observable<AdminLogStreamResponse> {
    let params = new HttpParams().set('level', level).set('limit', String(limit));
    if (afterId != null) {
      params = params.set('afterId', String(afterId));
    }
    if (scope !== 'ALL') {
      params = params.set('scope', scope);
    }

    return this.http.get<AdminLogStreamResponse>(`${environment.apiUrl}/admin/logs`, { params });
  }
}