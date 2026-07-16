import { Component, DestroyRef, OnDestroy, OnInit, inject } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LoadingComponent } from '../shared/loading/loading.component';
import { ValidationService } from '../../services/validation.service';
import { AuthService } from '../../services/auth.service';
import { Planilla } from '../../models/planilla.model';
import { User, UsuarioPermisos } from '../../models/user.model';

/**
 * Gestiona la bandeja de aprobación, consulta planillas visibles y ejecuta aprobaciones o rechazos.
 */
@Component({
    selector: 'app-aprobacion',
    standalone: true,
    imports: [CommonModule, RouterModule, FormsModule, LoadingComponent],
    templateUrl: './aprobacion.component.html',
    styleUrls: ['./aprobacion.component.scss']
})
export class AprobacionComponent implements OnInit, OnDestroy {
    private readonly destroyRef = inject(DestroyRef);
    planillas: Planilla[] = [];
    filteredPlanillas: Planilla[] = [];
    displayedPlanillas: Planilla[] = [];
    loading = false;
    currentUser: User | null = null;
    permisos: UsuarioPermisos | null = null;
    sidebarOpen = false;
    subMenuOpen = true;
    currentDateTime = '';
    currentIp = '127.0.0.1';

    // Modal state
    showModal = false;
    selectedPlanilla: Planilla | null = null;
    modalLoading = false;

    // Filter state
    selectedFilter: 'TODAS' | 'PENDIENTE' | 'APROBADO' | 'RECHAZADO' = 'TODAS';

    // Success modal state (Approval)
    showSuccessModal = false;
    successCountdown = 30;
    approvedPlanilla: Planilla | null = null;
    private successInterval: any = null;

    // Rejection modal states
    showRejectionReasonModal = false;
    showRejectionSuccessModal = false;
    rejectionCountdown = 30;
    rejectedPlanilla: Planilla | null = null;
    rejectionMotivo = '';
    private rejectionInterval: any = null;

    // Search and Pagination (#2, #3)
    searchTerm = '';
    pageSize = 10;

    // Stats (#10)
    stats = { pendientes: 0, aprobados: 0, rechazados: 0 };
    private dateTimeInterval: ReturnType<typeof setInterval> | null = null;

    get userName(): string {
        if (this.currentUser) {
            const nombres = this.currentUser.nombres || '';
            const apellidos = this.currentUser.apellidos || '';
            return `${nombres} ${apellidos}`.trim() || this.currentUser.name || 'Usuario';
        }
        return 'Usuario';
    }

    get userEmail(): string {
        return this.currentUser?.correo || '';
    }

