package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproUsuarioProductoRol;
import com.bancolombia.sipro.validations.domain.model.UsuarioArea;
import com.bancolombia.sipro.validations.domain.model.UsuarioPersona;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleCargaPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproUsuarioProductoRolRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.UsuarioAreaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detecta y notifica incumplimientos de carga de planillas manuales al cierre de la ventana.
 * Se ejecuta periódicamente vía {@link com.bancolombia.sipro.validations.infrastructure.config.IncumplimientoCargaScheduler}.
 *
 * Lógica:
 * - Un producto CUMPLE si tiene al menos una planilla APROBADA en el periodo.
 * - Cargadores son notificados por productos sin aprobación (null, PENDIENTE o RECHAZADO).
 * - Aprobadores son notificados por todos los productos no aprobados de sus cargadores,
 *   derivados de la relación cargador → jefe en usuario_area. Un jefe con varios cargadores
 *   recibe la unión de todos sus productos incumplidos en un solo correo.
 * - Se envía un correo por usuario agrupando todos sus productos incumplidos.
 * - La clave INCUMPLIMIENTO_ULTIMO_PERIODO_NOTIFICADO garantiza que solo se envía una vez por periodo.
 */
@Service
public class IncumplimientoCargaService {

    private static final Logger logger = LoggerFactory.getLogger(IncumplimientoCargaService.class);

    private static final String CLAVE_ULTIMO_PERIODO = "INCUMPLIMIENTO_ULTIMO_PERIODO_NOTIFICADO";
    private static final DateTimeFormatter PERIODO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final Long ID_SEGMENTO_COLGAAP = 1L;
    private static final Long ID_SEGMENTO_FULL_IFRS = 2L;
    private static final String NOMBRE_SEGMENTO_COLGAAP = "Colgaap";
    private static final String NOMBRE_SEGMENTO_FULL_IFRS = "Full IFRS";

    private final ParametroUnicoService parametroUnicoService;
    private final VentanaCargaService ventanaCargaService;
    private final SiproDetalleCargaPlanillasRepository planillasRepository;
    private final SiproUsuarioProductoRolRepository usuarioProductoRolRepository;
    private final UsuarioAreaRepository areaRepository;
    private final PlanillaNotificationService planillaNotificationService;

    public IncumplimientoCargaService(
            ParametroUnicoService parametroUnicoService,
            VentanaCargaService ventanaCargaService,
            SiproDetalleCargaPlanillasRepository planillasRepository,
            SiproUsuarioProductoRolRepository usuarioProductoRolRepository,
            UsuarioAreaRepository areaRepository,
            PlanillaNotificationService planillaNotificationService) {
        this.parametroUnicoService = parametroUnicoService;
        this.ventanaCargaService = ventanaCargaService;
        this.planillasRepository = planillasRepository;
        this.usuarioProductoRolRepository = usuarioProductoRolRepository;
        this.areaRepository = areaRepository;
        this.planillaNotificationService = planillaNotificationService;
    }

    /**
     * Punto de entrada del scheduler. Evalúa si corresponde notificar y lo hace una sola vez por periodo.
     */
    public void ejecutarNotificacionIncumplimiento() {
        // Periodo = último día del mes anterior (ej: 31/07/2026 si hoy es agosto)
        LocalDate periodoValoracion = LocalDate.now().withDayOfMonth(1).minusDays(1);
        String periodoKey = periodoValoracion.format(PERIODO_FORMAT);

        // Idempotencia: no reenviar si ya se notificó este periodo
        String ultimoPeriodo = parametroUnicoService.getString(CLAVE_ULTIMO_PERIODO, "");
        if (periodoKey.equals(ultimoPeriodo)) {
            logger.debug("[Incumplimiento] Periodo {} ya notificado. Se omite.", periodoKey);
            return;
        }

        // Solo notificar si la ventana de carga del periodo ya cerró
        Optional<VentanaCargaService.VentanaCalculada> ventanaOpt =
                ventanaCargaService.obtenerVentana(periodoValoracion);
        if (ventanaOpt.isEmpty()) {
            logger.debug("[Incumplimiento] Sin ventana de carga para {}. Se omite.", periodoValoracion);
            return;
        }

        LocalDateTime cierre = ventanaOpt.get().getFechaHoraCierre();
        if (!LocalDateTime.now().isAfter(cierre)) {
            logger.debug("[Incumplimiento] Ventana del periodo {} aún abierta (cierra: {}). Se omite.", periodoValoracion, cierre);
            return;
        }

        logger.info("[Incumplimiento] Iniciando notificaciones para el periodo {}", periodoKey);
        procesarSegmento(periodoValoracion, ID_SEGMENTO_COLGAAP, NOMBRE_SEGMENTO_COLGAAP);
        procesarSegmento(periodoValoracion, ID_SEGMENTO_FULL_IFRS, NOMBRE_SEGMENTO_FULL_IFRS);

        // Marcar periodo como notificado (persiste en BD y actualiza caché)
        parametroUnicoService.setString(CLAVE_ULTIMO_PERIODO, periodoKey);
        logger.info("[Incumplimiento] Notificaciones completadas para el periodo {}", periodoKey);
    }

