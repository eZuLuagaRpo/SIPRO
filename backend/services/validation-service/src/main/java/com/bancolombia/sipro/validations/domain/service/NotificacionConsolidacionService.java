package com.bancolombia.sipro.validations.domain.service;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionArchivo;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleConsolidacionesPlanillas;
import com.bancolombia.sipro.validations.domain.model.UsuarioPersona;
import com.bancolombia.sipro.validations.infrastructure.config.MailNotificationProperties;
import com.bancolombia.sipro.validations.infrastructure.notification.MailTemplateNotificationService;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionArchivoRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleConsolidacionesPlanillasRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproUsuarioProductoRolRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.UsuarioPersonaRepository;
import com.bancolombia.sipro.validations.infrastructure.security.SiproAuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Envía notificaciones no bloqueantes del flujo de consolidación usando la misma infraestructura de correo SIPRO.
 */
@Service
public class NotificacionConsolidacionService {

    private static final Logger logger = LoggerFactory.getLogger(NotificacionConsolidacionService.class);
    private static final int ADMIN_FUNCIONAL_ROLE_ID = 6;
    private static final String ESTADO_ERROR = "ERROR";
    private static final List<String> ESTADOS_EXITOSOS = List.of("COMPLETADO", "COMPLETADO_CON_ADVERTENCIAS");
    private static final Locale LOCALE_ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter MES_ANIO_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", LOCALE_ES_CO);
    private static final DateTimeFormatter FECHA_CORTA_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FECHA_HORA_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", LOCALE_ES_CO);

    private final SiproUsuarioProductoRolRepository usuarioProductoRolRepository;
    private final SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository;
    private final SiproDetalleConsolidacionArchivoRepository consolidacionArchivoRepository;
    private final UsuarioPersonaRepository usuarioPersonaRepository;
    private final ParametroUnicoService parametroUnicoService;
    private final MailNotificationProperties mailNotificationProperties;
    private final MailTemplateNotificationService mailTemplateNotificationService;

    public NotificacionConsolidacionService(SiproUsuarioProductoRolRepository usuarioProductoRolRepository,
                                            SiproDetalleConsolidacionesPlanillasRepository consolidacionRepository,
                                            SiproDetalleConsolidacionArchivoRepository consolidacionArchivoRepository,
                                            UsuarioPersonaRepository usuarioPersonaRepository,
                                            ParametroUnicoService parametroUnicoService,
                                            MailNotificationProperties mailNotificationProperties,
                                            MailTemplateNotificationService mailTemplateNotificationService) {
        this.usuarioProductoRolRepository = usuarioProductoRolRepository;
        this.consolidacionRepository = consolidacionRepository;
        this.consolidacionArchivoRepository = consolidacionArchivoRepository;
        this.usuarioPersonaRepository = usuarioPersonaRepository;
        this.parametroUnicoService = parametroUnicoService;
        this.mailNotificationProperties = mailNotificationProperties;
        this.mailTemplateNotificationService = mailTemplateNotificationService;
    }

