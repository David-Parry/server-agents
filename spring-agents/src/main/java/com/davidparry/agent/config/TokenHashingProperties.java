package com.davidparry.agent.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;
import java.util.Set;

/**
 * Configuration properties for token hashing with multi-version secret support.
 * Supports secret rotation by maintaining multiple active secrets.
 * 
 * Version naming follows sequential pattern: V1, V2, V3, etc.
 */
@Validated
@ConfigurationProperties(prefix = "agent.security")
public record TokenHashingProperties(
    /**
     * Map of secret versions to their values.
     * Keys must follow pattern: V1, V2, V3, etc.
     * Example: {"V1": "old-secret", "V2": "new-secret"}
     */
    @NotEmpty(message = "At least one hashing secret must be configured")
    Map<String, String> hashingSecrets,
    
    /**
     * The current version to use for hashing NEW tokens.
     * Must be a key in hashingSecrets map.
     */
    @NotBlank(message = "Current secret version must be specified")
    @Pattern(regexp = "V\\d+", message = "Current secret version must match pattern V1, V2, etc.")
    String currentSecretVersion
) {
    /**
     * Compact constructor with validation.
     */
    public TokenHashingProperties {
        // Validate current version exists in the map
        if (!hashingSecrets.containsKey(currentSecretVersion)) {
            throw new IllegalArgumentException(
                "Current secret version '" + currentSecretVersion + 
                "' not found in hashingSecrets. Available versions: " + hashingSecrets.keySet()
            );
        }
        
        // Validate all version keys match the pattern and secrets meet minimum length
        for (Map.Entry<String, String> entry : hashingSecrets.entrySet()) {
            String version = entry.getKey();
            String secret = entry.getValue();
            
            // Validate version key format
            if (!version.matches("V\\d+")) {
                throw new IllegalArgumentException(
                    "Secret version key '" + version + 
                    "' must match pattern V1, V2, etc."
                );
            }
            
            // Validate minimum secret length
            if (secret == null || secret.length() < 32) {
                throw new IllegalArgumentException(
                    "Secret for version '" + version + 
                    "' must be at least 32 characters for security"
                );
            }
        }
    }
    
    /**
     * Gets the secret for a specific version.
     * @param version The version key (e.g., "V1", "V2")
     * @return The secret string
     * @throws IllegalArgumentException if version not found
     */
    public String getSecret(String version) {
        String secret = hashingSecrets.get(version);
        if (secret == null) {
            throw new IllegalArgumentException(
                "Unknown secret version: " + version + 
                ". Available versions: " + hashingSecrets.keySet()
            );
        }
        return secret;
    }
    
    /**
     * Gets the current secret (for hashing new tokens).
     */
    public String getCurrentSecret() {
        return hashingSecrets.get(currentSecretVersion);
    }
    
    /**
     * Checks if a version exists in the configuration.
     */
    public boolean hasVersion(String version) {
        return hashingSecrets.containsKey(version);
    }
    
    /**
     * Gets all configured version keys.
     */
    public Set<String> getAvailableVersions() {
        return hashingSecrets.keySet();
    }
}
