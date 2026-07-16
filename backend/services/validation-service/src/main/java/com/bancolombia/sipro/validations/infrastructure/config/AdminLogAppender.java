package com.bancolombia.sipro.validations.infrastructure.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.bancolombia.sipro.validations.domain.service.AdminLogBufferService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registra un appender adicional para alimentar el buffer del panel admin.
 */
@Component
public class AdminLogAppender extends AppenderBase<ILoggingEvent> {

    private final AdminLogBufferService adminLogBufferService;
    private Logger rootLogger;

    public AdminLogAppender(AdminLogBufferService adminLogBufferService) {
        this.adminLogBufferService = adminLogBufferService;
    }

    @PostConstruct
    public void register() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        setContext(context);
        setName("ADMIN_PANEL_BUFFER");
        start();

        rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(this);
    }

    @PreDestroy
    public void unregister() {
        if (rootLogger != null) {
            rootLogger.detachAppender(this);
        }
        stop();
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        adminLogBufferService.append(eventObject);
    }
}