    /**
     * Notifica la consolidación completada o completada con advertencias.
     */
    public MailTemplateNotificationService.DeliveryResult enviarConfirmacion(Long idConsolidacion) {
        if (idConsolidacion == null || idConsolidacion <= 0) {
            logger.warn("Correo de consolidación no preparado: idConsolidacion inválido ({})", idConsolidacion);
            return MailTemplateNotificationService.DeliveryResult.skipped("idConsolidacion invalido");
        }

        Optional<SiproDetalleConsolidacionesPlanillas> cabeceraOpt = consolidacionRepository.findById(idConsolidacion);
        if (cabeceraOpt.isEmpty()) {
            logger.warn("Correo de consolidación no preparado: no existe cabecera para idConsolidacion={}", idConsolidacion);
            return MailTemplateNotificationService.DeliveryResult.skipped("cabecera no encontrada");
        }

        SiproDetalleConsolidacionesPlanillas cabecera = cabeceraOpt.get();
        if (!ESTADOS_EXITOSOS.contains(defaultIfBlank(cabecera.getEstadoConsolidacion(), ""))) {
            logger.warn("Correo de consolidación no preparado para idConsolidacion={}: estado actual {} no es notificable.",
                    idConsolidacion,
                    cabecera.getEstadoConsolidacion());
            return MailTemplateNotificationService.DeliveryResult.skipped(
                    "estado no notificable: " + defaultIfBlank(cabecera.getEstadoConsolidacion(), "sin estado"));
        }

        ResolvedUser ejecutor = resolveUser(cabecera.getCreadoPorId(), null);
        List<String> destinatarios = resolveDestinatarios(cabecera.getCreadoPorId(), null);
        String periodoTexto = formatMesAnio(cabecera.getPeriodoValoracion());
        String asunto = "SIPRO - Consolidación completada: " + periodoTexto;

        if (destinatarios.isEmpty()) {
            logger.warn("Correo de consolidación completada no enviado para {}. Destinatarios: []. Asunto: {}. No hay destinatarios configurados.",
                    periodoTexto,
                    asunto);
            return MailTemplateNotificationService.DeliveryResult.skipped("no hay destinatarios configurados");
        }

        List<SiproDetalleConsolidacionArchivo> archivos = consolidacionArchivoRepository
                .findByIdConsolidacionOrderByIdCargaPlanillaAsc(idConsolidacion);

        Map<String, String> model = new LinkedHashMap<>();
        model.put("banner_url", escape(mailTemplateNotificationService.resolveBannerSrc()));
        model.put("periodo_titulo", escape(buildPeriodoTitulo(cabecera.getPeriodoValoracion())));
        model.put("estado_consolidacion", escape(defaultIfBlank(cabecera.getEstadoConsolidacion(), "COMPLETADO")));
        model.put("archivos_procesados", escape(String.valueOf(nullSafeInt(cabecera.getCantidadArchivosConsolidados()))));
        model.put("registros_totales", escape(String.valueOf(nullSafeInt(cabecera.getCantidadRegistrosConsolidados()))));
        model.put("ejecutado_por", escape(formatUsuarioLinea(ejecutor)));
        model.put("fecha_ejecucion", escape(formatFechaHora(cabecera.getFechaHoraFin())));
        model.put("duracion_minutos", escape(formatDuracion(cabecera.getDuracionMinutos())));
        model.put("archivos_rows", buildArchivosRows(archivos, cabecera.getNombresArchivosConsolidados()));
        model.put("resultado_descripcion", escape(buildResultadoDescripcion(cabecera.getEstadoConsolidacion())));
        model.put("creffos_mensaje", escape(buildMensajeCreffos(cabecera.getEstadoConsolidacion())));
        model.put("ruta_compartida", escape(resolveRutaCompartida()));

        String html = mailTemplateNotificationService.renderPlanillaTemplate("consolidacion-completada.html", model);
        return enviarCorreo("consolidación completada", periodoTexto, asunto, destinatarios, html,
                "consolidacion " + idConsolidacion);
    }

    /**
     * Notifica que una consolidación terminó en error sin bloquear el flujo operativo.
     */
    public MailTemplateNotificationService.DeliveryResult notificarError(Long idConsolidacion) {
        if (idConsolidacion == null || idConsolidacion <= 0) {
            logger.warn("Correo de consolidación fallida no preparado: idConsolidacion inválido ({})", idConsolidacion);
            return MailTemplateNotificationService.DeliveryResult.skipped("idConsolidacion invalido");
        }

        Optional<SiproDetalleConsolidacionesPlanillas> cabeceraOpt = consolidacionRepository.findById(idConsolidacion);
        if (cabeceraOpt.isEmpty()) {
            logger.warn("Correo de consolidación fallida no preparado: no existe cabecera para idConsolidacion={}", idConsolidacion);
            return MailTemplateNotificationService.DeliveryResult.skipped("cabecera no encontrada");
        }

        SiproDetalleConsolidacionesPlanillas cabecera = cabeceraOpt.get();
        if (!ESTADO_ERROR.equalsIgnoreCase(defaultIfBlank(cabecera.getEstadoConsolidacion(), ""))) {
            logger.warn("Correo de consolidación fallida no preparado para idConsolidacion={}: estado actual {} no es notificable.",
                    idConsolidacion,
                    cabecera.getEstadoConsolidacion());
            return MailTemplateNotificationService.DeliveryResult.skipped(
                    "estado no notificable: " + defaultIfBlank(cabecera.getEstadoConsolidacion(), "sin estado"));
        }

        ResolvedUser ejecutor = resolveUser(cabecera.getCreadoPorId(), null);
        List<String> destinatarios = resolveDestinatarios(cabecera.getCreadoPorId(), null);
        String periodoTexto = formatMesAnio(cabecera.getPeriodoValoracion());
        String asunto = "SIPRO - Consolidación fallida: " + periodoTexto;

        if (destinatarios.isEmpty()) {
            logger.warn("Correo de consolidación fallida no enviado para {}. Destinatarios: []. Asunto: {}. No hay destinatarios configurados.",
                    periodoTexto,
                    asunto);
            return MailTemplateNotificationService.DeliveryResult.skipped("no hay destinatarios configurados");
        }

        Map<String, String> model = new LinkedHashMap<>();
        model.put("banner_url", escape(mailTemplateNotificationService.resolveBannerSrc()));
        model.put("periodo_titulo", escape(buildPeriodoTitulo(cabecera.getPeriodoValoracion())));
        model.put("estado_consolidacion", escape(defaultIfBlank(cabecera.getEstadoConsolidacion(), ESTADO_ERROR)));
        model.put("archivos_procesados", escape(String.valueOf(nullSafeInt(cabecera.getCantidadArchivosConsolidados()))));
        model.put("registros_totales", escape(String.valueOf(nullSafeInt(cabecera.getCantidadRegistrosConsolidados()))));
        model.put("ejecutado_por", escape(formatUsuarioLinea(ejecutor)));
        model.put("fecha_error", escape(formatFechaHora(cabecera.getFechaHoraFin())));
        model.put("duracion_minutos", escape(formatDuracion(cabecera.getDuracionMinutos())));
        model.put("mensaje_error", escape(defaultIfBlank(cabecera.getMensajeError(), "Sin detalle adicional.")));
        model.put("observacion", escape(defaultIfBlank(cabecera.getObservacion(), "No se registraron observaciones adicionales.")));
        model.put("ruta_compartida", escape(resolveRutaCompartida()));

        String html = mailTemplateNotificationService.renderPlanillaTemplate("consolidacion-fallida.html", model);
        return enviarCorreo("consolidación fallida", periodoTexto, asunto, destinatarios, html,
                "consolidacion-error-" + idConsolidacion);
    }