    private void procesarSegmento(LocalDate periodoValoracion, Long idSegmento, String nombreSegmento) {
        int anio = periodoValoracion.getYear();
        int mes = periodoValoracion.getMonthValue();

        List<SiproDetalleCargaPlanillas> planillas =
                planillasRepository.findActivasByAnioMesAndSegmentoId(anio, mes, idSegmento);

        // Productos que ya cumplieron (APROBADO) → quedan excluidos de notificaciones de cargadores
        Set<Long> productosAprobados = planillas.stream()
                .filter(p -> "APROBADO".equals(p.getEstadoPlanilla()))
                .map(SiproDetalleCargaPlanillas::getIdProducto)
                .collect(Collectors.toSet());

        // --- Cargadores: productos sin aprobación (null, PENDIENTE, RECHAZADO) ---
        List<SiproUsuarioProductoRol> cargadores =
                usuarioProductoRolRepository.findActivasCargadoresBySegmento(idSegmento);

        Map<Long, Set<String>> productosPorCargador = new LinkedHashMap<>();
        Map<Long, UsuarioPersona> datosCargadores = new LinkedHashMap<>();

        for (SiproUsuarioProductoRol upr : cargadores) {
            Long idProducto = upr.getProducto().getIdProducto();
            if (productosAprobados.contains(idProducto)) {
                continue;
            }
            Long idUsuario = upr.getId().getIdUsuario();
            productosPorCargador.computeIfAbsent(idUsuario, k -> new LinkedHashSet<>())
                    .add(upr.getProducto().getTitulo());
            datosCargadores.putIfAbsent(idUsuario, upr.getUsuario());
        }

        enviarNotificaciones(productosPorCargador, datosCargadores, nombreSegmento, periodoValoracion, "cargador");

        // --- Aprobadores: se derivan de la relación cargador → jefe en usuario_area ---
        // El jefe de cada cargador recibe los mismos productos no aprobados de ese cargador.
        // Un jefe con varios cargadores recibe la unión de todos sus productos incumplidos.
        Map<Long, Set<String>> productosPorAprobador = new LinkedHashMap<>();
        Map<Long, UsuarioPersona> datosAprobadores = new LinkedHashMap<>();

        Set<Long> idsCargadoresIncumplidos = new LinkedHashSet<>();
        for (SiproUsuarioProductoRol upr : cargadores) {
            if (!productosAprobados.contains(upr.getProducto().getIdProducto())) {
                idsCargadoresIncumplidos.add(upr.getId().getIdUsuario());
            }
        }

        if (!idsCargadoresIncumplidos.isEmpty()) {
            Map<Long, UsuarioPersona> jefesPorCargador = new LinkedHashMap<>();
            List<UsuarioArea> areas = areaRepository.findWithJefeByIdUsuarioIn(idsCargadoresIncumplidos);
            for (UsuarioArea area : areas) {
                if (area.getJefe() != null) {
                    jefesPorCargador.put(area.getIdUsuario(), area.getJefe());
                }
            }

            for (SiproUsuarioProductoRol upr : cargadores) {
                Long idProducto = upr.getProducto().getIdProducto();
                if (productosAprobados.contains(idProducto)) {
                    continue;
                }
                Long idCargador = upr.getId().getIdUsuario();
                UsuarioPersona jefe = jefesPorCargador.get(idCargador);
                if (jefe == null) {
                    continue;
                }
                Long idJefe = jefe.getIdUsuario();
                productosPorAprobador.computeIfAbsent(idJefe, k -> new LinkedHashSet<>())
                        .add(upr.getProducto().getTitulo());
                datosAprobadores.putIfAbsent(idJefe, jefe);
            }
        }

        enviarNotificaciones(productosPorAprobador, datosAprobadores, nombreSegmento, periodoValoracion, "aprobador");
    }

    private void enviarNotificaciones(
            Map<Long, Set<String>> productosPorUsuario,
            Map<Long, UsuarioPersona> datosUsuarios,
            String nombreSegmento,
            LocalDate periodoValoracion,
            String rolDescripcion) {

        for (Map.Entry<Long, Set<String>> entry : productosPorUsuario.entrySet()) {
            Long idUsuario = entry.getKey();
            UsuarioPersona persona = datosUsuarios.get(idUsuario);
            String correo = persona != null ? persona.getCorreo() : null;

            if (correo == null || correo.isBlank()) {
                logger.warn("[Incumplimiento] {} idUsuario={} sin correo registrado. Se omite notificación.", rolDescripcion, idUsuario);
                continue;
            }

            String nombre = buildNombreCompleto(persona);
            List<String> productos = new ArrayList<>(entry.getValue());

            try {
                planillaNotificationService.notificarIncumplimiento(nombre, correo, productos, nombreSegmento, periodoValoracion);
            } catch (Exception e) {
                logger.error("[Incumplimiento] Error notificando {} idUsuario={} correo={}: {}",
                        rolDescripcion, idUsuario, correo, e.getMessage(), e);
            }
        }
    }

    private String buildNombreCompleto(UsuarioPersona persona) {
        if (persona == null) return "Usuario SIPRO";
        String nombres = persona.getNombres() == null ? "" : persona.getNombres().trim();
        String apellidos = persona.getApellidos() == null ? "" : persona.getApellidos().trim();
        String completo = (nombres + " " + apellidos).trim();
        return completo.isEmpty() ? "Usuario SIPRO" : completo;
    }
}
