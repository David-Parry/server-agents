package com.davidparry.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing an LLM model.
 * The model identifier string is the primary key.
 */
@Entity
@Table(name = "llm_model")
public class LlmModelEntity {

    @Id
    @Column(length = 100)
    private String model;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(length = 500)
    private String description;

    /**
     * Default token allowance when this model is linked to new customers.
     * 0 = no access by default, NULL = unlimited by default.
     */
    @Column(name = "default_tokens_for_new_customers")
    private Long defaultTokensForNewCustomers = 0L;

    @Column(name = "input_token_price_per_million", precision = 10, scale = 4)
    private BigDecimal inputTokenPricePerMillion = BigDecimal.ZERO;

    @Column(name = "output_token_price_per_million", precision = 10, scale = 4)
    private BigDecimal outputTokenPricePerMillion = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and setters
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getDefaultTokensForNewCustomers() {
        return defaultTokensForNewCustomers;
    }

    public void setDefaultTokensForNewCustomers(Long defaultTokensForNewCustomers) {
        this.defaultTokensForNewCustomers = defaultTokensForNewCustomers;
    }

    public BigDecimal getInputTokenPricePerMillion() {
        return inputTokenPricePerMillion;
    }

    public void setInputTokenPricePerMillion(BigDecimal inputTokenPricePerMillion) {
        this.inputTokenPricePerMillion = inputTokenPricePerMillion;
    }

    public BigDecimal getOutputTokenPricePerMillion() {
        return outputTokenPricePerMillion;
    }

    public void setOutputTokenPricePerMillion(BigDecimal outputTokenPricePerMillion) {
        this.outputTokenPricePerMillion = outputTokenPricePerMillion;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