    /**
     * Notifica la eliminación segura de una consolidación desde el panel admin.
     */
    public MailTemplateNotificationService.DeliveryResult notificarEliminacion(SiproDetalleConsolidacionesPlanillas cabecera,
                                                                               String motivo,
                                                                               SiproAuthenticatedUser principal,
                                                                               long registrosEliminados,
                                                                               int archivosEliminados,
                                                                               OffsetDateTime fechaEliminacion) {
        if (cabecera == null) {
            logger.warn("Correo de consolidación eliminada no preparado: cabecera nula.");
            return MailTemplateNotificationService.DeliveryResult.skipped("cabecera nula");
        }

        ResolvedUser eliminador = resolveUser(principal != null ? principal.idUsuario() : null, principal);
        List<String> destinatarios = resolveDestinatarios(principal != null ? principal.idUsuario() : null, principal);
        String periodoTexto = formatMesAnio(cabecera.getPeriodoValoracion());
        String asunto = "SIPRO - Consolidación eliminada: " + periodoTexto;

        if (destinatarios.isEmpty()) {
            logger.warn("Correo de consolidación eliminada no enviado para {}. Destinatarios: []. Asunto: {}. No hay destinatarios configurados.",
                    periodoTexto,
                    asunto);
            return MailTemplateNotificationService.DeliveryResult.skipped("no hay destinatarios configurados");
        }

        Map<String, String> model = new LinkedHashMap<>();
        model.put("banner_url", escape(mailTemplateNotificationService.resolveBannerSrc()));
        model.put("periodo_titulo", escape(buildPeriodoTitulo(cabecera.getPeriodoValoracion())));
        model.put("eliminado_por", escape(formatUsuarioLinea(eliminador)));
        model.put("fecha_eliminacion", escape(formatFechaHora(fechaEliminacion)));
        model.put("motivo", escape(defaultIfBlank(motivo, "No informado")));
        model.put("registros_eliminados", escape(String.valueOf(registrosEliminados)));
        model.put("archivos_eliminados", escape(String.valueOf(archivosEliminados)));

        String html = mailTemplateNotificationService.renderPlanillaTemplate("consolidacion-eliminada.html", model);
        return enviarCorreo("consolidación eliminada", periodoTexto, asunto, destinatarios, html,
                "consolidacion-eliminada-" + defaultIfBlank(String.valueOf(cabecera.getIdConsolidacion()), "sin-id"));
    }

