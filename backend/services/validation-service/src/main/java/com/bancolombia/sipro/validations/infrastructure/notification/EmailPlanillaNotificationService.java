package com.bancolombia.sipro.validations.infrastructure.notification;

import com.bancolombia.sipro.validations.domain.model.SiproDetalleArchivoValidacion;
import com.bancolombia.sipro.validations.domain.model.SiproDetalleCargaPlanillas;
import com.bancolombia.sipro.validations.domain.model.SiproResumenPorMoneda;
import com.bancolombia.sipro.validations.domain.model.UsuarioPersona;
import com.bancolombia.sipro.validations.domain.service.ParametroUnicoService;
import com.bancolombia.sipro.validations.domain.service.PlanillaNotificationService;
import com.bancolombia.sipro.validations.infrastructure.config.MailNotificationProperties;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproDetalleArchivoValidacionRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.SiproResumenPorMonedaRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.UsuarioLoginRepository;
import com.bancolombia.sipro.validations.infrastructure.repository.UsuarioPersonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Implementa el envío de correos del flujo de planillas usando plantillas HTML y datos operativos.
 */
@Service
public class EmailPlanillaNotificationService implements PlanillaNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailPlanillaNotificationService.class);
    private static final Locale LOCALE_ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA_CORTE_FORMATTER =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", LOCALE_ES_CO);

    private final MailNotificationProperties properties;
    private final ParametroUnicoService parametroUnicoService;
    private final SiproDetalleArchivoValidacionRepository validacionRepository;
    private final SiproResumenPorMonedaRepository resumenPorMonedaRepository;
    private final UsuarioLoginRepository usuarioLoginRepository;
    private final UsuarioPersonaRepository usuarioPersonaRepository;
    private final Environment environment;
    private final MailTemplateNotificationService mailTemplateNotificationService;

    public EmailPlanillaNotificationService(
            MailNotificationProperties properties,
            ParametroUnicoService parametroUnicoService,
            SiproDetalleArchivoValidacionRepository validacionRepository,
            SiproResumenPorMonedaRepository resumenPorMonedaRepository,
            UsuarioLoginRepository usuarioLoginRepository,
            UsuarioPersonaRepository usuarioPersonaRepository,
            Environment environment,
            MailTemplateNotificationService mailTemplateNotificationService) {
        this.properties = properties;
        this.parametroUnicoService = parametroUnicoService;
        this.validacionRepository = validacionRepository;
        this.resumenPorMonedaRepository = resumenPorMonedaRepository;
        this.usuarioLoginRepository = usuarioLoginRepository;
        this.usuarioPersonaRepository = usuarioPersonaRepository;
        this.environment = environment;
        this.mailTemplateNotificationService = mailTemplateNotificationService;
    }

    /**
     * Envía el correo de solicitud de aprobación o de no carga, según el tipo de planilla.
     */
    @Override
    public void notificarSolicitud(SiproDetalleCargaPlanillas planilla) {
        boolean sinDatos = Boolean.TRUE.equals(planilla.getNoReportaDatos());
        String template = sinDatos ? "solicitud-no-carga.html" : "solicitud-si-carga.html";
        String subject = sinDatos
                ? String.format("SIPRO | Solicitud de no carga | %s | %s", textoPlano(planilla.getProducto()), formatFechaCorta(planilla.getFechaCorteInformacion()))
                : String.format("SIPRO | Solicitud de aprobación de carga manual | %s | %s", textoPlano(planilla.getProducto()), formatFechaCorta(planilla.getFechaCorteInformacion()));

            NotificationUsers users = resolveNotificationUsers(planilla);
            Map<String, String> model = buildBaseModel(planilla, users);
        model.put("url_sipro", escape(resolveActionUrl()));
            EmailMessage email = new EmailMessage(subject, recipients(users.lider().email()), render(template, model));
        send(email, "solicitud", planilla.getId());
    }

    /**
     * Envía la confirmación de aprobación a los actores involucrados en la planilla.
     */
    @Override
    public void notificarAprobacion(SiproDetalleCargaPlanillas planilla) {
        boolean sinDatos = Boolean.TRUE.equals(planilla.getNoReportaDatos());
        String template = sinDatos ? "confirmar-aprobado-no-carga.html" : "confirmar-aprobado-si-carga.html";
        String subject = sinDatos
                ? String.format("SIPRO | No carga aprobada | %s | %s", textoPlano(planilla.getProducto()), formatFechaCorta(planilla.getFechaCorteInformacion()))
                : String.format("SIPRO | Carga manual aprobada | %s | %s", textoPlano(planilla.getProducto()), formatFechaCorta(planilla.getFechaCorteInformacion()));

            NotificationUsers users = resolveNotificationUsers(planilla);
            Map<String, String> model = buildBaseModel(planilla, users);

        // Full IFRS (segmento que contiene "Full" o "IFRS"): informar que los archivos
        // se enviarán a la carpeta de red compartida. Full IFRS no genera archivo TOPCREFFSOS.
        String segmento = planilla.getSegmento();
        boolean isFullIfrs = segmento != null
                && (segmento.toLowerCase().contains("full") || segmento.toLowerCase().contains("ifrs"));
        if (isFullIfrs) {
            model.put("mensaje_destino_full_ifrs",
                "<div style=\"background:#fff3cd; border:1px solid #ffc107; border-radius:4px; padding:10px 14px; "
                + "font-size:13px; color:#856404; margin-bottom:18px; line-height:18px;\">"
                + "<strong>Destino de archivos Full IFRS:</strong> "
                + "Los archivos aprobados (planilla y archivo de control) ser&#225;n enviados autom&#225;ticamente "
                + "a la carpeta compartida de red: "
                + "<strong>\\\\Sbcldwpifw01\\IFRS9_Planillas_Manuales</strong>. "
                + "Esta planilla no genera archivo TOPCREFFSOS."
                + "</div>");
        } else {
            model.put("mensaje_destino_full_ifrs", "");
        }

        // Nombre del archivo de control (extraído de la ruta almacenada, solo Full IFRS)
        String nombreArchivoControl = resolveNombreArchivoControl(planilla);

        // fila_archivo_control: fila de tabla HTML para template confirmar-aprobado-si-carga.html
        if (isFullIfrs && nombreArchivoControl != null) {
            model.put("fila_archivo_control",
                "<tr>"
                + "<td style=\"background:#d9d9d9; border:1px solid #8a8a8a; padding:6px 10px; line-height:16px; font-weight:700; vertical-align:middle; mso-line-height-rule:exactly;\">Nombre del archivo control</td>"
                + "<td style=\"background:#f3f3f3; border:1px solid #8a8a8a; padding:6px 10px; line-height:16px; text-align:center; font-weight:400; vertical-align:middle; mso-line-height-rule:exactly;\">" + escape(nombreArchivoControl) + "</td>"
                + "</tr>");
        } else {
            model.put("fila_archivo_control", "");
        }

        // fila_archivos_full_ifrs: filas HTML para template confirmar-aprobado-no-carga.html (Full IFRS sin datos)
        if (isFullIfrs && sinDatos) {
            String nombreXlsx = escape(defaultIfBlank(planilla.getNombreArchivoFuente(), "N/D"));
            StringBuilder filas = new StringBuilder();
            filas.append("<tr>")
                 .append("<td style=\"background:#d9d9d9; border:1px solid #8a8a8a; padding:6px 10px; line-height:16px; font-weight:700; vertical-align:middle; mso-line-height-rule:exactly;\">Planilla generada autom&#225;ticamente</td>")
                 .append("<td style=\"background:#f3f3f3; border:1px solid #8a8a8a; padding:6px 10px; line-height:16px; text-align:center; font-weight:400; vertical-align:middle; mso-line-height-rule:exactly;\">").append(nombreXlsx).append("</td>")
                 .append("</tr>");
            if (nombreArchivoControl != null) {
                filas.append("<tr>")
                     .append("<td style=\"background:#d9d9d9; border:1px solid #8a8a8a; padding:6px 10px; line-height:16px; font-weight:700; vertical-align:middle; mso-line-height-rule:exactly;\">Archivo control generado</td>")
                     .append("<td style=\"background:#f3f3f3; border:1px solid #8a8a8a; padding:6px 10px; line-height:16px; text-align:center; font-weight:400; vertical-align:middle; mso-line-height-rule:exactly;\">").append(escape(nombreArchivoControl)).append("</td>")
                     .append("</tr>");
            }
            model.put("fila_archivos_full_ifrs", filas.toString());
        } else {
            model.put("fila_archivos_full_ifrs", "");
        }

        EmailMessage email = new EmailMessage(
                subject,
                recipients(users.usuarioCarga().email(), users.lider().email()),
                render(template, model));
        send(email, "aprobacion", planilla.getId());
    }

    /**
     * Envía la notificación de rechazo incluyendo el motivo funcional registrado.
     */
    @Override
    public void notificarRechazo(SiproDetalleCargaPlanillas planilla, String motivoRechazo) {
        boolean sinDatos = Boolean.TRUE.equals(planilla.getNoReportaDatos());
        String template = sinDatos ? "confirmar-rechazado-no-carga.html" : "confirmar-rechazado-si-carga.html";
        String subject = sinDatos
                ? String.format("SIPRO | No carga rechazada | %s | %s", textoPlano(planilla.getProducto()), formatFechaCorta(planilla.getFechaCorteInformacion()))
                : String.format("SIPRO | Carga manual rechazada | %s | %s", textoPlano(planilla.getProducto()), formatFechaCorta(planilla.getFechaCorteInformacion()));

            NotificationUsers users = resolveNotificationUsers(planilla);
            Map<String, String> model = buildBaseModel(planilla, users);
        model.put("motivo_rechazo", escape(defaultIfBlank(motivoRechazo, "No informado")));
        EmailMessage email = new EmailMessage(
                subject,
                recipients(users.usuarioCarga().email(), users.lider().email()),
                render(template, model));
        send(email, "rechazo", planilla.getId());
    }

            private Map<String, String> buildBaseModel(SiproDetalleCargaPlanillas planilla, NotificationUsers users) {
        Map<String, String> model = new LinkedHashMap<>();
                model.put("banner_url", escape(mailTemplateNotificationService.resolveBannerSrc()));
            model.put("usuario_carga", escape(users.usuarioCarga().displayName()));
            model.put("usuario_red", escape(users.usuarioCarga().username()));
            model.put("correo_usuario_carga", escape(users.usuarioCarga().email()));
            model.put("lider_aprobador", escape(users.lider().displayName()));
            model.put("correo_lider", escape(users.lider().email()));
        model.put("nombre_area", escape(defaultIfBlank(planilla.getNombreArea(), "No asignada")));
        model.put("producto", escape(defaultIfBlank(planilla.getProducto(), "No informado")));
        model.put("segmento", escape(defaultIfBlank(planilla.getSegmento(), "No informado")));
        model.put("fecha_corte", escape(formatFechaLarga(planilla.getFechaCorteInformacion())));
        model.put("descripcion", escape(defaultIfBlank(planilla.getDescripcionLarga(), "No informada")));
        model.put("nombre_archivo", escape(resolveNombreArchivo(planilla)));
        model.put("peso_archivo", escape(resolvePesoArchivo(planilla.getPesoArchivoFuente())));

        enrichValidationData(model, planilla.getId());
        return model;
    }

    private void enrichValidationData(Map<String, String> model, Long idCargaPlanilla) {
        model.put("registros_validados", "No disponible");
        model.put("registros_rechazados", "No disponible");
        model.put("numero_columnas", "No disponible");
        model.put("porcentaje_completitud", "No disponible");
        model.put("resumen_moneda_rows", buildResumenMonedaFallbackRows());

        validacionRepository.findByIdCargaPlanilla(idCargaPlanilla).ifPresent(validacion -> {
            model.put("registros_validados", valueOrNoDisponible(validacion.getRegistrosValidados(), validacion.getNumeroFilasDatos()));
            model.put("registros_rechazados", valueOrNoDisponible(validacion.getRegistrosRechazados()));
            model.put("numero_columnas", valueOrNoDisponible(validacion.getNumeroColumnas()));
            model.put("porcentaje_completitud", escape(formatPorcentaje(validacion.getPorcentajeCompletitud())));
            model.put("resumen_moneda_rows", buildResumenMonedaRows(validacion));
        });
    }

    private String buildResumenMonedaRows(SiproDetalleArchivoValidacion validacion) {
        if (validacion == null || validacion.getId() == null) {
            return buildResumenMonedaFallbackRows();
        }

        List<SiproResumenPorMoneda> resumenes = resumenPorMonedaRepository.findByIdValidacionOrderByCodigoMonedaAsc(validacion.getId());
        if (resumenes.isEmpty()) {
            return buildResumenMonedaFallbackRows();
        }

        StringBuilder html = new StringBuilder();
        for (SiproResumenPorMoneda resumen : resumenes) {
            html.append("<tr>")
                    .append(cellData(escape(defaultIfBlank(resumen.getCodigoMoneda(), "No disponible"))))
                    .append(cellData(escape(resumen.getCantidadRegistros() == null ? "No disponible" : String.valueOf(resumen.getCantidadRegistros()))))
                    .append(cellData(escape(formatMontoResumen(resumen.getSumaVlrInicialObligacion()))))
                    .append("</tr>");
        }
        return html.toString();
    }

    private String buildResumenMonedaFallbackRows() {
        return new StringBuilder()
                .append("<tr>")
                .append(cellData("No disponible"))
                .append(cellData("No disponible"))
                .append(cellData("No disponible"))
                .append("</tr>")
                .toString();
    }

    private String cellData(String value) {
        return "<td style=\"background:#f3f3f3; border:1px solid #8a8a8a; padding:6px 10px; line-height:16px; font-size:13px; color:#333333; font-weight:400; text-align:center; vertical-align:middle; mso-line-height-rule:exactly;\">"
                + value +
                "</td>";
    }

    private String resolveActionUrl() {
        String parametroClave = resolveActionUrlKey();
        String url = parametroUnicoService.getString(parametroClave)
                .orElseGet(properties::getActionUrl);

        if (isBlank(url)) {
            logger.warn("No se encontró URL de acción para la clave {} y tampoco hay fallback configurado.", parametroClave);
            return "#";
        }

        return url.trim();
    }

    private String resolveActionUrlKey() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prd".equalsIgnoreCase(profile) || "pdn".equalsIgnoreCase(profile)) {
                return "URL_PDN";
            }
            if ("qa".equalsIgnoreCase(profile)) {
                return "URL_QA";
            }
            if ("dev".equalsIgnoreCase(profile)) {
                return "URL_DEV";
            }
        }

        return "URL_DEV";
    }

    private void send(EmailMessage email, String etapa, Long planillaId) {
        mailTemplateNotificationService.sendHtml(
                new MailTemplateNotificationService.EmailPayload(email.subject(), email.recipients(), email.htmlBody()),
                etapa,
                "planilla " + planillaId);
    }

    private String render(String templateName, Map<String, String> model) {
        return mailTemplateNotificationService.renderPlanillaTemplate(templateName, model);
    }

    private List<String> recipients(String... addresses) {
        Set<String> unique = new LinkedHashSet<>();
        for (String address : addresses) {
            if (!isBlank(address) && !"N/A".equalsIgnoreCase(address)) {
                unique.add(address.trim());
            }
        }
        return new ArrayList<>(unique);
    }

        private NotificationUsers resolveNotificationUsers(SiproDetalleCargaPlanillas planilla) {
        ResolvedUser usuarioCarga = resolveUser(
            planilla.getIdUsuarioCarga(),
            planilla.getNombreUsuarioCarga(),
            planilla.getCorreoUsuarioCarga());
        ResolvedUser lider = resolveUser(
            planilla.getIdLider(),
            planilla.getNombreLider(),
            planilla.getCorreoLider());

        logger.info(
            "Destinatarios resueltos planilla {} | usuarioCarga[id={}, username={}, email={}, fuente={}] | lider[id={}, username={}, email={}, fuente={}]",
            planilla.getId(),
            usuarioCarga.idUsuario(),
            usuarioCarga.username(),
            usuarioCarga.email(),
            usuarioCarga.source(),
            lider.idUsuario(),
            lider.username(),
            lider.email(),
            lider.source());

        return new NotificationUsers(usuarioCarga, lider);
        }

        private ResolvedUser resolveUser(Long idUsuario, String fallbackName, String fallbackEmail) {
        Optional<UsuarioPersona> persona = idUsuario == null
            ? Optional.empty()
            : usuarioPersonaRepository.findById(idUsuario);
        Optional<String> username = idUsuario == null
            ? Optional.empty()
            : usuarioLoginRepository.findById(idUsuario).map(usuario -> defaultIfBlank(usuario.getUsuario(), null));

        String resolvedName = persona
            .map(this::nombreCompleto)
            .filter(value -> !isBlank(value))
            .orElse(defaultIfBlank(fallbackName, username.orElse("No informado")));

        String personaEmail = persona
            .map(UsuarioPersona::getCorreo)
            .filter(value -> !isBlank(value))
            .orElse(null);
        String resolvedEmail;
        String emailSource;
        if (!isBlank(personaEmail)) {
            resolvedEmail = personaEmail;
            emailSource = "usuario_persona";
        } else {
            if (idUsuario != null) {
                logger.warn("No se encontro correo en usuario_persona para idUsuario {}. Se utilizara correo fallback de la planilla.", idUsuario);
            }
            resolvedEmail = defaultIfBlank(fallbackEmail, "No informado");
            emailSource = "fallback_planilla";
        }
        String resolvedUsername = username.orElse("No informado");
        String nameSource = persona.filter(value -> !isBlank(nombreCompleto(value))).isPresent()
            ? "usuario_persona"
            : "fallback_planilla";
        String usernameSource = username.isPresent() ? "usuario_login" : "fallback_planilla";
        String source = "nombre=" + nameSource + ",email=" + emailSource + ",usuario=" + usernameSource;

        return new ResolvedUser(idUsuario, resolvedName, resolvedEmail, resolvedUsername, source);
        }

        private String nombreCompleto(UsuarioPersona persona) {
        String nombres = defaultIfBlank(persona.getNombres(), "");
        String apellidos = defaultIfBlank(persona.getApellidos(), "");
        String nombreCompleto = (nombres + " " + apellidos).trim();
        return defaultIfBlank(nombreCompleto, "No informado");
    }

    private String resolveNombreArchivo(SiproDetalleCargaPlanillas planilla) {
        if (Boolean.TRUE.equals(planilla.getNoReportaDatos())) {
            return "No aplica";
        }
        return defaultIfBlank(planilla.getNombreArchivoFuente(), "No informado");
    }

    /**
     * Deriva el nombre del archivo de control a partir de la ruta almacenada.
     * La ruta tiene formato: "pendientes/YYYY-MM-DD/UUID__NOMBRE-CTRL.txt"
     * Se extrae el nombre de archivo y se elimina el prefijo UUID__.
     */
    private String resolveNombreArchivoControl(SiproDetalleCargaPlanillas planilla) {
        String ruta = planilla.getRutaArchivoControl();
        if (ruta == null || ruta.isEmpty()) return null;
        // Obtener el nombre de archivo (después del último '/')
        String filename = ruta.substring(ruta.lastIndexOf('/') + 1);
        // Eliminar el prefijo UUID__ si existe
        int idx = filename.indexOf("__");
        if (idx >= 0) {
            filename = filename.substring(idx + 2);
        }
        return filename.isEmpty() ? null : filename;
    }

    private String resolvePesoArchivo(Long pesoArchivoFuente) {
        if (pesoArchivoFuente == null || pesoArchivoFuente <= 0) {
            return "No aplica";
        }
        double kiloBytes = pesoArchivoFuente / 1024.0;
        if (kiloBytes < 1024) {
            return String.format(LOCALE_ES_CO, "%.1f KB", kiloBytes);
        }
        return String.format(LOCALE_ES_CO, "%.2f MB", kiloBytes / 1024.0);
    }

    private String formatFechaLarga(LocalDate fecha) {
        if (fecha == null) {
            return "No informada";
        }
        return FECHA_CORTE_FORMATTER.format(fecha);
    }

    private String formatFechaCorta(LocalDate fecha) {
        if (fecha == null) {
            return "Sin fecha";
        }
        return fecha.toString();
    }

    private String formatPorcentaje(BigDecimal porcentaje) {
        if (porcentaje == null) {
            return "No disponible";
        }

        BigDecimal valor = porcentaje;
        if (valor.compareTo(BigDecimal.ONE) <= 0) {
            NumberFormat formatter = NumberFormat.getPercentInstance(LOCALE_ES_CO);
            formatter.setMinimumFractionDigits(1);
            formatter.setMaximumFractionDigits(2);
            return formatter.format(valor);
        }

        NumberFormat formatter = NumberFormat.getNumberInstance(LOCALE_ES_CO);
        formatter.setMinimumFractionDigits(1);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(valor) + "%";
    }

    private String formatMontoResumen(BigDecimal valor) {
        if (valor == null) {
            return "No disponible";
        }

        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return "$ " + formatter.format(valor);
    }

    private String valueOrNoDisponible(Integer primary) {
        if (primary == null) {
            return "No disponible";
        }
        return escape(String.valueOf(primary));
    }

    private String valueOrNoDisponible(Integer primary, Integer fallback) {
        Integer value = primary != null ? primary : fallback;
        if (value == null) {
            return "No disponible";
        }
        return escape(String.valueOf(value));
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value, StandardCharsets.UTF_8.name());
    }

    private String textoPlano(String value) {
        return defaultIfBlank(value, "Producto");
    }

    private record NotificationUsers(ResolvedUser usuarioCarga, ResolvedUser lider) {
    }

    private record ResolvedUser(Long idUsuario, String displayName, String email, String username, String source) {
    }

    private record EmailMessage(String subject, List<String> recipients, String htmlBody) {
    }
}