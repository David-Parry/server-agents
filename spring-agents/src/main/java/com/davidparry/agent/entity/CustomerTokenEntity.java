package com.davidparry.agent.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing a customer's JWT token (hashed).
 * Supports multiple active tokens per customer for zero-downtime rotation.
 * 
 * Note: No separate salt field - the customer's customer_id (UUID) is used as salt.
 */
@Entity
@Table(name = "customer_token")
public class CustomerTokenEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerEntity customer;
    
    /**
     * One-way hash of the JWT token stored as hex string.
     * Hash is computed as: HMAC-SHA256(secret[version], customer.customerId || token)
     * Note: No separate salt field - customer.customerId (UUID) is used as salt.
     * The hash is stored as a 64-character uppercase hex string (SHA-256 = 32 bytes = 64 hex chars).
     */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;
    
    /**
     * Version identifier of the hashing secret used to create this token hash.
     * Used for secret rotation - allows verification with the correct secret.
     * Format: V1, V2, V3, etc. (sequential versioning)
     */
    @Column(name = "secret_version", nullable = false, length = 10)
    private String secretVersion = "V1";
    
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    /**
     * Checks if this token is valid (active and not expired).
     */
    public boolean isValid() {
        if (!active) {
            return false;
        }
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }
    
    /**
     * Revokes this token.
     */
    public void revoke() {
        this.active = false;
        this.revokedAt = LocalDateTime.now();
    }
    
    // Getters and setters
    public UUID getId() { 
        return id; 
    }
    
    public void setId(UUID id) { 
        this.id = id; 
    }
    
    public CustomerEntity getCustomer() { 
        return customer; 
    }
    
    public void setCustomer(CustomerEntity customer) { 
        this.customer = customer; 
    }
    
    /**
     * Gets the customer name via the customer relationship.
     * This is a convenience method - the name is stored in the customer table.
     */
    public String getCustomerName() { 
        return customer != null ? customer.getName() : null; 
    }
    
    public String getTokenHash() { 
        return tokenHash; 
    }
    
    public void setTokenHash(String tokenHash) { 
        this.tokenHash = tokenHash; 
    }
    
    public boolean isActive() { 
        return active; 
    }
    
    public void setActive(boolean active) { 
        this.active = active; 
    }
    
    public LocalDateTime getExpiresAt() { 
        return expiresAt; 
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) { 
        this.expiresAt = expiresAt; 
    }
    
    public LocalDateTime getCreatedAt() { 
        return createdAt; 
    }
    
    public LocalDateTime getRevokedAt() { 
        return revokedAt; 
    }
    
    public void setRevokedAt(LocalDateTime revokedAt) { 
        this.revokedAt = revokedAt; 
    }
    
    public String getSecretVersion() {
        return secretVersion;
    }
    
    public void setSecretVersion(String secretVersion) {
        this.secretVersion = secretVersion;
    }
}
