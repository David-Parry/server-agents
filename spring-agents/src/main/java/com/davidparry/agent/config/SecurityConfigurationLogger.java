package com.davidparry.agent.config;

import com.davidparry.agent.service.SecurityAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs security configuration at application startup for audit purposes.
 * Records all configured secret versions to the audit log.
 */
@Component
public class SecurityConfigurationLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfigurationLogger.class);

    private final TokenHashingProperties hashingProperties;
    private final SecurityAuditService auditService;

    public SecurityConfigurationLogger(
            TokenHashingProperties hashingProperties,
            SecurityAuditService auditService) {
        this.hashingProperties = hashingProperties;
        this.auditService = auditService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logSecurityConfiguration() {
        String currentVersion = hashingProperties.currentSecretVersion();

        LOGGER.info("=== Security Configuration ===");
        LOGGER.info("Available secret versions: {}", hashingProperties.getAvailableVersions());
        LOGGER.info("Current secret version: {}", currentVersion);
        LOGGER.info("==============================");

        // Audit log each configured version
        for (String version : hashingProperties.getAvailableVersions()) {
            boolean isCurrent = version.equals(currentVersion);
            auditService.logSecretVersionConfigured(version, isCurrent);
        }
    }
}