    get puedeCargar(): boolean {
        return this.authService.puedeAccederCargaManual();
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

    constructor(
        private validationService: ValidationService,
        private authService: AuthService,
        private router: Router
    ) { }

    /**
     * Carga el usuario actual y habilita la vista solo si tiene permisos de aprobación.
     */
    ngOnInit() {
        this.authService.currentUser$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(user => {
            this.currentUser = user;
            this.permisos = this.authService.getPermisos();

            if (!user) {
                return;
            }

            if (!this.puedeAprobar) {
                this.router.navigate(['/inicio']);
                return;
            }

            if (user) {
                this.loadPlanillas();
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

    /**
     * Consulta las planillas visibles para el aprobador actual y recalcula filtros y estadísticas.
     */
    loadPlanillas() {
        this.loading = true;
        const idUsuario = this.currentUser?.idUsuario;

        if (!idUsuario) {
            this.planillas = [];
            this.calculateStats();
            this.applyFilter();
            this.loading = false;
            return;
        }

        const source$ = this.validationService.listarPlanillasParaAprobador(Number(idUsuario));

        source$.subscribe({
            next: (planillas) => {
                // Ordenar de más reciente a más viejo
                this.planillas = planillas.sort((a, b) =>
                    new Date(b.fechaCreacion).getTime() - new Date(a.fechaCreacion).getTime()
                );

                this.calculateStats();
                this.applyFilter();
                // Delay de 1 segundo extra para dar tiempo a la UI de renderizar
                setTimeout(() => {
                    this.loading = false;
                }, 1000);
            },
            error: (error) => {
                console.error('Error cargando planillas', error);
                this.loading = false;
            }
        });
    }

    calculateStats() {
        this.stats = {
            pendientes: this.planillas.filter(p => p.estado === 'PENDIENTE').length,
            aprobados: this.planillas.filter(p => p.estado === 'APROBADO').length,
            rechazados: this.planillas.filter(p => p.estado === 'RECHAZADO').length
        };
    }

    applyFilter() {
        // Filter by status
        let result = [...this.planillas];
        if (this.selectedFilter !== 'TODAS') {
            result = result.filter(p => p.estado === this.selectedFilter);
        }

        // Filter by search term (#2)
        if (this.searchTerm.trim()) {
            const term = this.searchTerm.toLowerCase().trim();
            result = result.filter(p =>
                p.nombreResponsable?.toLowerCase().includes(term)
            );
        }

        this.filteredPlanillas = result;
        this.updateDisplayedPlanillas();
    }

    updateDisplayedPlanillas() {
        // Pagination (#3)
        this.displayedPlanillas = this.filteredPlanillas.slice(0, this.pageSize);
    }

    onPageSizeChange() {
        this.updateDisplayedPlanillas();
    }

    setFilter(filter: 'TODAS' | 'PENDIENTE' | 'APROBADO' | 'RECHAZADO') {
        this.selectedFilter = filter;
        this.applyFilter();
    }

    openModal(planilla: Planilla) {
        this.selectedPlanilla = planilla;
        this.showModal = true;
    }

    closeModal() {
        this.showModal = false;
        this.selectedPlanilla = null;
    }

    /**
     * Aprueba la planilla seleccionada y refresca la bandeja del aprobador.
     */
    aprobar() {
        if (!this.selectedPlanilla) return;

        this.modalLoading = true;
        const planillaToApprove = { ...this.selectedPlanilla };
        const usuarioAprobador = this.userEmail || this.currentUser?.usuario || this.currentUser?.username || 'N/A';
        const idUsuarioAprobador = this.currentUser?.idUsuario ? Number(this.currentUser.idUsuario) : undefined;

        this.validationService.aprobarPlanilla(
            this.selectedPlanilla.id,
            usuarioAprobador,
            idUsuarioAprobador
        ).subscribe({
            next: () => {
                this.modalLoading = false;
                this.closeModal();
                this.approvedPlanilla = planillaToApprove;
                this.showSuccessModal = true;
                this.startSuccessCountdown();
                this.loadPlanillas();
            },
            error: (error) => {
                this.modalLoading = false;
                alert('Error al aprobar: ' + (error.error?.mensaje || error.message));
            }
        });
    }

    startSuccessCountdown() {
        this.successCountdown = 30;
        this.successInterval = setInterval(() => {
            this.successCountdown--;
            if (this.successCountdown <= 0) {
                this.closeSuccessModal();
            }
        }, 1000);
    }

    closeSuccessModal() {
        if (this.successInterval) {
            clearInterval(this.successInterval);
            this.successInterval = null;
        }
        this.showSuccessModal = false;
        this.approvedPlanilla = null;
    }

    // ========== REJECTION FLOW ==========
    openRejectionReasonModal() {
        if (!this.selectedPlanilla) return;
        this.rejectedPlanilla = { ...this.selectedPlanilla };
        this.rejectionMotivo = '';
        this.closeModal();
        this.showRejectionReasonModal = true;
    }

    closeRejectionReasonModal() {
        this.showRejectionReasonModal = false;
        this.rejectedPlanilla = null;
        this.rejectionMotivo = '';
    }

    /**
     * Envía el rechazo con motivo y actualiza la vista del aprobador.
     */
    confirmRejection() {
        if (!this.rejectedPlanilla || !this.rejectionMotivo.trim()) {
            alert('Por favor ingresa el motivo del rechazo.');
            return;
        }

        this.modalLoading = true;
        const userEmail = this.userEmail || 'N/A';
        const idUsuario = this.currentUser?.idUsuario ? Number(this.currentUser.idUsuario) : undefined;

        this.validationService.rechazarPlanilla(
            this.rejectedPlanilla.id,
            this.rejectionMotivo,
            userEmail,
            idUsuario
        ).subscribe({
            next: () => {
                this.modalLoading = false;
                this.showRejectionReasonModal = false;
                this.showRejectionSuccessModal = true;
                this.startRejectionCountdown();
                this.loadPlanillas();
            },
            error: (error) => {
                this.modalLoading = false;
                alert('Error al rechazar: ' + (error.error?.mensaje || error.message));
            }
        });
    }

    startRejectionCountdown() {
        this.rejectionCountdown = 30;
        this.rejectionInterval = setInterval(() => {
            this.rejectionCountdown--;
            if (this.rejectionCountdown <= 0) {
                this.closeRejectionSuccessModal();
            }
        }, 1000);
    }

    closeRejectionSuccessModal() {
        if (this.rejectionInterval) {
            clearInterval(this.rejectionInterval);
            this.rejectionInterval = null;
        }
        this.showRejectionSuccessModal = false;
        this.rejectedPlanilla = null;
        this.rejectionMotivo = '';
    }

    descargarAdjunto() {
        if (!this.selectedPlanilla) return;

        this.validationService.descargarAdjuntoPlanilla(this.selectedPlanilla.id).subscribe({
            next: (blob) => {
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = this.selectedPlanilla?.nombreArchivo || 'archivo.xlsx';
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                window.URL.revokeObjectURL(url);
            },
            error: (error) => {
                console.error('Error al descargar archivo', error);
                alert('Error al descargar el archivo.');
            }
        });
    }

    formatDate(dateStr: string): string {
        if (!dateStr) return '';
        const normalized = dateStr.replace(/\//g, '-').trim();
        const datePart = normalized.includes('T') ? normalized.split('T')[0] : normalized;
        const match = datePart.match(/^(\d{4})-(\d{2})-(\d{2})$/);

        if (match) {
            return `${match[1]}-${match[2]}-${match[3]}`;
        }

        return dateStr;
    }

    getFileSize(bytes: number): string {
        if (!bytes) return '0 B';
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
    }

    getEstadoBadgeClass(estado: string): string {
        switch (estado) {
            case 'APROBADO': return 'badge-green';
            case 'RECHAZADO': return 'badge-orange';
            case 'PENDIENTE': return 'badge-gray';
            default: return 'badge-gray';
        }
    }

    getSegmentoLabel(segmento?: string | null): string {
        const normalized = (segmento || '').trim().toLowerCase();

        if (normalized.includes('full ifrs')) {
            return 'Full IFRS';
        }

        if (normalized.includes('colgaap') || normalized.includes('modificado')) {
            return 'Colgaap/Modificado';
        }

        return segmento?.trim() || 'No informado';
    }

    getSegmentoBadgeClass(segmento?: string | null): string {
        const label = this.getSegmentoLabel(segmento);

        if (label === 'Full IFRS') {
            return 'segment-badge--ifrs';
        }

        if (label === 'Colgaap/Modificado') {
            return 'segment-badge--colgaap';
        }

        return 'segment-badge--default';
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
}