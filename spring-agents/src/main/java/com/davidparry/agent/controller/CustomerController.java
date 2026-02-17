package com.davidparry.agent.controller;

import com.davidparry.agent.dto.BulkPolicyTypeRequest;
import com.davidparry.agent.dto.BulkSetAllowanceRequest;
import com.davidparry.agent.dto.CreateCustomerRequest;
import com.davidparry.agent.dto.CustomerCreatedResponse;
import com.davidparry.agent.dto.CustomerResponse;
import com.davidparry.agent.dto.GenerateTokenRequest;
import com.davidparry.agent.dto.ModelAllowanceResponse;
import com.davidparry.agent.dto.MonthlyTokenUsageResponse;
import com.davidparry.agent.dto.PolicyTypeResponse;
import com.davidparry.agent.dto.SetAllowanceRequest;
import com.davidparry.agent.dto.TokenGeneratedResponse;
import com.davidparry.agent.dto.TokensRevokedResponse;
import com.davidparry.agent.dto.UpdateAllowanceNotificationThresholdRequest;
import com.davidparry.agent.dto.UpdateNotificationSettingsRequest;
import com.davidparry.agent.dto.UpdatePolicyRequest;
import com.davidparry.agent.dto.UpdateStatusRequest;
import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerModelAllowanceEntity;
import com.davidparry.agent.entity.PolicyTypeEntity;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.repository.PolicyTypeRepository;
import com.davidparry.agent.security.JwtTokenService;
import com.davidparry.agent.service.CustomerTokenService;
import com.davidparry.agent.service.CustomerUsageService;
import com.davidparry.agent.service.LlmTokenUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST API for managing customers and their API tokens.
 * Provides endpoints for creating customers, generating tokens, and managing token lifecycle.
 * API tokens are JWTs containing the customerId as a claim.
 */
