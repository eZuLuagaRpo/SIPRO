package com.bancolombia.sipro.validations.infrastructure.notification;

import com.bancolombia.sipro.validations.infrastructure.config.MailNotificationProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.RawMessage;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Soporte compartido para renderizar plantillas HTML SIPRO y enviarlas por el transporte configurado.
 */
@Service
public class MailTemplateNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(MailTemplateNotificationService.class);
    private static final String BANNER_CONTENT_ID = "sipro-banner";
    private static final String OUTLOOK_SCRIPT_PATH = "classpath:mail-scripts/send-outlook-mail.ps1";

    private final MailNotificationProperties properties;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final ResourceLoader resourceLoader;
    private final ConcurrentMap<String, String> templateCache = new ConcurrentHashMap<>();
    private volatile BannerResource cachedBanner;

    public MailTemplateNotificationService(MailNotificationProperties properties,
                                           ObjectProvider<JavaMailSender> mailSenderProvider,
                                           ResourceLoader resourceLoader) {
        this.properties = properties;
        this.mailSenderProvider = mailSenderProvider;
        this.resourceLoader = resourceLoader;
    }

    public String renderPlanillaTemplate(String templateName, Map<String, String> model) {
        return renderTemplate("planillas", templateName, model);
    }

    public String renderTemplate(String folder, String templateName, Map<String, String> model) {
        String safeFolder = isBlank(folder) ? "planillas" : folder.trim();
        String safeTemplateName = normalizeTemplateName(templateName);
        String cacheKey = safeFolder + "/" + safeTemplateName;
        try {
            String template = templateCache.computeIfAbsent(cacheKey, key -> loadTemplate(safeFolder, safeTemplateName));
            String rendered = template;
            for (Map.Entry<String, String> entry : model.entrySet()) {
                rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return rendered.replaceAll("\\{\\{[a-zA-Z0-9_]+}}", "");
        } catch (RuntimeException ex) {
            logger.error("No fue posible renderizar la plantilla {}/{}: {}", safeFolder, safeTemplateName, ex.getMessage(), ex);
            return "<html><body><p>No fue posible generar el correo.</p></body></html>";
        }
    }

    public String resolveBannerSrc() {
        BannerResource banner = resolveBannerResource();
        if (banner != null) {
            return "cid:" + BANNER_CONTENT_ID;
        }
        return defaultIfBlank(properties.getBannerUrl(), "");
    }

    public String resolveActionUrl() {
        String actionUrl = defaultIfBlank(properties.getActionUrl(), "").trim();
        if (isBlank(actionUrl)) {
            logger.warn("No hay app.mail.actionUrl configurado; se usará '#'.");
            return "#";
        }
        return actionUrl;
    }

    public DeliveryResult sendHtml(EmailPayload email, String contexto, String referencia) {
        String safeContexto = defaultIfBlank(contexto, "notificacion");
        String safeReferencia = defaultIfBlank(referencia, "sin referencia");
        EmailPayload normalizedEmail = normalize(email);

        if (normalizedEmail.recipients().isEmpty()) {
            logger.warn("No se enviara correo de {} para {}: no hay destinatarios validos.", safeContexto, safeReferencia);
            return DeliveryResult.skipped("no hay destinatarios validos");
        }

        logger.info(
                "Preparando correo de {} para {} | enabled={} | transport={} | destinatarios={}",
                safeContexto,
                safeReferencia,
                properties.isEnabled(),
                defaultIfBlank(properties.getTransport(), "outlook-win32"),
                normalizedEmail.recipients());

        if (!properties.isEnabled()) {
            logPreview(normalizedEmail, safeContexto, safeReferencia, "mail deshabilitado");
            return DeliveryResult.skipped("mail deshabilitado");
        }

        String transport = defaultIfBlank(properties.getTransport(), "outlook-win32")
                .trim()
                .toLowerCase(Locale.ROOT);

        if ("preview".equals(transport)) {
            logPreview(normalizedEmail, safeContexto, safeReferencia, "modo preview");
            return DeliveryResult.skipped("modo preview");
        }

        if ("smtp".equals(transport)) {
            return sendViaSmtp(normalizedEmail, safeContexto, safeReferencia);
        }

        if ("outlook-win32".equals(transport)) {
            return sendViaOutlook(normalizedEmail, safeContexto, safeReferencia);
        }

        if ("ses-api".equals(transport)) {
            return sendViaSesApi(normalizedEmail, safeContexto, safeReferencia);
        }

        logPreview(normalizedEmail, safeContexto, safeReferencia, "transporte no soportado: " + transport);
        return DeliveryResult.failed("transporte no soportado: " + transport);
    }

    private DeliveryResult sendViaSmtp(EmailPayload email, String contexto, String referencia) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            logPreview(email, contexto, referencia, "JavaMailSender no disponible");
            return DeliveryResult.failed("JavaMailSender no disponible");
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setTo(email.recipients().toArray(new String[0]));
            if (!email.cc().isEmpty()) {
                helper.setCc(email.cc().toArray(new String[0]));
            }
            if (!isBlank(properties.getFrom())) {
                helper.setFrom(properties.getFrom());
            }
            helper.setSubject(email.subject());
            helper.setText(email.htmlBody(), true);

            BannerResource banner = resolveBannerResource();
            if (banner != null) {
                helper.addInline(BANNER_CONTENT_ID, banner.asByteArrayResource(), "image/png");
            }

            mailSender.send(mimeMessage);
            logger.info("Correo de {} enviado para {} a {}", contexto, referencia, email.recipients());
            return DeliveryResult.sent("smtp");
        } catch (Exception ex) {
            logger.error("Error enviando correo de {} para {} por SMTP: {}", contexto, referencia, ex.getMessage(), ex);
            logPreview(email, contexto, referencia, "fallo SMTP");
            return DeliveryResult.failed(sanitizeMessage(ex.getMessage()));
        }
    }

    private DeliveryResult sendViaOutlook(EmailPayload email, String contexto, String referencia) {
        if (!isWindows()) {
            logPreview(email, contexto, referencia, "Outlook Win32 solo esta disponible en Windows");
            return DeliveryResult.failed("Outlook Win32 solo esta disponible en Windows");
        }

        Path htmlFile = null;
        Path bannerFile = null;
        Path scriptFile = null;

        try {
            htmlFile = Files.createTempFile("sipro-mail-", ".html");
            Files.writeString(htmlFile, email.htmlBody(), StandardCharsets.UTF_8);

            BannerResource banner = resolveBannerResource();
            if (banner != null) {
                bannerFile = banner.writeToTempFile();
            }

            scriptFile = materializeResourceToTempFile(OUTLOOK_SCRIPT_PATH, "sipro-outlook-", ".ps1");

            ProcessBuilder builder = new ProcessBuilder(
                    defaultIfBlank(properties.getOutlookPowershellPath(), "powershell.exe"),
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    scriptFile.toString(),
                    email.subject(),
                    String.join(";", email.recipients()),
                    htmlFile.toString(),
                    bannerFile != null ? bannerFile.toString() : "");
            builder.redirectErrorStream(true);

            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("PowerShell finalizo con codigo " + exitCode
                        + (isBlank(output) ? "" : ": " + output));
            }

            logger.info("Correo de {} enviado por Outlook Win32 para {} a {}", contexto, referencia, email.recipients());
            if (!isBlank(output)) {
                logger.info("Salida Outlook Win32 para {}: {}", referencia, output);
            }
            return DeliveryResult.sent("outlook-win32");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Envio Outlook interrumpido para {}: {}", referencia, ex.getMessage(), ex);
            logPreview(email, contexto, referencia, "envio Outlook interrumpido");
            return DeliveryResult.failed("envio Outlook interrumpido");
        } catch (Exception ex) {
            logger.error("Error enviando correo de {} para {} por Outlook: {}", contexto, referencia, ex.getMessage(), ex);
            logPreview(email, contexto, referencia, "fallo Outlook Win32");
            return DeliveryResult.failed(sanitizeMessage(ex.getMessage()));
        } finally {
            deleteQuietly(htmlFile);
            deleteQuietly(bannerFile);
            deleteQuietly(scriptFile);
        }
    }

    /**
     * Envia el correo por AWS SES via API (sesv2), no SMTP. Arma el mismo MIME que la ruta
     * SMTP (con el banner inline) y lo entrega a SES como mensaje "raw" — la version simple
     * de la API de SES no soporta adjuntos/imagenes inline, por eso se usa el modo raw aqui.
     */
    private DeliveryResult sendViaSesApi(EmailPayload email, String contexto, String referencia) {
        MailNotificationProperties.Ses sesProperties = properties.getSes();
        if (isBlank(sesProperties.getRegion()) || isBlank(sesProperties.getAccessKey()) || isBlank(sesProperties.getSecretKey())) {
            logPreview(email, contexto, referencia, "SES no configurado (region/credenciales faltantes)");
            return DeliveryResult.failed("SES no configurado (region/credenciales faltantes)");
        }

        try {
            Session session = Session.getInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setTo(email.recipients().toArray(new String[0]));
            if (!email.cc().isEmpty()) {
                helper.setCc(email.cc().toArray(new String[0]));
            }
            if (!isBlank(properties.getFrom())) {
                helper.setFrom(properties.getFrom());
            }
            helper.setSubject(email.subject());
            helper.setText(email.htmlBody(), true);

            BannerResource banner = resolveBannerResource();
            if (banner != null) {
                helper.addInline(BANNER_CONTENT_ID, banner.asByteArrayResource(), "image/png");
            }

            ByteArrayOutputStream rawOutput = new ByteArrayOutputStream();
            mimeMessage.writeTo(rawOutput);

            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    sesProperties.getAccessKey(), sesProperties.getSecretKey());

            List<String> allDestinations = new ArrayList<>(email.recipients());
            allDestinations.addAll(email.cc());

            try (SesV2Client sesClient = SesV2Client.builder()
                    .region(Region.of(sesProperties.getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build()) {

                SendEmailRequest request = SendEmailRequest.builder()
                        .destination(Destination.builder()
                                .toAddresses(email.recipients())
                                .ccAddresses(email.cc())
                                .build())
                        .content(EmailContent.builder()
                                .raw(RawMessage.builder()
                                        .data(SdkBytes.fromByteArray(rawOutput.toByteArray()))
                                        .build())
                                .build())
                        .build();

                SendEmailResponse response = sesClient.sendEmail(request);
                logger.info("Correo de {} enviado para {} a {} via SES API (messageId={})",
                        contexto, referencia, allDestinations, response.messageId());
                return DeliveryResult.sent("ses-api");
            }
        } catch (Exception ex) {
            logger.error("Error enviando correo de {} para {} por SES API: {}", contexto, referencia, ex.getMessage(), ex);
            logPreview(email, contexto, referencia, "fallo SES API");
            return DeliveryResult.failed(sanitizeMessage(ex.getMessage()));
        }
    }

    private EmailPayload normalize(EmailPayload email) {
        if (email == null) {
            return new EmailPayload("SIPRO", List.of(), List.of(), "<html><body><p>Sin contenido.</p></body></html>");
        }

        return new EmailPayload(
                defaultIfBlank(email.subject(), "SIPRO"),
                dedupeAndTrim(email.recipients()),
                dedupeAndTrim(email.cc()),
                defaultIfBlank(email.htmlBody(), "<html><body><p>Sin contenido.</p></body></html>"));
    }

    private List<String> dedupeAndTrim(List<String> values) {
        Set<String> unique = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (!isBlank(value)) {
                    unique.add(value.trim());
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private void logPreview(EmailPayload email, String contexto, String referencia, String motivo) {
        if (!properties.isLogPreview()) {
            return;
        }

        String html = email.htmlBody();
        String preview = html.length() > 1200 ? html.substring(0, 1200) + "..." : html;
        logger.info(
                "Preview correo {} para {} ({}) | asunto={} | destinatarios={} | html={}",
                contexto,
                referencia,
                motivo,
                email.subject(),
                email.recipients(),
                preview);
    }

    private String loadTemplate(String folder, String templateName) {
        String resourcePath = "classpath:mail-templates/" + folder + "/" + templateName;
        Resource resource = resourceLoader.getResource(resourcePath);
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("No fue posible cargar la plantilla: " + resourcePath, ex);
        }
    }

    private String normalizeTemplateName(String templateName) {
        String safeTemplateName = defaultIfBlank(templateName, "").trim();
        if (safeTemplateName.isEmpty()) {
            return safeTemplateName;
        }
        return safeTemplateName.endsWith(".html") ? safeTemplateName : safeTemplateName + ".html";
    }

    private BannerResource resolveBannerResource() {
        BannerResource banner = cachedBanner;
        if (banner != null) {
            return banner;
        }

        synchronized (this) {
            if (cachedBanner != null) {
                return cachedBanner;
            }

            String resourcePath = defaultIfBlank(properties.getBannerResourcePath(),
                    "classpath:static/email-assets/SIPRO_BannerCorreo.png");
            Resource resource = resourceLoader.getResource(resourcePath);
            if (!resource.exists()) {
                logger.warn("Banner de correo no encontrado en {}. Se usara banner-url si esta configurado.", resourcePath);
                return null;
            }

            try {
                String fileName = defaultIfBlank(resource.getFilename(), "SIPRO_BannerCorreo.png");
                byte[] bytes = StreamUtils.copyToByteArray(resource.getInputStream());
                cachedBanner = new BannerResource(fileName, bytes);
                return cachedBanner;
            } catch (IOException ex) {
                logger.warn("No fue posible cargar el banner de correo desde {}: {}", resourcePath, ex.getMessage());
                return null;
            }
        }
    }

    private Path materializeResourceToTempFile(String resourcePath, String prefix, String suffix) throws IOException {
        Resource resource = resourceLoader.getResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalStateException("No existe el recurso requerido: " + resourcePath);
        }

        Path tempFile = Files.createTempFile(prefix, suffix);
        Files.write(tempFile, StreamUtils.copyToByteArray(resource.getInputStream()));
        return tempFile;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            logger.debug("No fue posible eliminar archivo temporal {}: {}", path, ex.getMessage());
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "sin detalle";
        }
        return message.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record BannerResource(String fileName, byte[] bytes) {

        private ByteArrayResource asByteArrayResource() {
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
        }

        private Path writeToTempFile() throws IOException {
            String safeName = fileName == null || fileName.isBlank() ? "SIPRO_BannerCorreo.png" : fileName;
            String suffix = safeName.contains(".") ? safeName.substring(safeName.lastIndexOf('.')) : ".tmp";
            Path tempFile = Files.createTempFile("sipro-banner-", suffix);
            Files.write(tempFile, bytes);
            return tempFile;
        }
    }

    public enum DeliveryStatus {
        SENT,
        SKIPPED,
        FAILED
    }

    public record EmailPayload(String subject, List<String> recipients, List<String> cc, String htmlBody) {

        /** Compatibilidad con llamadores existentes que no envian CC. */
        public EmailPayload(String subject, List<String> recipients, String htmlBody) {
            this(subject, recipients, List.of(), htmlBody);
        }
    }

    public record DeliveryResult(DeliveryStatus status, String detail) {

        public static DeliveryResult sent(String detail) {
            return new DeliveryResult(DeliveryStatus.SENT, detail);
        }

        public static DeliveryResult skipped(String detail) {
            return new DeliveryResult(DeliveryStatus.SKIPPED, detail);
        }

        public static DeliveryResult failed(String detail) {
            return new DeliveryResult(DeliveryStatus.FAILED, detail);
        }

        public boolean sent() {
            return status == DeliveryStatus.SENT;
        }
    }
}