        private MailTemplateNotificationService.DeliveryResult enviarCorreo(String tipoEvento,
                                           String periodoTexto,
                                           String asunto,
                                           List<String> destinatarios,
                                           String html,
                                           String referencia) {
        if (!mailNotificationProperties.isEnabled()) {
            logger.info("Correo de {} preparado para {}. Destinatarios: {}. Asunto: {}. Mail deshabilitado, no se envía.",
                    tipoEvento,
                    periodoTexto,
                    destinatarios,
                    asunto);
        } else {
            logger.info("Correo de {} preparado para {}. Destinatarios: {}. Asunto: {}.",
                    tipoEvento,
                    periodoTexto,
                    destinatarios,
                    asunto);
        }

        MailTemplateNotificationService.DeliveryResult result = mailTemplateNotificationService.sendHtml(
                new MailTemplateNotificationService.EmailPayload(asunto, destinatarios, html),
                tipoEvento,
                referencia);

        if (result.sent()) {
            logger.info("Correo de {} enviado para {}. Destinatarios: {}. Asunto: {}.",
                    tipoEvento,
                    periodoTexto,
                    destinatarios,
                    asunto);
            return result;
        }

        if (result.status() == MailTemplateNotificationService.DeliveryStatus.FAILED) {
            logger.error("Correo de {} no enviado para {}. Destinatarios: {}. Asunto: {}. Motivo: {}.",
                    tipoEvento,
                    periodoTexto,
                    destinatarios,
                    asunto,
                    result.detail());
            return result;
        }

        logger.info("Correo de {} no enviado para {}. Destinatarios: {}. Asunto: {}. Motivo: {}.",
                tipoEvento,
                periodoTexto,
                destinatarios,
                asunto,
                result.detail());
        return result;
    }

    private List<String> resolveDestinatarios(Long actorUserId, SiproAuthenticatedUser principal) {
        Set<String> destinatarios = new LinkedHashSet<>();
        destinatarios.addAll(usuarioProductoRolRepository.findDistinctActiveEmailsByRolId(ADMIN_FUNCIONAL_ROLE_ID));

        resolveActorEmail(actorUserId, principal)
                .filter(correo -> !correo.isBlank())
                .map(String::trim)
                .map(String::toLowerCase)
                .ifPresent(destinatarios::add);

        return destinatarios.stream().toList();
    }

    private Optional<String> resolveActorEmail(Long actorUserId, SiproAuthenticatedUser principal) {
        if (actorUserId != null) {
            Optional<String> correoPersona = usuarioPersonaRepository.findById(actorUserId)
                    .map(UsuarioPersona::getCorreo)
                    .map(String::trim)
                    .filter(value -> !value.isBlank());
            if (correoPersona.isPresent()) {
                return correoPersona;
            }
        }

        if (principal != null && !isBlank(principal.email())) {
            return Optional.of(principal.email().trim());
        }
        return Optional.empty();
    }

    private ResolvedUser resolveUser(Long idUsuario, SiproAuthenticatedUser principal) {
        Optional<UsuarioPersona> persona = idUsuario == null
                ? Optional.empty()
                : usuarioPersonaRepository.findById(idUsuario);

        String nombre = persona
                .map(this::nombreCompleto)
                .filter(value -> !isBlank(value))
                .orElseGet(() -> fallbackNombre(idUsuario, principal));

        String usuario = persona
                .map(UsuarioPersona::getUsuario)
                .filter(value -> !isBlank(value))
                .map(String::trim)
                .orElseGet(() -> fallbackUsuario(principal));

        String correo = persona
                .map(UsuarioPersona::getCorreo)
                .filter(value -> !isBlank(value))
                .map(String::trim)
                .orElseGet(() -> principal != null ? defaultIfBlank(principal.email(), "") : "");

        return new ResolvedUser(idUsuario, defaultIfBlank(nombre, "Usuario"), defaultIfBlank(usuario, "No informado"), defaultIfBlank(correo, ""));
    }

    private String buildPeriodoTitulo(LocalDate periodoValoracion) {
        return formatMesAnio(periodoValoracion) + " (" + formatFechaCorte(periodoValoracion) + ")";
    }

    private String buildResultadoDescripcion(String estado) {
        if ("COMPLETADO_CON_ADVERTENCIAS".equalsIgnoreCase(defaultIfBlank(estado, ""))) {
            return "Se informa que la consolidación de planillas manuales para el segmento Colgaap/Modificado ha sido ejecutada y terminó con advertencias operativas controladas.";
        }
        return "Se informa que la consolidación de planillas manuales para el segmento Colgaap/Modificado ha sido ejecutada exitosamente.";
    }

    private String buildMensajeCreffos(String estado) {
        if ("COMPLETADO_CON_ADVERTENCIAS".equalsIgnoreCase(defaultIfBlank(estado, ""))) {
            return "El archivo CREFFSOS fue generado. Revise el panel admin para validar las advertencias operativas antes de consumirlo en la ruta compartida configurada.";
        }
        return "El archivo CREFFSOS ha sido generado y publicado en la ruta compartida configurada.";
    }

