package com.bancolombia.sipro.validations.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agrupa los parámetros del executor usado por las validaciones asíncronas.
 */
@ConfigurationProperties(prefix = "app.validation.async")
public class ValidationAsyncProperties {

    private int corePoolSize = 2;
    private int maxPoolSize = 4;
    private int queueCapacity = 16;
    private String threadNamePrefix = "validation-job-";

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }
}