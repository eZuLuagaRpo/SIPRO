import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject, from, of, switchMap, tap, throwError } from 'rxjs';
import {
  AccountInfo,
  AuthenticationResult,
  InteractionRequiredAuthError,
  PublicClientApplication,
  RedirectRequest,
  SilentRequest
} from '@azure/msal-browser';
import { environment } from '../../environments/environment';
import { EntraConfigResponse, LoginResponse, User, UsuarioPermisos } from '../models/user.model';

/**
 * Maneja autenticación, sesión en sessionStorage y permisos RBAC del usuario actual.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private static readonly DEFAULT_SESSION_TIMEOUT_MINUTES = 5;
  private static readonly SESSION_ACTIVITY_REFRESH_THROTTLE_MS = 30000;
  private static readonly ENTRA_LOGIN_BASE_SCOPES = ['openid', 'profile', 'email', 'offline_access', 'User.Read'];
  private static readonly ENTRA_GRAPH_SCOPES = ['User.Read', 'User.Read.All', 'Directory.Read.All'];
  private static readonly ENTRA_CONFIG_STORAGE_KEY = 'entraConfig';
  private static readonly ENTRA_ID_TOKEN_STORAGE_KEY = 'entraIdToken';
  private static readonly ENTRA_API_ACCESS_TOKEN_STORAGE_KEY = 'entraApiAccessToken';
  private static readonly ENTRA_GRAPH_ACCESS_TOKEN_STORAGE_KEY = 'entraGraphAccessToken';

  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();
  private logoutTimer: ReturnType<typeof setTimeout> | null = null;
  private protectedActivityCount = 0;
  private lastSessionRefreshAt = 0;
  private msalInstance: PublicClientApplication | null = null;
  private cachedEntraConfig: EntraConfigResponse | null = null;

  /** Permisos RBAC por defecto (sin acceso) */
  private static readonly DEFAULT_PERMISOS: UsuarioPermisos = {
    puedeCargar: false,
    puedeAprobar: false,
    puedeSolicitarAprobacion: false,
    puedeVisualizar: false,
    puedeExportar: false,
    puedeModificarParametros: false,
    puedeAccederPanelAdmin: false,
    puedeVisualizarConsolidados: false,
    productosAsignados: []
  };

  constructor(private http: HttpClient) {
    this.loadEntraConfigFromSession();
    this.loadUserFromSession();
    this.registerSessionActivityListeners();
  }

  /**
   * Inicia el flujo interactivo de Entra ID mediante redirect.
   */
  login(): Observable<void> {
    return this.getEntraConfig(true).pipe(
      switchMap(config => {
        if (!config.enabled || !config.clientId || !config.tenantId) {
          return throwError(() => new Error('La autenticación con Entra ID no está configurada en SIPRO'));
        }

        return from(this.loginWithRedirect(config));
      })
    );
  }

  /**
   * Completa el redirect de MSAL, obtiene token delegado de Graph y bootstrapea la sesión local.
   */
  completeLoginRedirect(): Observable<LoginResponse | null> {
    return this.getEntraConfig().pipe(
      switchMap(config => {
        if (!config.enabled || !config.clientId || !config.tenantId) {
          return of(null);
        }

        return from(this.completeRedirectResult(config)).pipe(
          switchMap(result => {
            if (!result) {
              return of(null);
            }

            return from(this.acquireApiAccessToken(config, result.account, result)).pipe(
              switchMap(apiAccessToken => {
                if (!apiAccessToken) {
                  return of(null);
                }

                return from(this.acquireGraphAccessToken(config, result.account)).pipe(
                switchMap(graphAccessToken => this.http.post<LoginResponse>(`${environment.apiUrl}/auth/login`, {
                  apiAccessToken,
                  idToken: result.idToken,
                  graphAccessToken
                }).pipe(
                  tap(response => {
                    if (response.success) {
                      this.persistSession(response, result, apiAccessToken, graphAccessToken);
                    }
                  })
                ))
              );
              })
            );
          })
        );
      })
    );
  }

  /**
   * Cierra la sesión local, limpia temporizadores y elimina la información persistida.
   * @param redirectToEntra Si `true` (default), redirige a Microsoft para cerrar también
   *   la sesión remota de Entra ID. Pasar `false` en logouts automáticos (timer, expiración)
   *   para evitar el loop: logoutRedirect → Microsoft → /login → redirectToEntra() → loop.
   */
  logout(redirectToEntra = true): void {
    if (this.logoutTimer) {
      clearTimeout(this.logoutTimer);
      this.logoutTimer = null;
    }
    this.protectedActivityCount = 0;
    this.lastSessionRefreshAt = 0;
    sessionStorage.removeItem('userData');
    sessionStorage.removeItem(AuthService.ENTRA_ID_TOKEN_STORAGE_KEY);
    sessionStorage.removeItem(AuthService.ENTRA_API_ACCESS_TOKEN_STORAGE_KEY);
    sessionStorage.removeItem(AuthService.ENTRA_GRAPH_ACCESS_TOKEN_STORAGE_KEY);
    // Limpiar todo el cache interno de MSAL para evitar datos pegados entre sesiones.
    const msalKeys = Object.keys(sessionStorage).filter(k =>
      k.startsWith('msal.') || k.includes('.interaction.') || k.includes('interaction-status')
    );
    msalKeys.forEach(k => sessionStorage.removeItem(k));
    this.currentUserSubject.next(null);

    // Marcar cierre de sesión manual para que la pantalla de login se muestre al regresar.
    if (redirectToEntra) {
      sessionStorage.setItem('sipro_logout', 'true');
    }

    const logoutConfig = this.cachedEntraConfig;
    if (redirectToEntra && logoutConfig) {
      void this.ensureMsalInstance(logoutConfig)
        .then(instance => instance.logoutRedirect({
          postLogoutRedirectUri: `${window.location.origin}/login`
        }))
        .catch(() => {
          // Si falla el cierre remoto, al menos la sesión local ya quedó limpia.
        });
    }
  }

  /**
   * Indica si existe una sesión válida y no vencida para el usuario actual.
   */
  isAuthenticated(): boolean {
    const user = this.currentUserSubject.value;
    if (!user) {
      return false;
    }
    if (this.isSessionExpired(user)) {
      this.logout(false);
      return false;
    }
    return true;
  }

  getCurrentUser(): User | null {
    return this.currentUserSubject.value;
  }

  /**
   * Obtiene los permisos RBAC del usuario actual.
   * Retorna permisos por defecto (sin acceso) si no hay usuario o permisos.
   */
  getPermisos(): UsuarioPermisos {
    const user = this.currentUserSubject.value;
    return user?.permisos || AuthService.DEFAULT_PERMISOS;
  }

  /**
   * Verifica si el usuario actual puede cargar archivos.
   */
  puedeCargar(): boolean {
    return this.getPermisos().puedeCargar;
  }

  /**
   * Verifica si el usuario actual puede aprobar planillas.
   */
  puedeAprobar(): boolean {
    return this.getPermisos().puedeAprobar;
  }

  /**
   * Acceso efectivo al módulo de carga manual según la matriz de navegación.
   * Un admin no debe navegar a los módulos operativos aunque tenga permisos heredados.
   */
  puedeAccederCargaManual(): boolean {
    return !this.puedeAdministrar() && this.puedeCargar();
  }

  /**
   * Acceso efectivo al módulo de aprobación manual según la matriz de navegación.
   */
  puedeAccederAprobacionManual(): boolean {
    return !this.puedeAdministrar() && this.puedeAprobar();
  }

  /**
   * Acceso efectivo a /resumen y /tablero: Usuario_Analista, Auditoria y Admin_Permisos.
   */
  puedeAccederResumenConsolidado(): boolean {
    return this.getPermisos().puedeVisualizarConsolidados === true;
  }

  /**
   * Acceso efectivo al panel /admin: Soporte Técnico (dashboard/consola SQL/logs) o Admin_Permisos
   * (que también entra aquí únicamente para ejecutar la consolidación manual).
   */
  puedeAccederPanelAdmin(): boolean {
    return this.getPermisos().puedeAccederPanelAdmin === true || this.puedeModificarParametros();
  }

  /**
   * true solo para Soporte Técnico (id_rol=3). Úsalo dentro de /admin para mostrar/ocultar
   * las secciones que Admin_Permisos no debe ver (consola SQL, logs técnicos).
   */
  esAdminTecnico(): boolean {
    return this.getPermisos().puedeAccederPanelAdmin === true;
  }

  /**
   * true solo para Admin_Permisos (id_rol=6): es el único perfil autorizado a ejecutar la
   * consolidación manual dentro de /admin. Soporte Técnico ve el panel pero no esta acción.
   */
  puedeEjecutarConsolidacionManual(): boolean {
    return this.puedeModificarParametros();
  }

  /**
   * Verifica si el usuario actual tiene permiso RBAC para administrar parámetros.
   */
  puedeModificarParametros(): boolean {
    return this.getPermisos().puedeModificarParametros;
  }

  /**
   * Verifica acceso administrativo a /parametros: exclusivo de Admin_Permisos (id_rol=6).
   * Totalmente data-driven: lee el flag puedeModificarParametros resuelto por el backend
   * desde sipro_roles_permisos.modificar_parametros — sin nombres ni IDs quemados.
   */
  puedeAdministrar(): boolean {
    return this.puedeModificarParametros();
  }

  /**
   * Extiende la sesión activa reiniciando el temporizador de expiración.
   * Llamar antes de operaciones de larga duración (carga de archivos, aprobaciones)
   * para evitar que el logout automático interrumpa la operación.
   */
  extendSession(): void {
    const user = this.currentUserSubject.value;
    if (!user) return;
    const timeoutMinutes = user.sessionTimeoutMinutes ?? AuthService.DEFAULT_SESSION_TIMEOUT_MINUTES;
    const newExpiration = Date.now() + (timeoutMinutes * 60 * 1000);
    this.lastSessionRefreshAt = Date.now();
    user.sessionTimeoutMinutes = timeoutMinutes;
    user.sessionExpiresAt = newExpiration;
    sessionStorage.setItem('userData', JSON.stringify(user));
    this.startSessionTimer(user);
  }

  /**
   * Marca el inicio de una operación larga para evitar logout automático durante su ejecución.
   */
  beginProtectedActivity(): void {
    this.protectedActivityCount++;
    this.extendSession();
  }

  /**
   * Marca el fin de una operación larga y restablece el temporizador normal de sesión.
   */
  endProtectedActivity(): void {
    if (this.protectedActivityCount > 0) {
      this.protectedActivityCount--;
    }

    if (this.protectedActivityCount === 0) {
      const user = this.currentUserSubject.value;
      if (!user) {
        return;
      }
      if (this.isSessionExpired(user)) {
        this.logout(false);
        return;
      }
      this.startSessionTimer(user);
    }
  }

  /**
   * Refresca los permisos RBAC consultando al backend.
   * Útil si los permisos cambiaron durante la sesión.
   */
  refrescarPermisos(): Observable<UsuarioPermisos> {
    const user = this.currentUserSubject.value;
    if (!user?.idUsuario) {
      return of(AuthService.DEFAULT_PERMISOS);
    }
    return this.http.get<UsuarioPermisos>(
      `${environment.apiUrl}/auth/permisos/${user.idUsuario}`
    ).pipe(
      tap(permisos => {
        if (user) {
          const updatedUser = { ...user, permisos };
          sessionStorage.setItem('userData', JSON.stringify(updatedUser));
          this.currentUserSubject.next(updatedUser);
          this.startSessionTimer(updatedUser);
        }
      })
    );
  }

  private registerSessionActivityListeners(): void {
    if (typeof window === 'undefined' || typeof document === 'undefined') {
      return;
    }

    const refreshSession = () => this.refreshSessionFromUserActivity();

    window.addEventListener('click', refreshSession);
    window.addEventListener('keydown', refreshSession);
    window.addEventListener('scroll', refreshSession, { passive: true });
    window.addEventListener('focus', refreshSession);
    window.addEventListener('touchstart', refreshSession, { passive: true });
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) {
        this.refreshSessionFromUserActivity(true);
      }
    });
  }

  private refreshSessionFromUserActivity(force = false): void {
    const user = this.currentUserSubject.value;
    if (!user || this.protectedActivityCount > 0) {
      return;
    }

    if (this.isSessionExpired(user)) {
      this.logout(false);
      return;
    }

    const now = Date.now();
    if (!force && now - this.lastSessionRefreshAt < AuthService.SESSION_ACTIVITY_REFRESH_THROTTLE_MS) {
      return;
    }

    this.extendSession();
  }

  private getEntraConfig(forceRefresh = false): Observable<EntraConfigResponse> {
    if (!forceRefresh && this.isUsableEntraConfig(this.cachedEntraConfig)) {
      return of(this.cachedEntraConfig as EntraConfigResponse);
    }

    return this.http.get<EntraConfigResponse>(`${environment.apiUrl}/auth/entra/config`).pipe(
      tap(config => {
        this.cachedEntraConfig = config;
        if (this.isUsableEntraConfig(config)) {
          sessionStorage.setItem(AuthService.ENTRA_CONFIG_STORAGE_KEY, JSON.stringify(config));
        } else {
          sessionStorage.removeItem(AuthService.ENTRA_CONFIG_STORAGE_KEY);
        }
      })
    );
  }

  private isUsableEntraConfig(config: EntraConfigResponse | null | undefined): config is EntraConfigResponse {
    return !!config && config.enabled && !!config.clientId?.trim() && !!config.tenantId?.trim();
  }

  private async loginWithRedirect(config: EntraConfigResponse): Promise<void> {
    const instance = await this.ensureMsalInstance(config);
    const redirectRequest: RedirectRequest = {
      scopes: AuthService.ENTRA_LOGIN_BASE_SCOPES,
      prompt: 'select_account'
    };

    await instance.loginRedirect(redirectRequest);
  }

  private async completeRedirectResult(config: EntraConfigResponse): Promise<AuthenticationResult | null> {
    const instance = await this.ensureMsalInstance(config);
    const redirectResult = await instance.handleRedirectPromise();
    if (redirectResult?.account) {
      instance.setActiveAccount(redirectResult.account);
    }
    return redirectResult;
  }

  private async acquireGraphAccessToken(config: EntraConfigResponse, account?: AccountInfo | null): Promise<string> {
    const instance = await this.ensureMsalInstance(config);
    const resolvedAccount = account ?? instance.getActiveAccount() ?? instance.getAllAccounts()[0] ?? null;
    if (!resolvedAccount) {
      throw new Error('No fue posible resolver la cuenta autenticada de Entra ID');
    }

    instance.setActiveAccount(resolvedAccount);
    const silentRequest: SilentRequest = {
      account: resolvedAccount,
      scopes: AuthService.ENTRA_GRAPH_SCOPES,
      forceRefresh: false
    };

    const tokenResult = await instance.acquireTokenSilent(silentRequest);
    return tokenResult.accessToken;
  }

  private async acquireApiAccessToken(
    config: EntraConfigResponse,
    account?: AccountInfo | null,
    redirectResult?: AuthenticationResult | null
  ): Promise<string | null> {
    if (!config.apiScope?.trim()) {
      return redirectResult?.idToken ?? null;
    }

    if (redirectResult?.accessToken && redirectResult.scopes.includes(config.apiScope)) {
      return redirectResult.accessToken;
    }

    const instance = await this.ensureMsalInstance(config);
    const resolvedAccount = account ?? instance.getActiveAccount() ?? instance.getAllAccounts()[0] ?? null;
    if (!resolvedAccount) {
      throw new Error('No fue posible resolver la cuenta autenticada de Entra ID');
    }

    instance.setActiveAccount(resolvedAccount);
    try {
      const tokenResult = await instance.acquireTokenSilent({
        account: resolvedAccount,
        scopes: [config.apiScope],
        forceRefresh: false
      });

      return tokenResult.accessToken;
    } catch (error) {
      if (error instanceof InteractionRequiredAuthError) {
        await instance.acquireTokenRedirect({
          account: resolvedAccount,
          scopes: [config.apiScope]
        });
        return null;
      }

      if (redirectResult?.idToken) {
        console.warn('No fue posible obtener access token de API; se usará idToken para bootstrap de sesión.', error);
        return redirectResult.idToken;
      }

      throw error;
    }
  }

  private async ensureMsalInstance(config: EntraConfigResponse): Promise<PublicClientApplication> {
    if (
      this.msalInstance &&
      this.cachedEntraConfig?.clientId === config.clientId &&
      this.cachedEntraConfig?.tenantId === config.tenantId
    ) {
      return this.msalInstance;
    }

    const instance = new PublicClientApplication({
      auth: {
        clientId: config.clientId,
        authority: `https://login.microsoftonline.com/${config.tenantId}`,
        redirectUri: `${window.location.origin}/login`,
        postLogoutRedirectUri: `${window.location.origin}/login`,
        navigateToLoginRequestUrl: false
      },
      cache: {
        cacheLocation: 'sessionStorage',
        storeAuthStateInCookie: false
      }
    });

    await instance.initialize();
    this.msalInstance = instance;
    this.cachedEntraConfig = config;
    return instance;
  }

  private persistSession(
    response: LoginResponse,
    authResult: AuthenticationResult,
    apiAccessToken: string,
    graphAccessToken: string
  ): void {
    const permisos: UsuarioPermisos = response.permisos || AuthService.DEFAULT_PERMISOS;
    const sessionTimeoutMinutes = response.sessionTimeoutMinutes ?? AuthService.DEFAULT_SESSION_TIMEOUT_MINUTES;
    const sessionExpiresAt = Date.now() + (sessionTimeoutMinutes * 60 * 1000);

    const username = response.usuario || authResult.account?.username || response.correo || 'usuario';
    const fullName = `${response.nombres || ''} ${response.apellidos || ''}`.trim();

    const userData: User = response.user || {
      username,
      name: fullName || authResult.account?.name || username,
      role: 'user',
      sessionTimeoutMinutes,
      sessionExpiresAt,
      idUsuario: response.idUsuario,
      usuario: response.usuario,
      nombres: response.nombres,
      apellidos: response.apellidos,
      correo: response.correo,
      areaNombre: response.areaNombre,
      jefeNombre: response.jefeNombre,
      permisos
    };

    userData.sessionTimeoutMinutes = sessionTimeoutMinutes;
    userData.sessionExpiresAt = sessionExpiresAt;
    this.lastSessionRefreshAt = Date.now();
    sessionStorage.setItem('userData', JSON.stringify(userData));
    sessionStorage.setItem(AuthService.ENTRA_ID_TOKEN_STORAGE_KEY, authResult.idToken);
    sessionStorage.setItem(AuthService.ENTRA_API_ACCESS_TOKEN_STORAGE_KEY, apiAccessToken);
    sessionStorage.setItem(AuthService.ENTRA_GRAPH_ACCESS_TOKEN_STORAGE_KEY, graphAccessToken);
    this.currentUserSubject.next(userData);
    this.startSessionTimer(userData);
  }

  private loadUserFromSession(): void {
    const userData = sessionStorage.getItem('userData');
    if (userData) {
      try {
        if (!this.hasStoredApiAccessToken()) {
          sessionStorage.removeItem('userData');
          sessionStorage.removeItem(AuthService.ENTRA_ID_TOKEN_STORAGE_KEY);
          sessionStorage.removeItem(AuthService.ENTRA_API_ACCESS_TOKEN_STORAGE_KEY);
          sessionStorage.removeItem(AuthService.ENTRA_GRAPH_ACCESS_TOKEN_STORAGE_KEY);
          return;
        }

        const parsedUser = JSON.parse(userData) as User;
        if (this.isSessionExpired(parsedUser)) {
          this.logout(false);
          return;
        }
        this.currentUserSubject.next(parsedUser);
        this.startSessionTimer(parsedUser);
      } catch {
        sessionStorage.removeItem('userData');
        sessionStorage.removeItem(AuthService.ENTRA_ID_TOKEN_STORAGE_KEY);
        sessionStorage.removeItem(AuthService.ENTRA_API_ACCESS_TOKEN_STORAGE_KEY);
        sessionStorage.removeItem(AuthService.ENTRA_GRAPH_ACCESS_TOKEN_STORAGE_KEY);
      }
    }
  }

  private loadEntraConfigFromSession(): void {
    const rawConfig = sessionStorage.getItem(AuthService.ENTRA_CONFIG_STORAGE_KEY);
    if (!rawConfig) {
      return;
    }

    try {
      const config = JSON.parse(rawConfig) as EntraConfigResponse;
      this.cachedEntraConfig = this.isUsableEntraConfig(config) ? config : null;
      if (!this.cachedEntraConfig) {
        sessionStorage.removeItem(AuthService.ENTRA_CONFIG_STORAGE_KEY);
      }
    } catch {
      sessionStorage.removeItem(AuthService.ENTRA_CONFIG_STORAGE_KEY);
      this.cachedEntraConfig = null;
    }
  }

  private hasStoredApiAccessToken(): boolean {
    const token = sessionStorage.getItem(AuthService.ENTRA_API_ACCESS_TOKEN_STORAGE_KEY);
    return typeof token === 'string' && token.trim().length > 0;
  }

  private isSessionExpired(user: User): boolean {
    return typeof user.sessionExpiresAt === 'number' && Date.now() >= user.sessionExpiresAt;
  }

  private startSessionTimer(user: User): void {
    if (this.logoutTimer) {
      clearTimeout(this.logoutTimer);
      this.logoutTimer = null;
    }
    if (typeof user.sessionExpiresAt !== 'number') {
      return;
    }

    const delay = user.sessionExpiresAt - Date.now();
    if (delay <= 0) {
      this.logout(false);
      return;
    }

    this.logoutTimer = setTimeout(() => {
      if (this.protectedActivityCount > 0) {
        this.extendSession();
        return;
      }
      this.logout(false);
    }, delay);
  }
}