    private String buildArchivosRows(List<SiproDetalleConsolidacionArchivo> archivos, String fallbackNombres) {
        if (archivos != null && !archivos.isEmpty()) {
            StringBuilder html = new StringBuilder();
            for (SiproDetalleConsolidacionArchivo archivo : archivos) {
                String nombre = escape(defaultIfBlank(archivo.getNombreArchivo(), "Archivo sin nombre"));
                String cantidad = String.valueOf(nullSafeInt(archivo.getCantidadRegistrosArchivo()));
                html.append("<li style=\"margin-bottom:6px;\">")
                        .append(nombre)
                        .append(" (")
                        .append(escape(cantidad))
                        .append(nullSafeInt(archivo.getCantidadRegistrosArchivo()) == 1 ? " registro)" : " registros)")
                        .append("</li>");
            }
            return html.toString();
        }

        if (!isBlank(fallbackNombres)) {
            StringBuilder html = new StringBuilder();
            for (String nombreArchivo : fallbackNombres.split(",")) {
                String nombreNormalizado = defaultIfBlank(nombreArchivo, "");
                if (!nombreNormalizado.isBlank()) {
                    html.append("<li style=\"margin-bottom:6px;\">")
                            .append(escape(nombreNormalizado))
                            .append("</li>");
                }
            }
            if (html.length() > 0) {
                return html.toString();
            }
        }

        return "<li style=\"margin-bottom:6px;\">No se encontró detalle de archivos consolidados.</li>";
    }

    private String formatMesAnio(LocalDate fecha) {
        if (fecha == null) {
            return "Periodo no informado";
        }
        String formatted = MES_ANIO_FORMATTER.format(fecha).trim();
        if (formatted.isEmpty()) {
            return fecha.toString();
        }
        return Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
    }

    private String formatFechaCorte(LocalDate fecha) {
        if (fecha == null) {
            return "No informado";
        }
        return FECHA_CORTA_FORMATTER.format(fecha);
    }

    private String formatFechaHora(OffsetDateTime fechaHora) {
        if (fechaHora == null) {
            return "No informada";
        }
        String formatted = FECHA_HORA_FORMATTER.format(fechaHora.atZoneSameInstant(ZoneId.systemDefault()));
        return formatted
                .replace("a. m.", "a.m.")
                .replace("p. m.", "p.m.")
                .replace("AM", "a.m.")
                .replace("PM", "p.m.");
    }

    private String formatDuracion(BigDecimal duracionMinutos) {
        if (duracionMinutos == null) {
            return "No disponible";
        }
        return duracionMinutos.stripTrailingZeros().toPlainString() + " minutos";
    }

    private String formatUsuarioLinea(ResolvedUser user) {
        if (user == null) {
            return "Usuario no informado";
        }
        String username = defaultIfBlank(user.username(), "No informado");
        return defaultIfBlank(user.displayName(), "Usuario") + " (" + username + ")";
    }

    private String resolveRutaCompartida() {
        return defaultIfBlank(parametroUnicoService.getString("CREFFSOS_RUTA_SALIDA", ""), "No configurada");
    }

    private String fallbackNombre(Long idUsuario, SiproAuthenticatedUser principal) {
        if (principal != null && !isBlank(principal.alias())) {
            return principal.alias().trim();
        }
        if (principal != null && !isBlank(principal.usuario())) {
            return principal.usuario().trim();
        }
        if (principal != null && !isBlank(principal.preferredUsername())) {
            return principal.preferredUsername().trim();
        }
        if (idUsuario != null) {
            return "Usuario " + idUsuario;
        }
        return "Usuario";
    }

    private String fallbackUsuario(SiproAuthenticatedUser principal) {
        if (principal == null) {
            return "No informado";
        }
        if (!isBlank(principal.usuario())) {
            return principal.usuario().trim();
        }
        if (!isBlank(principal.alias())) {
            return principal.alias().trim();
        }
        if (!isBlank(principal.preferredUsername())) {
            return principal.preferredUsername().trim();
        }
        if (!isBlank(principal.email())) {
            return principal.email().trim();
        }
        return "No informado";
    }

    private String nombreCompleto(UsuarioPersona persona) {
        String nombres = defaultIfBlank(persona.getNombres(), "");
        String apellidos = defaultIfBlank(persona.getApellidos(), "");
        return defaultIfBlank((nombres + " " + apellidos).trim(), "Usuario");
    }

    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8.name());
    }

    private record ResolvedUser(Long idUsuario, String displayName, String username, String email) {
    }
}
