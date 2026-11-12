import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AdminComponent } from './admin.component';
import { AdminService } from '../../services/admin.service';
import { AuthService } from '../../services/auth.service';
import { AdminLogItem, AdminLogStreamResponse } from '../../models/admin.model';

describe('AdminComponent diagnostico', () => {
  let component: AdminComponent;
  let service: jasmine.SpyObj<AdminService>;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = jasmine.createSpyObj<AdminService>('AdminService',
      ['obtenerLogs', 'ejecutarSql', 'capturarDiagnostico']);
    const auth = jasmine.createSpyObj<AuthService>('AuthService', ['getPermisos']);
    const router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    component = TestBed.runInInjectionContext(() => new AdminComponent(auth, service, router));
  });

  afterEach(() => TestBed.resetTestingModule());

  const log = (id: number): AdminLogItem => ({
    id, timestamp: '11/11/2026 15:00:00', level: 'INFO', logger: 'ConsolidacionPeriodoExecutor',
    thread: 'validation-job-1', scope: 'CONSOLIDACION', message: `Etapa ${id}`, executionId: 'ejecucion'
  });

  it('muestra mas de cinco entradas del modal', () => {
    component.consolidacionLogEntries = Array.from({ length: 8 }, (_, index) => log(index + 1));
    expect(component.modalLogEntries.length).toBe(8);
  });

  it('no intenta representar un error HTTP generico como una tabla SQL', () => {
    service.ejecutarSql.and.returnValue(throwError(() => ({
      error: { message: 'Servicio no disponible' }
    })));
    component.ejecutarSql();
    expect(component.sqlResultado).toBeNull();
    expect(component.sqlError).toBe('Servicio no disponible');
    expect(component.sqlLoading).toBeFalse();
  });

  it('conserva columnas de un SELECT sin filas', () => {
    service.ejecutarSql.and.returnValue(of({
      exito: true, tipoOperacion: 'SELECT', mensaje: 'Sin filas', filasAfectadas: null,
      columnas: ['pid'], filas: [], sqlEjecutada: 'SELECT pid FROM pg_stat_activity',
      resultadoTruncado: false, duracionMs: 2
    }));
    component.ejecutarSql();
    expect(component.sqlColumns).toEqual(['pid']);
    expect(component.sqlResultado?.filas).toEqual([]);
  });

  it('reinicia cursores sin mezclar logs cuando cambia la instancia', () => {
    const response: AdminLogStreamResponse = {
      latestId: 1, cursorId: 1, totalBuffered: 1, items: [log(1)], instanceId: 'instancia-A'
    };
    service.obtenerLogs.and.returnValues(of(response), of({ ...response, instanceId: 'instancia-B' }));
    component.toggleLogsPause();
    component.toggleLogsPause();
    expect(component.logEntries.length).toBe(1);
    component.toggleLogsPause();
    component.toggleLogsPause();
    expect(component.logEntries).toEqual([]);
    expect(component.logAfterId).toBe(0);
    expect(component.logsInstanceId).toBe('instancia-B');
    expect(component.logsWarning).toContain('Cambio de instancia');
  });

  it('las plantillas solo cargan SQL de lectura y no lo ejecutan', () => {
    component.consultaDiagnostica = 'sesiones';
    component.cargarConsultaDiagnostica();
    expect(component.sqlOperacion).toBe('SELECT');
    expect(component.sqlTexto).toContain('pg_catalog.pg_stat_activity');
    expect(component.sqlTexto).not.toContain(';');
    expect(component.sqlTexto).not.toContain('\n');
    expect(service.ejecutarSql).not.toHaveBeenCalled();
  });

  it('informa el fallo de captura sin conservar una captura antigua como vigente', () => {
    service.capturarDiagnostico.and.returnValue(throwError(() => ({ status: 403 })));
    component.capturarDiagnostico();
    expect(component.diagnostico).toBeNull();
    expect(component.diagnosticoError).toContain('No se pudo capturar');
    expect(component.diagnosticoLoading).toBeFalse();
  });

  it('muestra en el modal los errores de actualizacion de sus logs', () => {
    service.obtenerLogs.and.returnValue(throwError(() => ({
      error: { message: 'Conexion interrumpida' }
    })));
    component['consultarLogs'](false, 'CONSOLIDACION');
    expect(component.consolidacionLogsError).toBe('Conexion interrumpida');
    expect(component.consolidacionLogEntries).toEqual([]);
  });

  it('no superpone solicitudes de logs del mismo alcance', () => {
    const pending = new Subject<AdminLogStreamResponse>();
    service.obtenerLogs.and.returnValue(pending);
    component['consultarLogs'](false, 'CONSOLIDACION');
    component['consultarLogs'](false, 'CONSOLIDACION');
    expect(service.obtenerLogs).toHaveBeenCalledTimes(1);
    pending.next({ latestId: 1, cursorId: 1, totalBuffered: 1, items: [log(1)], instanceId: 'A' });
    pending.complete();
    service.obtenerLogs.and.returnValue(of({
      latestId: 1, cursorId: 1, totalBuffered: 1, items: [], instanceId: 'A'
    }));
    component['consultarLogs'](false, 'CONSOLIDACION');
    expect(service.obtenerLogs).toHaveBeenCalledTimes(2);
    expect(component.consolidacionLogEntries.length).toBe(1);
  });
});