@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers", description = "Customer management and token operations")
public class CustomerController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerTokenService customerTokenService;
    private final CustomerUsageService customerUsageService;
    private final CustomerRepository customerRepository;
    private final PolicyTypeRepository policyTypeRepository;
    private final JwtTokenService jwtTokenService;
    private final LlmTokenUsageService llmTokenUsageService;

    public CustomerController(
            CustomerTokenService customerTokenService,
            CustomerUsageService customerUsageService,
            CustomerRepository customerRepository,
            PolicyTypeRepository policyTypeRepository,
            JwtTokenService jwtTokenService,
            LlmTokenUsageService llmTokenUsageService) {
        this.customerTokenService = customerTokenService;
        this.customerUsageService = customerUsageService;
        this.customerRepository = customerRepository;
        this.policyTypeRepository = policyTypeRepository;
        this.jwtTokenService = jwtTokenService;
        this.llmTokenUsageService = llmTokenUsageService;
    }

    @Operation(
        summary = "Create a new customer",
        description = "Creates a new customer with a generated JWT API token. "
                + "Links all enabled models with specified default allowance and policy type."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Customer successfully created"),
        @ApiResponse(responseCode = "400", description = "Invalid request - name is required")
    })
    @PostMapping
    public ResponseEntity<CustomerCreatedResponse> createCustomer(@RequestBody CreateCustomerRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Find policy type if specified, otherwise use default unlimited
        PolicyTypeEntity policyType;
        if (request.policyTypeName() != null && !request.policyTypeName().isBlank()) {
            policyType = policyTypeRepository.findByName(request.policyTypeName())
                .orElseThrow(() -> new IllegalArgumentException("Policy type not found: " + request.policyTypeName()));
        } else {
            policyType = policyTypeRepository.findUnlimitedPolicyType()
                .orElseThrow(() -> new IllegalStateException("No unlimited policy type found"));
        }

        // Generate a new customer ID first (needed for JWT claim)
        UUID customerId = UUID.randomUUID();

        // Generate a JWT token with the customerId as a claim
        String apiToken = jwtTokenService.generateToken(customerId, request.name());

        boolean unlimited = Boolean.TRUE.equals(request.unlimited());

        // Create customer with the specified ID and generated JWT token (will be hashed internally)
        // The policy type is applied to all model allowances, not the customer itself
        CustomerEntity customer = customerTokenService.createCustomerWithId(
            customerId,
            request.name(),
            policyType,
            request.defaultAllowance(),
            unlimited,
            apiToken
        );

        LOGGER.info("Created customer: {} with ID: {} (policy: {}, unlimited: {}, default: {})",
                   customer.getName(), customer.getCustomerId(), policyType.getName(), unlimited, request.defaultAllowance());

        // Return the customer info along with the plaintext JWT token (only time it's visible)
        return ResponseEntity.status(HttpStatus.CREATED).body(new CustomerCreatedResponse(
            customer.getId(),
            customer.getCustomerId(),
            customer.getName(),
            policyType.getName(),
            policyType.getResetDays(),
            unlimited,
            request.defaultAllowance(),
            apiToken,
            "Store this JWT token securely. It cannot be retrieved again."
        ));
    }

    @Operation(
        summary = "Generate new API token",
        description = "Generates a new JWT API token for an existing customer. Optionally revokes existing tokens."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Token successfully generated"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "403", description = "Customer is disabled")
    })
    @PostMapping("/{customerId}/tokens")
    public ResponseEntity<TokenGeneratedResponse> generateToken(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId,
            @RequestBody(required = false) GenerateTokenRequest request) {

        // Verify customer exists
        CustomerEntity customer = customerRepository.findByCustomerId(customerId)
            .orElse(null);

        if (customer == null) {
            return ResponseEntity.notFound().build();
        }

        if (!customer.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new TokenGeneratedResponse(null, null, "Customer is disabled"));
        }

        // Determine expiration if specified
        LocalDateTime expiresAt = null;
        if (request != null && request.expiresInDays() != null && request.expiresInDays() > 0) {
            expiresAt = LocalDateTime.now().plusDays(request.expiresInDays());
        }

        // Generate a JWT token with the customerId as a claim
        String apiToken = jwtTokenService.generateToken(customerId, customer.getName(), expiresAt);

        // Determine if old tokens should be revoked
        boolean revokeOld = request != null && Boolean.TRUE.equals(request.revokeExisting());

        if (revokeOld) {
            customerTokenService.rotateToken(customerId, apiToken, expiresAt, true);
        } else {
            customerTokenService.registerToken(customerId, apiToken, expiresAt);
        }

        LOGGER.info("Generated new JWT token for customer: {}", customerId);

        return ResponseEntity.status(HttpStatus.CREATED).body(new TokenGeneratedResponse(
            apiToken,
            expiresAt,
            "Store this JWT token securely. It cannot be retrieved again."
        ));
    }

    @Operation(
        summary = "Revoke all tokens",
        description = "Revokes all active tokens for a customer."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tokens successfully revoked"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @DeleteMapping("/{customerId}/tokens")
    public ResponseEntity<TokensRevokedResponse> revokeAllTokens(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId) {
        // Verify customer exists
        if (!customerRepository.existsByCustomerId(customerId)) {
            return ResponseEntity.notFound().build();
        }

        int revokedCount = customerTokenService.revokeAllTokens(customerId);

        LOGGER.info("Revoked {} tokens for customer: {}", revokedCount, customerId);

        return ResponseEntity.ok(new TokensRevokedResponse(revokedCount, "All tokens have been revoked"));
    }

    @Operation(
        summary = "Get customer details",
        description = "Returns detailed information about a customer including all model allowances."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved customer"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> getCustomer(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId) {
        return customerRepository.findByCustomerId(customerId)
            .map(customer -> ResponseEntity.ok(toCustomerResponse(customer)))
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "List all customers",
        description = "Returns a list of all customers with their details and allowances."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved customer list")
    })
    @GetMapping
    public ResponseEntity<List<CustomerResponse>> listCustomers() {
        List<CustomerResponse> customers = customerRepository.findAll().stream()
            .map(this::toCustomerResponse)
            .toList();
        return ResponseEntity.ok(customers);
    }

    @Operation(
        summary = "Update customer policy",
        description = "Updates the policy type for ALL allowances of a customer."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Policy successfully updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request or policy type not found"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @PutMapping("/{customerId}/policy")
    public ResponseEntity<Map<String, Object>> updatePolicy(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId,
            @RequestBody UpdatePolicyRequest request) {

        if (request.policyTypeName() == null || request.policyTypeName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        PolicyTypeEntity policyType = policyTypeRepository.findByName(request.policyTypeName())
            .orElse(null);

        if (policyType == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            int updated = customerTokenService.updateAllowancesPolicyType(customerId, policyType);
            return ResponseEntity.ok(Map.of(
                "customerId", customerId,
                "allowancesUpdated", updated,
                "policyTypeName", policyType.getName(),
                "resetDays", policyType.getResetDays() != null ? policyType.getResetDays() : "UNLIMITED"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
        summary = "Update customer status",
        description = "Enables or disables a customer."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status successfully updated"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @PatchMapping("/{customerId}/status")
    public ResponseEntity<CustomerResponse> updateStatus(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId,
            @RequestBody UpdateStatusRequest request) {

        return customerRepository.findByCustomerId(customerId)
            .map(customer -> {
                customer.setEnabled(request.enabled());
                customer = customerRepository.save(customer);
                LOGGER.info("Updated customer {} status to: {}", customerId, request.enabled());
                return ResponseEntity.ok(toCustomerResponse(customer));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Get customer allowances",
        description = "Returns all model allowances for a customer."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved allowances"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @GetMapping("/{customerId}/allowances")
    public ResponseEntity<List<ModelAllowanceResponse>> getCustomerAllowances(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId) {

        if (!customerRepository.existsByCustomerId(customerId)) {
            return ResponseEntity.notFound().build();
        }

        List<CustomerModelAllowanceEntity> allowances =
            customerUsageService.getCustomerAllowancesWithPolicyType(customerId);

        List<ModelAllowanceResponse> responses = allowances.stream()
            .map(this::toModelAllowanceResponse)
            .toList();

        return ResponseEntity.ok(responses);
    }

    @Operation(
        summary = "Set model allowance",
        description = "Sets the token allowance for a customer on a specific model. Optionally sets a policy type."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Allowance successfully set"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @PutMapping("/{customerId}/allowances/{model}")
    public ResponseEntity<ModelAllowanceResponse> setAllowance(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId,
            @Parameter(description = "Model identifier") @PathVariable String model,
            @RequestBody SetAllowanceRequest request) {

        if (!customerRepository.existsByCustomerId(customerId)) {
            return ResponseEntity.notFound().build();
        }

        try {
            // Resolve policy type if specified
            PolicyTypeEntity policyType = null;
            if (request.policyTypeName() != null && !request.policyTypeName().isBlank()) {
                policyType = policyTypeRepository.findByName(request.policyTypeName())
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Policy type not found: " + request.policyTypeName()));
            }

            CustomerModelAllowanceEntity allowance;
            if (policyType != null) {
                allowance = customerUsageService.setAllowanceWithPolicyType(
                    customerId, model, request.allowedTokens(), policyType);
            } else {
                allowance = customerUsageService.setAllowance(customerId, model, request.allowedTokens());
            }

            return ResponseEntity.ok(toModelAllowanceResponse(allowance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
        summary = "Set bulk allowance",
        description = "Sets the token allowance for a customer on ALL models."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Allowances successfully set"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @PutMapping("/{customerId}/allowances")
    public ResponseEntity<Map<String, Object>> setBulkAllowance(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId,
            @RequestBody BulkSetAllowanceRequest request) {

        if (!customerRepository.existsByCustomerId(customerId)) {
            return ResponseEntity.notFound().build();
        }

        try {
            int updated;
            if (Boolean.TRUE.equals(request.unlimited())) {
                updated = customerUsageService.setUnlimitedForAllModels(customerId);
            } else {
                updated = customerUsageService.setAllowanceForAllModels(customerId, request.allowedTokens());
            }

            return ResponseEntity.ok(Map.of(
                "customerId", customerId,
                "modelsUpdated", updated,
                "allowedTokens", Boolean.TRUE.equals(request.unlimited()) ? "UNLIMITED" :
                    (request.allowedTokens() != null ? request.allowedTokens() : 0)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
        summary = "Reset model usage",
        description = "Resets the token usage counter for a customer on a specific model."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usage successfully reset"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @PostMapping("/{customerId}/allowances/{model}/reset")
    public ResponseEntity<Void> resetModelUsage(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId,
            @Parameter(description = "Model identifier") @PathVariable String model) {

        if (!customerRepository.existsByCustomerId(customerId)) {
            return ResponseEntity.notFound().build();
        }

        customerUsageService.resetUsage(customerId, model);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "Reset all usage",
        description = "Resets the token usage counters for a customer on ALL models."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usage successfully reset"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @PostMapping("/{customerId}/allowances/reset-all")
    public ResponseEntity<Void> resetAllUsage(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId) {
        if (!customerRepository.existsByCustomerId(customerId)) {
            return ResponseEntity.notFound().build();
        }

        customerUsageService.resetAllUsage(customerId);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "List policy types",
        description = "Returns all available (enabled) policy types."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved policy types")
    })
    @GetMapping("/policy-types")
    public ResponseEntity<List<PolicyTypeResponse>> listPolicyTypes() {
        List<PolicyTypeResponse> policyTypes = policyTypeRepository.findByEnabledTrue().stream()
            .map(this::toPolicyTypeResponse)
            .toList();
        return ResponseEntity.ok(policyTypes);
    }

    @Operation(
        summary = "Set bulk policy type",
        description = "Sets the policy type for ALL allowances of a customer. Optionally also sets the token allowance."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Policy type successfully set"),
        @ApiResponse(responseCode = "400", description = "Invalid request or policy type not found"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @PutMapping("/{customerId}/allowances/policy-type")
    public ResponseEntity<Map<String, Object>> setBulkPolicyType(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId,
            @RequestBody BulkPolicyTypeRequest request) {

        if (!customerRepository.existsByCustomerId(customerId)) {
            return ResponseEntity.notFound().build();
        }

        if (request.policyTypeName() == null || request.policyTypeName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            PolicyTypeEntity policyType = policyTypeRepository.findByName(request.policyTypeName())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Policy type not found: " + request.policyTypeName()));

            int updated;
            if (request.allowedTokens() != null) {
                // Set both allowance and policy type for all models
                updated = customerUsageService.setAllowanceAndPolicyTypeForAllModels(
                    customerId, request.allowedTokens(), policyType);
            } else {
                // Only update policy type for existing allowances
                updated = customerUsageService.setPolicyTypeForAllAllowances(customerId, policyType);
            }

            LOGGER.info("Set policy type {} for customer {} on {} allowances",
                       policyType.getName(), customerId, updated);

            return ResponseEntity.ok(Map.of(
                "customerId", customerId,
                "allowancesUpdated", updated,
                "policyTypeName", policyType.getName(),
                "resetDays", policyType.getResetDays() != null ? policyType.getResetDays() : "UNLIMITED",
                "allowedTokens", request.allowedTokens() != null ? request.allowedTokens() : "UNCHANGED"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
        summary = "Update notification settings",
        description = "Updates the notification settings for a customer (threshold, webhook URL, email)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Settings successfully updated"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @PatchMapping("/{customerId}/notification-settings")
    public ResponseEntity<Map<String, Object>> updateNotificationSettings(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId,
            @RequestBody UpdateNotificationSettingsRequest request) {

        Optional<CustomerEntity> customerOpt = customerRepository.findByCustomerId(customerId);
        if (customerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CustomerEntity customer = customerOpt.get();
        if (request.defaultNotificationThreshold() != null) {
            customer.setDefaultMinTokenNotificationThreshold(request.defaultNotificationThreshold());
        }
        if (request.webhookUrl() != null) {
            customer.setNotificationWebhookUrl(request.webhookUrl().isBlank() ? null : request.webhookUrl());
        }
        if (request.email() != null) {
            customer.setNotificationEmail(request.email().isBlank() ? null : request.email());
        }
        customer = customerRepository.save(customer);

        LOGGER.info("Updated notification settings for customer {}", customerId);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("customerId", customerId);
        response.put("defaultNotificationThreshold", customer.getDefaultMinTokenNotificationThreshold());
        response.put("webhookUrl", customer.getNotificationWebhookUrl() != null ? customer.getNotificationWebhookUrl() : "");
        response.put("email", customer.getNotificationEmail() != null ? customer.getNotificationEmail() : "");

        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Update allowance notification threshold",
        description = "Updates the notification threshold for a specific model allowance."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Threshold successfully updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @PatchMapping("/{customerId}/allowances/{model}/notification-threshold")
    public ResponseEntity<ModelAllowanceResponse> updateAllowanceNotificationThreshold(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId,
            @Parameter(description = "Model identifier") @PathVariable String model,
            @RequestBody UpdateAllowanceNotificationThresholdRequest request) {

        if (!customerRepository.existsByCustomerId(customerId)) {
            return ResponseEntity.notFound().build();
        }

        try {
            CustomerModelAllowanceEntity allowance =
                customerUsageService.setNotificationThreshold(customerId, model, request.threshold());
            return ResponseEntity.ok(toModelAllowanceResponse(allowance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
        summary = "Get monthly token usage",
        description = "Returns monthly-aggregated LLM token usage for a customer, broken down by model and agent type."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved token usage"),
        @ApiResponse(responseCode = "400", description = "Invalid months parameter"),
        @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    @GetMapping("/{customerId}/token-usage")
    public ResponseEntity<List<MonthlyTokenUsageResponse>> getTokenUsage(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId,
            @Parameter(description = "Number of months to look back (1-24)") @RequestParam(defaultValue = "12") int months,
            @Parameter(description = "Optional model filter") @RequestParam(required = false) String model) {

        if (months < 1 || months > 24) {
            return ResponseEntity.badRequest().build();
        }

        if (!customerRepository.existsByCustomerId(customerId)) {
            return ResponseEntity.notFound().build();
        }

        List<MonthlyTokenUsageResponse> usage = llmTokenUsageService.getMonthlyUsage(customerId, months, model);
        return ResponseEntity.ok(usage);
    }

    // ==================== Helper Methods ====================

    private CustomerResponse toCustomerResponse(CustomerEntity customer) {
        List<CustomerModelAllowanceEntity> allowances =
            customerUsageService.getCustomerAllowancesWithPolicyType(customer.getCustomerId());

        List<ModelAllowanceResponse> allowanceResponses = allowances.stream()
            .map(this::toModelAllowanceResponse)
            .toList();

        return new CustomerResponse(
            customer.getId(),
            customer.getCustomerId(),
            customer.getName(),
            customer.isEnabled(),
            null, // No customer-level policy type anymore
            null, // No customer-level reset days anymore
            false, // No customer-level unlimited flag anymore
            customer.getCreatedAt(),
            customer.getUpdatedAt(),
            allowanceResponses,
            customer.getDefaultMinTokenNotificationThreshold(),
            customer.getNotificationWebhookUrl(),
            customer.getNotificationEmail()
        );
    }

    /**
     * Converts an allowance entity to a response DTO using the allowance's own policy type.
     */
    private ModelAllowanceResponse toModelAllowanceResponse(CustomerModelAllowanceEntity allowance) {
        PolicyTypeEntity policy = allowance.getPolicyType();
        Integer daysUntilReset = calculateDaysUntilReset(allowance, policy);

        return new ModelAllowanceResponse(
            allowance.getId(),
            allowance.getLlmModel().getModel(),
            allowance.getLlmModel().getDisplayName(),
            allowance.getLlmModel().getProvider(),
            allowance.getAllowedTokens(),
            allowance.getTokensUsed(),
            allowance.getRemainingTokens(),
            allowance.isUnlimited(),
            daysUntilReset,
            allowance.getTokensResetAt(),
            policy != null ? policy.getName() : null,
            policy != null ? policy.getResetDays() : null,
            allowance.getMinTokenNotificationThreshold(),
            allowance.getEffectiveNotificationThreshold(),
            allowance.isBelowNotificationThreshold(),
            allowance.getLowTokenNotificationSentAt()
        );
    }

    private Integer calculateDaysUntilReset(CustomerModelAllowanceEntity allowance, PolicyTypeEntity policy) {
        if (policy == null || policy.isUnlimited()) {
            return null;
        }

        Integer resetDays = policy.getResetDays();
        if (resetDays == null || allowance.getTokensResetAt() == null) {
            return null;
        }

        LocalDateTime nextReset = allowance.getTokensResetAt().plusDays(resetDays);
        long daysUntil = ChronoUnit.DAYS.between(LocalDateTime.now(), nextReset);
        return Math.max(0, (int) daysUntil);
    }

    private PolicyTypeResponse toPolicyTypeResponse(PolicyTypeEntity policyType) {
        return new PolicyTypeResponse(
            policyType.getId(),
            policyType.getName(),
            policyType.getDescription(),
            policyType.getResetDays(),
            null, // defaultTokens removed from policy
            policyType.isEnabled()
        );
    }
}
