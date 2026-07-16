import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';

/**
 * Punto de entrada de autenticación. Gestiona tres escenarios:
 * 1. Primera entrada / sesión expirada → auto-redirect silencioso a Entra ID.
 * 2. Retorno de logout manual (flag sipro_logout) → muestra pantalla SIPRO con botón.
 * 3. Error de autenticación → muestra pantalla SIPRO con mensaje de error.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit, OnDestroy {
  loading = false;
  errorMessage = '';
  /** Controla si la pantalla SIPRO es visible (card + header + footer). */
  mostrarPantalla = false;
  currentDateTime = '';
  currentIp = 'Cargando...';

  private loginRetryCount = 0;
  private clockInterval: ReturnType<typeof setInterval> | null = null;

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit() {
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/inicio']);
      return;
    }

    // Retorno de login-callback de Entra ID (code=, id_token=, etc.)
    if (this.isEntraRedirectCallback()) {
      this.completeRedirectLogin();
      return;
    }

    // El usuario cerró sesión manualmente → mostrar pantalla de bienvenida
    if (sessionStorage.getItem('sipro_logout') === 'true') {
      sessionStorage.removeItem('sipro_logout');
      this.mostrarPantalla = true;
      this.iniciarClock();
      this.fetchPublicIP();
      return;
    }

    // Primera entrada o sesión expirada automáticamente → redirect silencioso
    this.redirectToEntra();
  }

  ngOnDestroy() {
    if (this.clockInterval) {
      clearInterval(this.clockInterval);
    }
  }

  /** Invocado por el botón "Iniciar sesión con Entra ID". */
  iniciarSesion() {
    this.errorMessage = '';
    this.loginRetryCount = 0;
    this.loading = true;
    this.limpiarBloqueoInteraccionMsal();
    this.redirectToEntra();
  }

  private redirectToEntra() {
    this.authService.login().subscribe({
      error: (error) => {
        const raw: string = error?.error?.message || error?.message || '';
        if (raw.includes('interaction_in_progress') && this.loginRetryCount < 1) {
          this.loginRetryCount++;
          this.limpiarBloqueoInteraccionMsal();
          this.redirectToEntra();
          return;
        }
        this.errorMessage = error?.error?.mensaje || this.resolverMensajeError(raw);
        this.loading = false;
        this.mostrarPantalla = true;
        if (!this.clockInterval) {
          this.iniciarClock();
          this.fetchPublicIP();
        }
      }
    });
  }

  private limpiarBloqueoInteraccionMsal(): void {
    Object.keys(sessionStorage)
      .filter(k => k.includes('interaction.status') || k.includes('interaction-status') || k.startsWith('msal.'))
      .forEach(k => sessionStorage.removeItem(k));
  }

  private resolverMensajeError(raw: string): string {
    if (raw.includes('interaction_in_progress')) {
      return 'El proceso de inicio de sesión quedó interrumpido. Haz clic en "Iniciar sesión con Entra ID" para continuar.';
    }
    if (raw.includes('user_cancelled') || raw.includes('access_denied')) {
      return 'Cancelaste el proceso de autenticación. Haz clic en "Iniciar sesión con Entra ID" cuando estés listo.';
    }
    if (raw.includes('SIPRO') || raw.includes('administrador') || raw.includes('usuario activo')) {
      return raw;
    }
    if (raw.toLowerCase().includes('network') || raw.includes('AADSTS')) {
      return 'No se pudo conectar con Microsoft Entra ID. Verifica tu conexión a internet e inténtalo de nuevo.';
    }
    return 'No fue posible iniciar sesión. Por favor, inténtalo de nuevo.';
  }

  private completeRedirectLogin() {
    this.authService.completeLoginRedirect().subscribe({
      next: (response) => {
        if (response?.success) {
          this.router.navigate(['/inicio']);
          return;
        }

        if (response && !response.success) {
          this.errorMessage = response.message || response.mensaje || 'No fue posible iniciar sesión con Entra ID';
          this.loading = false;
          this.mostrarPantalla = true;
          this.iniciarClock();
          this.fetchPublicIP();
          return;
        }

        this.redirectToEntra();
      },
      error: (error) => {
        const raw: string = error?.error?.message || error?.message || '';
        this.errorMessage = error?.error?.mensaje || this.resolverMensajeError(raw);
        this.loading = false;
        this.mostrarPantalla = true;
        this.iniciarClock();
        this.fetchPublicIP();
      }
    });
  }

  private isEntraRedirectCallback(): boolean {
    const hash = window.location.hash;
    const search = window.location.search;
    const callbackPattern = /(?:code|id_token|access_token|error)=/;
    return callbackPattern.test(hash) || callbackPattern.test(search);
  }

  private iniciarClock(): void {
    this.actualizarFechaHora();
    this.clockInterval = setInterval(() => this.actualizarFechaHora(), 60000);
  }

  private actualizarFechaHora(): void {
    const now = new Date();
    const dias = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
    const meses = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
      'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
    const diaNombre = dias[now.getDay()];
    const dia = now.getDate();
    const mesNombre = meses[now.getMonth()];
    const anio = now.getFullYear();
    let horas = now.getHours();
    const minutos = now.getMinutes().toString().padStart(2, '0');
    const ampm = horas >= 12 ? 'p.m.' : 'a.m.';
    horas = horas % 12 || 12;
    this.currentDateTime = `${diaNombre}, ${dia} de ${mesNombre} de ${anio}, ${horas}:${minutos} ${ampm}`;
  }

  private fetchPublicIP(): void {
    fetch('https://api.ipify.org?format=json')
      .then(res => res.json())
      .then((data: { ip?: string }) => {
        this.currentIp = (data?.ip ?? '').trim() || 'No disponible';
      })
      .catch(() => {
        this.currentIp = 'No disponible';
      });
  }
}
