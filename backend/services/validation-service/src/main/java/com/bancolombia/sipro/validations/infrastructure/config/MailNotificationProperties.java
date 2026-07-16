package com.bancolombia.sipro.validations.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agrupa la configuración de correo usada para notificaciones funcionales del proceso.
 */
@Component
@ConfigurationProperties(prefix = "app.mail")
public class MailNotificationProperties {

    private boolean enabled;
    private String transport = "outlook-win32";
    private String from;
    private String actionUrl;
    private String bannerUrl;
    private String bannerResourcePath = "classpath:static/email-assets/SIPRO_BannerCorreo.png";
    private String outlookPowershellPath = "powershell.exe";
    private boolean logPreview = true;
    private final Ses ses = new Ses();

    public Ses getSes() {
        return ses;
    }

    /** Configuracion del transporte "ses-api" (AWS SES via SDK, sin SMTP). */
    public static class Ses {
        private String region = "us-east-1";
        private String accessKey;
        private String secretKey;

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFrom() {
        return from;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public void setBannerUrl(String bannerUrl) {
        this.bannerUrl = bannerUrl;
    }

    public String getBannerResourcePath() {
        return bannerResourcePath;
    }

    public void setBannerResourcePath(String bannerResourcePath) {
        this.bannerResourcePath = bannerResourcePath;
    }

    public String getOutlookPowershellPath() {
        return outlookPowershellPath;
    }

    public void setOutlookPowershellPath(String outlookPowershellPath) {
        this.outlookPowershellPath = outlookPowershellPath;
    }

    public boolean isLogPreview() {
        return logPreview;
    }

    public void setLogPreview(boolean logPreview) {
        this.logPreview = logPreview;
    }
}