package com.davidparry.agent.controller;

import com.davidparry.agent.dto.AdminCustomerSummaryResponse;
import com.davidparry.agent.dto.AgentConfigResponse;
import com.davidparry.agent.dto.AgentTypeCustomersResponse;
import com.davidparry.agent.dto.AgentTypeStatisticsResponse;
import com.davidparry.agent.dto.AllowancesByModelResponse;
import com.davidparry.agent.dto.AssignAgentTypeRequest;
import com.davidparry.agent.dto.AuditCleanupRequest;
import com.davidparry.agent.dto.AuditCleanupResponse;
import com.davidparry.agent.dto.AuditLogResponse;
import com.davidparry.agent.dto.AuditStatisticsResponse;
import com.davidparry.agent.dto.BulkAllowanceUpdateRequest;
import com.davidparry.agent.dto.BulkAssignAgentTypesRequest;
import com.davidparry.agent.dto.BulkUpdateResponse;
import com.davidparry.agent.dto.CreatePolicyTypeRequest;
import com.davidparry.agent.dto.CustomerAgentTypeResponse;
import com.davidparry.agent.dto.CustomerAgentTypesResponse;
import com.davidparry.agent.dto.CustomerAllowanceSummary;
import com.davidparry.agent.dto.CustomerConnectionStatusResponse;
import com.davidparry.agent.dto.CustomerDisableResponse;
import com.davidparry.agent.dto.CustomerResponse;
import com.davidparry.agent.dto.ModelAllowanceResponse;
import com.davidparry.agent.dto.ModelResponse;
import com.davidparry.agent.dto.PolicyTypeResponse;
import com.davidparry.agent.dto.SystemStatisticsResponse;
import com.davidparry.agent.dto.TokenStatisticsResponse;
import com.davidparry.agent.dto.UpdateAgentConfigRequest;
import com.davidparry.agent.dto.UpdateCustomerAgentTypeRequest;
import com.davidparry.agent.dto.UpdateModelRequest;
import com.davidparry.agent.dto.UpdatePolicyTypeRequest;
import com.davidparry.agent.dto.UsageResetResponse;
import com.davidparry.agent.entity.AgentConfigEntity;
import com.davidparry.agent.entity.AgentExecutionConfigEntity;
import com.davidparry.agent.entity.CustomerAgentTypeEntity;
import com.davidparry.agent.entity.CustomerEntity;
import com.davidparry.agent.entity.CustomerModelAllowanceEntity;
import com.davidparry.agent.entity.LlmModelEntity;
import com.davidparry.agent.entity.PolicyTypeEntity;
import com.davidparry.agent.entity.SecurityAuditLogEntity;
import com.davidparry.agent.protocol.dto.AgentType;
import com.davidparry.agent.repository.AgentConfigRepository;
import com.davidparry.agent.repository.CustomerAgentTypeRepository;
import com.davidparry.agent.repository.CustomerModelAllowanceRepository;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.repository.CustomerTokenRepository;
import com.davidparry.agent.repository.LlmModelRepository;
import com.davidparry.agent.repository.PolicyTypeRepository;
import com.davidparry.agent.repository.SecurityAuditLogRepository;
import com.davidparry.agent.service.AdminService;
import com.davidparry.agent.service.CustomerUsageService;
import com.davidparry.agent.service.LlmModelService;
import com.davidparry.agent.service.SecurityAuditService;
import com.davidparry.agent.session.ClientConnection;
import com.davidparry.agent.session.ConnectionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST API for administrative operations.
 * Requires admin token authentication via Authorization header (Bearer token).
 * All actions are logged to the security audit trail.
 *
 * Authentication is handled by AdminAuthenticationFilter.
 */
@RestController
@RequestMapping("/api/admin")
@SecurityRequirement(name = "AdminBearerAuth")
public class AdminController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminController.class);

    private final AdminService adminService;
    private final CustomerRepository customerRepository;
    private final CustomerTokenRepository tokenRepository;
    private final LlmModelRepository modelRepository;
    private final LlmModelService llmModelService;
    private final PolicyTypeRepository policyTypeRepository;
    private final CustomerModelAllowanceRepository allowanceRepository;
    private final SecurityAuditLogRepository auditLogRepository;
    private final SecurityAuditService auditService;
    private final CustomerUsageService customerUsageService;
    private final ConnectionManager connectionManager;
    private final AgentConfigRepository agentConfigRepository;
    private final CustomerAgentTypeRepository customerAgentTypeRepository;

    public AdminController(
            AdminService adminService,
            CustomerRepository customerRepository,
            CustomerTokenRepository tokenRepository,
            LlmModelRepository modelRepository,
            LlmModelService llmModelService,
            PolicyTypeRepository policyTypeRepository,
            CustomerModelAllowanceRepository allowanceRepository,
            SecurityAuditLogRepository auditLogRepository,
            SecurityAuditService auditService,
            CustomerUsageService customerUsageService,
            ConnectionManager connectionManager,
            AgentConfigRepository agentConfigRepository,
            CustomerAgentTypeRepository customerAgentTypeRepository) {
        this.adminService = adminService;
        this.customerRepository = customerRepository;
        this.tokenRepository = tokenRepository;
        this.modelRepository = modelRepository;
        this.llmModelService = llmModelService;
        this.policyTypeRepository = policyTypeRepository;
        this.allowanceRepository = allowanceRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.customerUsageService = customerUsageService;
        this.connectionManager = connectionManager;
        this.agentConfigRepository = agentConfigRepository;
        this.customerAgentTypeRepository = customerAgentTypeRepository;
    }

    // ==================== Customer Management ====================

    @Operation(
        summary = "List all customers",
        description = "Returns a paginated list of all customers with summary information including active token count and model allowance count."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved customer list"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Customers")
    @GetMapping("/customers")
    public ResponseEntity<Page<AdminCustomerSummaryResponse>> listCustomers(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Filter by enabled status") @RequestParam(required = false) Boolean enabled) {

        Pageable pageable = PageRequest.of(page, size);
        Page<CustomerEntity> customers;

        if (enabled != null) {
            customers = customerRepository.findByEnabled(enabled, pageable);
        } else {
            customers = customerRepository.findAll(pageable);
        }

        Page<AdminCustomerSummaryResponse> response = customers.map(this::toAdminCustomerSummary);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Get customer details",
        description = "Returns detailed information about a specific customer including all model allowances."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved customer"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Customers")
    @GetMapping("/customers/{customerId}")
    public ResponseEntity<CustomerResponse> getCustomer(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId) {
        return customerRepository.findByCustomerId(customerId)
            .map(this::toCustomerResponse)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Disable customer",
        description = "Disables a customer (soft delete) with cascading effects: revokes all tokens and disables all model allowances."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Customer successfully disabled"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Customers")
    @DeleteMapping("/customers/{customerId}")
    public ResponseEntity<CustomerDisableResponse> disableCustomer(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId) {
        try {
            AdminService.CustomerDisableResult result = adminService.disableCustomer(customerId);
            return ResponseEntity.ok(new CustomerDisableResponse(
                result.customerId(),
                true,
                result.tokensRevoked(),
                result.allowancesDisabled()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
        summary = "Enable customer",
        description = "Enables a previously disabled customer. Note: This does NOT re-enable allowances - use enable-allowances endpoint separately."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Customer successfully enabled"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Customers")
    @PostMapping("/customers/{customerId}/enable")
    public ResponseEntity<Map<String, Object>> enableCustomer(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId) {
        try {
            adminService.enableCustomer(customerId);
            return ResponseEntity.ok(Map.of(
                "customerId", customerId,
                "enabled", true
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
        summary = "Enable customer allowances",
        description = "Re-enables all model allowances for a customer that were disabled during customer disable."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Allowances successfully enabled"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Customers")
    @PostMapping("/customers/{customerId}/enable-allowances")
    public ResponseEntity<Map<String, Object>> enableCustomerAllowances(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId) {
        try {
            int enabled = adminService.enableCustomerAllowances(customerId);
            return ResponseEntity.ok(Map.of(
                "customerId", customerId,
                "allowancesEnabled", enabled
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
        summary = "Get token statistics",
        description = "Returns statistics about active tokens grouped by secret version. Useful for monitoring token rotation."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved token statistics"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Customers")
    @GetMapping("/customers/tokens/stats")
    public ResponseEntity<List<TokenStatisticsResponse>> getTokenStatistics() {
        List<AdminService.TokenVersionStatistics> stats = adminService.getTokenStatistics();
        List<TokenStatisticsResponse> response = stats.stream()
            .map(s -> new TokenStatisticsResponse(s.secretVersion(), s.activeTokenCount()))
            .toList();
        return ResponseEntity.ok(response);
    }

    // ==================== Model Management ====================

    @Operation(
        summary = "List all models",
        description = "Returns all LLM models including disabled ones."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved model list"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Models")
    @GetMapping("/models")
    public ResponseEntity<List<ModelResponse>> listAllModels() {
        List<ModelResponse> models = modelRepository.findAll().stream()
            .map(this::toModelResponse)
            .toList();
        return ResponseEntity.ok(models);
    }

    @Operation(
        summary = "Update model",
        description = "Updates a model's display name, description, default tokens for new customers, token pricing, or enabled status."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Model successfully updated"),
        @ApiResponse(responseCode = "404", description = "Model not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Models")
    @PutMapping("/models/{model}")
    @Transactional
    public ResponseEntity<ModelResponse> updateModel(
            @Parameter(description = "Model identifier (e.g., claude-sonnet-4-5)") @PathVariable String model,
            @RequestBody UpdateModelRequest request) {

        return modelRepository.findById(model)
            .map(llmModel -> {
                if (request.displayName() != null) {
                    llmModel.setDisplayName(request.displayName());
                }
                if (request.description() != null) {
                    llmModel.setDescription(request.description());
                }
                if (request.defaultTokensForNewCustomers() != null) {
                    llmModel.setDefaultTokensForNewCustomers(request.defaultTokensForNewCustomers());
                }
                if (request.enabled() != null) {
                    llmModel.setEnabled(request.enabled());
                }
                if (request.inputTokenPricePerMillion() != null) {
                    llmModel.setInputTokenPricePerMillion(request.inputTokenPricePerMillion());
                }
                if (request.outputTokenPricePerMillion() != null) {
                    llmModel.setOutputTokenPricePerMillion(request.outputTokenPricePerMillion());
                }

                llmModel = modelRepository.save(llmModel);

                auditService.logAdminModelAction(
                    SecurityAuditLogEntity.EventType.ADMIN_MODEL_UPDATED,
                    model,
                    "Model updated"
                );

                return ResponseEntity.ok(toModelResponse(llmModel));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Disable model",
        description = "Disables a model (soft delete). Disabled models cannot be used for new requests."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Model successfully disabled"),
        @ApiResponse(responseCode = "404", description = "Model not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Models")
    @DeleteMapping("/models/{model}")
    @Transactional
    public ResponseEntity<ModelResponse> disableModel(
            @Parameter(description = "Model identifier") @PathVariable String model) {
        return modelRepository.findById(model)
            .map(llmModel -> {
                llmModel.setEnabled(false);
                llmModel = modelRepository.save(llmModel);

                auditService.logAdminModelAction(
                    SecurityAuditLogEntity.EventType.ADMIN_MODEL_DISABLED,
                    model,
                    "Model disabled"
                );

                return ResponseEntity.ok(toModelResponse(llmModel));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Enable model",
        description = "Enables a previously disabled model."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Model successfully enabled"),
        @ApiResponse(responseCode = "404", description = "Model not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Models")
    @PostMapping("/models/{model}/enable")
    @Transactional
    public ResponseEntity<ModelResponse> enableModel(
            @Parameter(description = "Model identifier") @PathVariable String model) {
        return modelRepository.findById(model)
            .map(llmModel -> {
                llmModel.setEnabled(true);
                llmModel = modelRepository.save(llmModel);

                auditService.logAdminModelAction(
                    SecurityAuditLogEntity.EventType.ADMIN_MODEL_ENABLED,
                    model,
                    "Model enabled"
                );

                return ResponseEntity.ok(toModelResponse(llmModel));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Policy Type Management ====================

    @Operation(
        summary = "List all policy types",
        description = "Returns all policy types including disabled ones. Policy types define token reset periods."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved policy type list"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Policy Types")
    @GetMapping("/policy-types")
    public ResponseEntity<List<PolicyTypeResponse>> listAllPolicyTypes() {
        List<PolicyTypeResponse> policyTypes = policyTypeRepository.findAll().stream()
            .map(this::toPolicyTypeResponse)
            .toList();
        return ResponseEntity.ok(policyTypes);
    }

    @Operation(
        summary = "Create policy type",
        description = "Creates a new policy type. Reset days of null means unlimited (no reset)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Policy type successfully created"),
        @ApiResponse(responseCode = "400", description = "Invalid request - name is required"),
        @ApiResponse(responseCode = "409", description = "Conflict - policy type with this name already exists"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Policy Types")
    @PostMapping("/policy-types")
    @Transactional
    public ResponseEntity<PolicyTypeResponse> createPolicyType(
            @RequestBody CreatePolicyTypeRequest request) {

        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (policyTypeRepository.existsByName(request.name())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        PolicyTypeEntity policyType = new PolicyTypeEntity();
        policyType.setName(request.name());
        policyType.setDescription(request.description());
        policyType.setResetDays(request.resetDays());
        policyType.setEnabled(true);

        policyType = policyTypeRepository.save(policyType);

        auditService.logAdminAction(
            SecurityAuditLogEntity.EventType.ADMIN_POLICY_TYPE_CREATED,
            "Policy type created: " + request.name(),
            null
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toPolicyTypeResponse(policyType));
    }

    @Operation(
        summary = "Update policy type",
        description = "Updates a policy type's description, reset days, or enabled status."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Policy type successfully updated"),
        @ApiResponse(responseCode = "404", description = "Policy type not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Policy Types")
    @PutMapping("/policy-types/{id}")
    @Transactional
    public ResponseEntity<PolicyTypeResponse> updatePolicyType(
            @Parameter(description = "Policy type UUID") @PathVariable UUID id,
            @RequestBody UpdatePolicyTypeRequest request) {

        return policyTypeRepository.findById(id)
            .map(policyType -> {
                if (request.description() != null) {
                    policyType.setDescription(request.description());
                }
                if (request.resetDays() != null) {
                    policyType.setResetDays(request.resetDays());
                }
                if (request.enabled() != null) {
                    policyType.setEnabled(request.enabled());
                }

                policyType = policyTypeRepository.save(policyType);

                auditService.logAdminAction(
                    SecurityAuditLogEntity.EventType.ADMIN_POLICY_TYPE_UPDATED,
                    "Policy type updated: " + policyType.getName(),
                    null
                );

                return ResponseEntity.ok(toPolicyTypeResponse(policyType));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Disable policy type",
        description = "Disables a policy type (soft delete). Disabled policy types cannot be assigned to new allowances."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Policy type successfully disabled"),
        @ApiResponse(responseCode = "404", description = "Policy type not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Policy Types")
    @DeleteMapping("/policy-types/{id}")
    @Transactional
    public ResponseEntity<PolicyTypeResponse> disablePolicyType(
            @Parameter(description = "Policy type UUID") @PathVariable UUID id) {
        return policyTypeRepository.findById(id)
            .map(policyType -> {
                policyType.setEnabled(false);
                policyType = policyTypeRepository.save(policyType);

                auditService.logAdminAction(
                    SecurityAuditLogEntity.EventType.ADMIN_POLICY_TYPE_DISABLED,
                    "Policy type disabled: " + policyType.getName(),
                    null
                );

                return ResponseEntity.ok(toPolicyTypeResponse(policyType));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Allowance Management ====================

    @Operation(
        summary = "Get allowances by model",
        description = "Returns all customer allowances for a specific model with customer information."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved allowances"),
        @ApiResponse(responseCode = "404", description = "Model not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Allowances")
    @GetMapping("/allowances/by-model/{model}")
    public ResponseEntity<AllowancesByModelResponse> getAllowancesByModel(
            @Parameter(description = "Model identifier") @PathVariable String model) {
        LlmModelEntity llmModel = modelRepository.findById(model).orElse(null);
        if (llmModel == null) {
            return ResponseEntity.notFound().build();
        }

        List<CustomerModelAllowanceEntity> allowances =
            allowanceRepository.findAllByModelWithCustomer(model);

        List<CustomerAllowanceSummary> summaries = allowances.stream()
            .map(this::toCustomerAllowanceSummary)
            .toList();

        return ResponseEntity.ok(new AllowancesByModelResponse(
            model,
            llmModel.getDisplayName(),
            summaries.size(),
            summaries
        ));
    }

    @Operation(
        summary = "Bulk update allowances",
        description = "Updates token allowances for all customers on a specific model. Optionally sets a policy type."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Allowances successfully updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request - model is required or policy type not found"),
        @ApiResponse(responseCode = "404", description = "Model not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Allowances")
    @PutMapping("/allowances/bulk")
    @Transactional
    public ResponseEntity<BulkUpdateResponse> bulkUpdateAllowances(
            @RequestBody BulkAllowanceUpdateRequest request) {

        if (request.model() == null || request.model().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (!modelRepository.existsById(request.model())) {
            return ResponseEntity.notFound().build();
        }

        int updated;
        PolicyTypeEntity policyType = null;

        if (request.policyTypeName() != null && !request.policyTypeName().isBlank()) {
            policyType = policyTypeRepository.findByName(request.policyTypeName()).orElse(null);
            if (policyType == null) {
                return ResponseEntity.badRequest().build();
            }
            updated = allowanceRepository.bulkUpdateAllowanceAndPolicyForModel(
                request.model(),
                request.allowedTokens(),
                policyType,
                LocalDateTime.now()
            );
        } else {
            updated = allowanceRepository.bulkUpdateAllowanceForModel(
                request.model(),
                request.allowedTokens(),
                LocalDateTime.now()
            );
        }

        auditService.logAdminAction(
            SecurityAuditLogEntity.EventType.ADMIN_ALLOWANCE_BULK_UPDATED,
            String.format("Bulk updated %d allowances for model %s to %s tokens",
                         updated, request.model(),
                         request.allowedTokens() == null ? "UNLIMITED" : request.allowedTokens()),
            null
        );

        return ResponseEntity.ok(new BulkUpdateResponse(
            request.model(),
            updated,
            request.allowedTokens(),
            policyType != null ? policyType.getName() : null
        ));
    }

    @Operation(
        summary = "Reset usage for model",
        description = "Resets token usage counters for all customers on a specific model."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usage successfully reset"),
        @ApiResponse(responseCode = "404", description = "Model not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Allowances")
    @PostMapping("/allowances/reset-all/{model}")
    @Transactional
    public ResponseEntity<UsageResetResponse> resetAllUsageForModel(
            @Parameter(description = "Model identifier") @PathVariable String model) {
        if (!modelRepository.existsById(model)) {
            return ResponseEntity.notFound().build();
        }

        int reset = allowanceRepository.resetUsageForModel(model, LocalDateTime.now());

        auditService.logAdminAction(
            SecurityAuditLogEntity.EventType.ADMIN_USAGE_RESET,
            String.format("Reset usage for %d allowances on model %s", reset, model),
            null
        );

        return ResponseEntity.ok(new UsageResetResponse(model, reset));
    }

    // ==================== Audit Log Management ====================

    @Operation(
        summary = "Query audit logs",
        description = "Returns paginated audit logs with optional filters for customer, event type, category, and date range."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved audit logs"),
        @ApiResponse(responseCode = "400", description = "Invalid event type or category"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Audit")
    @GetMapping("/audit")
    public ResponseEntity<Page<AuditLogResponse>> queryAuditLogs(
            @Parameter(description = "Filter by customer UUID") @RequestParam(required = false) UUID customerId,
            @Parameter(description = "Filter by event type (e.g., TOKEN_CREATED, ADMIN_CUSTOMER_DISABLED)")
                @RequestParam(required = false) String eventType,
            @Parameter(description = "Filter by event category (e.g., TOKEN, ADMIN, AUTHENTICATION)")
                @RequestParam(required = false) String eventCategory,
            @Parameter(description = "Filter events from this date/time") @RequestParam(required = false) LocalDateTime from,
            @Parameter(description = "Filter events until this date/time") @RequestParam(required = false) LocalDateTime to,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size) {

        SecurityAuditLogEntity.EventType eventTypeEnum = null;
        if (eventType != null) {
            try {
                eventTypeEnum = SecurityAuditLogEntity.EventType.valueOf(eventType);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        SecurityAuditLogEntity.EventCategory categoryEnum = null;
        if (eventCategory != null) {
            try {
                categoryEnum = SecurityAuditLogEntity.EventCategory.valueOf(eventCategory);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<SecurityAuditLogEntity> logs = auditLogRepository.findByFilters(
            customerId, eventTypeEnum, categoryEnum, from, to, pageable);

        Page<AuditLogResponse> response = logs.map(this::toAuditLogResponse);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Get audit statistics",
        description = "Returns event counts grouped by type for a specified time period (default: last 24 hours)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved audit statistics"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Audit")
    @GetMapping("/audit/stats")
    public ResponseEntity<AuditStatisticsResponse> getAuditStatistics(
            @Parameter(description = "Count events since this date/time (default: 24 hours ago)")
                @RequestParam(required = false) LocalDateTime since) {

        LocalDateTime effectiveSince = since != null ? since : LocalDateTime.now().minusDays(1);

        List<Object[]> counts = auditLogRepository.countEventsByTypeSince(effectiveSince);
        Map<String, Long> eventCounts = new HashMap<>();
        long total = 0;

        for (Object[] row : counts) {
            String type = ((SecurityAuditLogEntity.EventType) row[0]).name();
            Long count = ((Number) row[1]).longValue();
            eventCounts.put(type, count);
            total += count;
        }

        return ResponseEntity.ok(new AuditStatisticsResponse(eventCounts, total, effectiveSince));
    }

    @Operation(
        summary = "Cleanup audit logs",
        description = "Deletes audit logs older than the specified date. Use with caution - this is irreversible."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Audit logs successfully cleaned up"),
        @ApiResponse(responseCode = "400", description = "Invalid request - before date is required"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Audit")
    @DeleteMapping("/audit/cleanup")
    @Transactional
    public ResponseEntity<AuditCleanupResponse> cleanupAuditLogs(
            @RequestBody AuditCleanupRequest request) {

        if (request.before() == null) {
            return ResponseEntity.badRequest().build();
        }

        int deleted = auditLogRepository.deleteByCreatedAtBefore(request.before());

        auditService.logAdminAction(
            SecurityAuditLogEntity.EventType.ADMIN_AUDIT_CLEANUP,
            String.format("Deleted %d audit logs before %s", deleted, request.before()),
            null
        );

        return ResponseEntity.ok(new AuditCleanupResponse(deleted, request.before()));
    }

    // ==================== System Statistics ====================

    @Operation(
        summary = "Get system statistics",
        description = "Returns overall system statistics including counts of customers, models, policy types, allowances, and audit logs."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved system statistics"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Statistics")
    @GetMapping("/stats")
    public ResponseEntity<SystemStatisticsResponse> getSystemStatistics() {
        AdminService.SystemStatistics stats = adminService.getSystemStatistics();
        return ResponseEntity.ok(new SystemStatisticsResponse(
            stats.totalCustomers(),
            stats.enabledCustomers(),
            stats.totalModels(),
            stats.enabledModels(),
            stats.totalPolicyTypes(),
            stats.totalAllowances(),
            stats.totalAuditLogs()
        ));
    }

    @Operation(
        summary = "Get customer connection status",
        description = "Returns whether a customer currently has an active WebSocket connection, "
                      + "along with details about all active connections and sessions. "
                      + "This is a real-time check against the in-memory connection manager."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved connection status"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Statistics")
    @GetMapping("/stats/customer/{customerId}/connection")
    public ResponseEntity<CustomerConnectionStatusResponse> getCustomerConnectionStatus(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId) {

        // Verify customer exists
        Optional<CustomerEntity> customerOpt = customerRepository.findByCustomerId(customerId);
        if (customerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CustomerEntity customer = customerOpt.get();
        String customerIdStr = customerId.toString();

        // Get all connections for this customer
        Collection<ClientConnection> connections = connectionManager.getConnectionsByCustomerId(customerIdStr);

        // Build connection details for active connections only
        List<CustomerConnectionStatusResponse.ConnectionDetail> connectionDetails = connections.stream()
                .filter(ClientConnection::isActive)
                .map(conn -> new CustomerConnectionStatusResponse.ConnectionDetail(
                    conn.getConnectionId(),
                    conn.getConnectedAt(),
                    conn.getLastActivityAt(),
                    conn.getActiveSessionCount(),
                    conn.getTotalSessionsCreated(),
                    conn.getTotalToolCalls(),
                    conn.getDurationMs(),
                    conn.getIdleTimeMs()
                ))
                .toList();

        int totalSessions = connectionDetails.stream()
                .mapToInt(CustomerConnectionStatusResponse.ConnectionDetail::activeSessions)
                .sum();

        return ResponseEntity.ok(new CustomerConnectionStatusResponse(
            customerId,
            customer.getName(),
            !connectionDetails.isEmpty(),
            connectionDetails.size(),
            totalSessions,
            connectionDetails
        ));
    }

    // ==================== Agent Type Management ====================

    @Operation(
        summary = "List all agent types",
        description = "Returns all agent type configurations including disabled ones."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved agent type list"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Agent Types")
    @GetMapping("/agent-types")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AgentConfigResponse>> listAllAgentTypes() {
        List<AgentConfigResponse> agentTypes = agentConfigRepository.findAll().stream()
            .map(this::toAgentConfigResponse)
            .toList();
        return ResponseEntity.ok(agentTypes);
    }

    @Operation(
        summary = "Get agent type details",
        description = "Returns detailed information about a specific agent type configuration."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved agent type"),
        @ApiResponse(responseCode = "400", description = "Invalid agent type"),
        @ApiResponse(responseCode = "404", description = "Agent type not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Agent Types")
    @GetMapping("/agent-types/{agentType}")
    @Transactional(readOnly = true)
    public ResponseEntity<AgentConfigResponse> getAgentType(
            @Parameter(description = "Agent type (e.g., ANALYST, ENGINEER, REVIEWER, DIAGNOSTICIAN)")
            @PathVariable String agentType) {
        try {
            AgentType type = AgentType.valueOf(agentType.toUpperCase());
            return agentConfigRepository.findByAgentType(type)
                .map(this::toAgentConfigResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(
        summary = "Update agent type configuration",
        description = "Updates an agent type's name, description, system prompt, model, or execution settings."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Agent type successfully updated"),
        @ApiResponse(responseCode = "400", description = "Invalid agent type or model not found"),
        @ApiResponse(responseCode = "404", description = "Agent type not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Agent Types")
    @PutMapping("/agent-types/{agentType}")
    @Transactional
    public ResponseEntity<AgentConfigResponse> updateAgentType(
            @Parameter(description = "Agent type") @PathVariable String agentType,
            @RequestBody UpdateAgentConfigRequest request) {

        AgentType type;
        try {
            type = AgentType.valueOf(agentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        Optional<AgentConfigEntity> configOpt = agentConfigRepository.findByAgentType(type);
        if (configOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AgentConfigEntity config = configOpt.get();

        if (request.name() != null) {
            config.setName(request.name());
        }
        if (request.description() != null) {
            config.setDescription(request.description());
        }
        if (request.systemPrompt() != null) {
            config.setSystemPrompt(request.systemPrompt());
        }
        if (request.model() != null) {
            LlmModelEntity model = modelRepository.findById(request.model()).orElse(null);
            if (model == null) {
                return ResponseEntity.badRequest().build();
            }
            config.setLlmModel(model);
        }
        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }

        // Update default execution config if provided
        AgentExecutionConfigEntity execConfig = config.getDefaultExecutionConfig();
        if (execConfig != null) {
            if (request.maxTokens() != null) {
                execConfig.setMaxTokens(request.maxTokens());
            }
            if (request.temperature() != null) {
                execConfig.setTemperature(request.temperature());
            }
            if (request.timeoutSeconds() != null) {
                execConfig.setTimeoutSeconds(request.timeoutSeconds());
            }
            if (request.retryAttempts() != null) {
                execConfig.setRetryAttempts(request.retryAttempts());
            }
            if (request.retryDelayMs() != null) {
                execConfig.setRetryDelayMs(request.retryDelayMs());
            }
        }

        config.setVersion(config.getVersion() + 1);
        config = agentConfigRepository.save(config);

        auditService.logAdminAction(
            SecurityAuditLogEntity.EventType.ADMIN_AGENT_CONFIG_UPDATED,
            "Agent config updated: " + agentType,
            null
        );

        return ResponseEntity.ok(toAgentConfigResponse(config));
    }

    @Operation(
        summary = "Disable agent type",
        description = "Disables an agent type. Disabled agent types cannot be used for new requests."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Agent type successfully disabled"),
        @ApiResponse(responseCode = "400", description = "Invalid agent type"),
        @ApiResponse(responseCode = "404", description = "Agent type not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Agent Types")
    @DeleteMapping("/agent-types/{agentType}")
    @Transactional
    public ResponseEntity<AgentConfigResponse> disableAgentType(
            @Parameter(description = "Agent type") @PathVariable String agentType) {

        AgentType type;
        try {
            type = AgentType.valueOf(agentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        return agentConfigRepository.findByAgentType(type)
            .map(config -> {
                config.setEnabled(false);
                config = agentConfigRepository.save(config);

                auditService.logAdminAction(
                    SecurityAuditLogEntity.EventType.ADMIN_AGENT_CONFIG_DISABLED,
                    "Agent config disabled: " + agentType,
                    null
                );

                return ResponseEntity.ok(toAgentConfigResponse(config));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Enable agent type",
        description = "Enables a previously disabled agent type."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Agent type successfully enabled"),
        @ApiResponse(responseCode = "400", description = "Invalid agent type"),
        @ApiResponse(responseCode = "404", description = "Agent type not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Agent Types")
    @PostMapping("/agent-types/{agentType}/enable")
    @Transactional
    public ResponseEntity<AgentConfigResponse> enableAgentType(
            @Parameter(description = "Agent type") @PathVariable String agentType) {

        AgentType type;
        try {
            type = AgentType.valueOf(agentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        return agentConfigRepository.findByAgentType(type)
            .map(config -> {
                config.setEnabled(true);
                config = agentConfigRepository.save(config);

                auditService.logAdminAction(
                    SecurityAuditLogEntity.EventType.ADMIN_AGENT_CONFIG_ENABLED,
                    "Agent config enabled: " + agentType,
                    null
                );

                return ResponseEntity.ok(toAgentConfigResponse(config));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Customer Agent Type Assignments ====================

    @Operation(
        summary = "Get customer's agent types",
        description = "Returns all agent types assigned to a specific customer."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved customer's agent types"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Customer Agent Types")
    @GetMapping("/customers/{customerId}/agent-types")
    public ResponseEntity<CustomerAgentTypesResponse> getCustomerAgentTypes(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId) {

        Optional<CustomerEntity> customerOpt = customerRepository.findByCustomerId(customerId);
        if (customerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CustomerEntity customer = customerOpt.get();
        List<CustomerAgentTypeEntity> assignments =
            customerAgentTypeRepository.findByCustomerCustomerId(customerId);

        List<CustomerAgentTypeResponse> responses = assignments.stream()
            .map(this::toCustomerAgentTypeResponse)
            .toList();

        int enabledCount = (int) assignments.stream().filter(CustomerAgentTypeEntity::isEnabled).count();

        return ResponseEntity.ok(new CustomerAgentTypesResponse(
            customerId,
            customer.getName(),
            responses.size(),
            enabledCount,
            responses
        ));
    }

    @Operation(
        summary = "Assign agent type to customer",
        description = "Assigns an agent type to a customer, optionally with custom token limit and priority."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Agent type successfully assigned"),
        @ApiResponse(responseCode = "400", description = "Invalid agent type"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "409", description = "Agent type already assigned to customer"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Customer Agent Types")
    @PostMapping("/customers/{customerId}/agent-types")
    @Transactional
    public ResponseEntity<CustomerAgentTypeResponse> assignAgentTypeToCustomer(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId,
            @Valid @RequestBody AssignAgentTypeRequest request) {

        Optional<CustomerEntity> customerOpt = customerRepository.findByCustomerId(customerId);
        if (customerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AgentType agentType;
        try {
            agentType = AgentType.valueOf(request.agentType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        // Check if already assigned
        if (customerAgentTypeRepository.existsByCustomerCustomerIdAndAgentType(customerId, agentType)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        CustomerEntity customer = customerOpt.get();

        CustomerAgentTypeEntity assignment = new CustomerAgentTypeEntity();
        assignment.setCustomer(customer);
        assignment.setAgentType(agentType);
        assignment.setEnabled(true);
        assignment.setCustomTokenLimit(request.customTokenLimit());
        assignment.setPriority(request.priority() != null ? request.priority() : 0);

        assignment = customerAgentTypeRepository.save(assignment);

        auditService.logAdminCustomerAction(
            SecurityAuditLogEntity.EventType.ADMIN_CUSTOMER_AGENT_TYPE_ASSIGNED,
            customerId,
            "Agent type assigned: " + agentType
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toCustomerAgentTypeResponse(assignment));
    }

    @Operation(
        summary = "Update customer's agent type assignment",
        description = "Updates the enabled status, custom token limit, or priority for a customer's agent type assignment."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Assignment successfully updated"),
        @ApiResponse(responseCode = "400", description = "Invalid agent type"),
        @ApiResponse(responseCode = "404", description = "Customer or assignment not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Customer Agent Types")
    @PutMapping("/customers/{customerId}/agent-types/{agentType}")
    @Transactional
    public ResponseEntity<CustomerAgentTypeResponse> updateCustomerAgentType(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId,
            @Parameter(description = "Agent type") @PathVariable String agentType,
            @RequestBody UpdateCustomerAgentTypeRequest request) {

        AgentType type;
        try {
            type = AgentType.valueOf(agentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        return customerAgentTypeRepository.findByCustomerCustomerIdAndAgentType(customerId, type)
            .map(assignment -> {
                if (request.enabled() != null) {
                    assignment.setEnabled(request.enabled());
                }
                if (request.customTokenLimit() != null) {
                    assignment.setCustomTokenLimit(request.customTokenLimit());
                }
                if (request.priority() != null) {
                    assignment.setPriority(request.priority());
                }

                assignment = customerAgentTypeRepository.save(assignment);

                auditService.logAdminCustomerAction(
                    SecurityAuditLogEntity.EventType.ADMIN_CUSTOMER_AGENT_TYPE_UPDATED,
                    customerId,
                    "Agent type assignment updated: " + agentType
                );

                return ResponseEntity.ok(toCustomerAgentTypeResponse(assignment));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Remove agent type from customer",
        description = "Removes an agent type assignment from a customer."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Agent type successfully removed"),
        @ApiResponse(responseCode = "400", description = "Invalid agent type"),
        @ApiResponse(responseCode = "404", description = "Customer or assignment not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Customer Agent Types")
    @DeleteMapping("/customers/{customerId}/agent-types/{agentType}")
    @Transactional
    public ResponseEntity<Void> removeAgentTypeFromCustomer(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId,
            @Parameter(description = "Agent type") @PathVariable String agentType) {

        AgentType type;
        try {
            type = AgentType.valueOf(agentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        int deleted = customerAgentTypeRepository.deleteByCustomerCustomerIdAndAgentType(customerId, type);

        if (deleted == 0) {
            return ResponseEntity.notFound().build();
        }

        auditService.logAdminCustomerAction(
            SecurityAuditLogEntity.EventType.ADMIN_CUSTOMER_AGENT_TYPE_REMOVED,
            customerId,
            "Agent type removed: " + agentType
        );

        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Bulk assign agent types to customer",
        description = "Assigns multiple agent types to a customer at once. Skips already assigned types."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Agent types successfully assigned"),
        @ApiResponse(responseCode = "400", description = "Invalid agent type in list"),
        @ApiResponse(responseCode = "404", description = "Customer not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Customer Agent Types")
    @PostMapping("/customers/{customerId}/agent-types/bulk")
    @Transactional
    public ResponseEntity<CustomerAgentTypesResponse> bulkAssignAgentTypes(
            @Parameter(description = "Customer's external UUID") @PathVariable UUID customerId,
            @Valid @RequestBody BulkAssignAgentTypesRequest request) {

        Optional<CustomerEntity> customerOpt = customerRepository.findByCustomerId(customerId);
        if (customerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CustomerEntity customer = customerOpt.get();
        List<CustomerAgentTypeEntity> newAssignments = new ArrayList<>();

        for (String agentTypeStr : request.agentTypes()) {
            AgentType agentType;
            try {
                agentType = AgentType.valueOf(agentTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }

            // Skip if already assigned
            if (customerAgentTypeRepository.existsByCustomerCustomerIdAndAgentType(customerId, agentType)) {
                continue;
            }

            CustomerAgentTypeEntity assignment = new CustomerAgentTypeEntity();
            assignment.setCustomer(customer);
            assignment.setAgentType(agentType);
            assignment.setEnabled(true);
            assignment.setCustomTokenLimit(request.defaultCustomTokenLimit());
            assignment.setPriority(request.defaultPriority() != null ? request.defaultPriority() : 0);

            newAssignments.add(customerAgentTypeRepository.save(assignment));
        }

        auditService.logAdminCustomerAction(
            SecurityAuditLogEntity.EventType.ADMIN_CUSTOMER_AGENT_TYPES_BULK_ASSIGNED,
            customerId,
            String.format("Bulk assigned %d agent types", newAssignments.size())
        );

        // Return updated list
        List<CustomerAgentTypeEntity> allAssignments =
            customerAgentTypeRepository.findByCustomerCustomerId(customerId);

        List<CustomerAgentTypeResponse> responses = allAssignments.stream()
            .map(this::toCustomerAgentTypeResponse)
            .toList();

        int enabledCount = (int) allAssignments.stream().filter(CustomerAgentTypeEntity::isEnabled).count();

        return ResponseEntity.ok(new CustomerAgentTypesResponse(
            customerId,
            customer.getName(),
            responses.size(),
            enabledCount,
            responses
        ));
    }

    @Operation(
        summary = "Get customers by agent type",
        description = "Returns all customers assigned to a specific agent type."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved customers"),
        @ApiResponse(responseCode = "400", description = "Invalid agent type"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Customer Agent Types")
    @GetMapping("/agent-types/{agentType}/customers")
    public ResponseEntity<AgentTypeCustomersResponse> getCustomersByAgentType(
            @Parameter(description = "Agent type") @PathVariable String agentType) {

        AgentType type;
        try {
            type = AgentType.valueOf(agentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        List<CustomerAgentTypeEntity> assignments = customerAgentTypeRepository.findByAgentType(type);

        List<CustomerAgentTypeResponse> responses = assignments.stream()
            .map(this::toCustomerAgentTypeResponse)
            .toList();

        int enabledCount = (int) assignments.stream()
            .filter(a -> a.isEnabled() && a.getCustomer().isEnabled())
            .count();

        // Get agent name from config
        String agentName = agentConfigRepository.findByAgentType(type)
            .map(AgentConfigEntity::getName)
            .orElse(type.name());

        return ResponseEntity.ok(new AgentTypeCustomersResponse(
            type.name(),
            agentName,
            responses.size(),
            enabledCount,
            responses
        ));
    }

    @Operation(
        summary = "Get agent type statistics",
        description = "Returns statistics about customer assignments per agent type."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved statistics"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing admin token")
    })
    @Tag(name = "Admin - Customer Agent Types")
    @GetMapping("/agent-types/stats")
    public ResponseEntity<AgentTypeStatisticsResponse> getAgentTypeStatistics() {
        List<Object[]> counts = customerAgentTypeRepository.countCustomersByAgentType();

        Map<String, Long> customerCountByAgentType = new HashMap<>();
        long total = 0;

        for (Object[] row : counts) {
            String type = ((AgentType) row[0]).name();
            Long count = ((Number) row[1]).longValue();
            customerCountByAgentType.put(type, count);
            total += count;
        }

        // Include all agent types, even those with 0 customers
        for (AgentType type : AgentType.values()) {
            customerCountByAgentType.putIfAbsent(type.name(), 0L);
        }

        return ResponseEntity.ok(new AgentTypeStatisticsResponse(
            customerCountByAgentType,
            total,
            AgentType.values().length
        ));
    }

    // ==================== Helper Methods ====================

    private AdminCustomerSummaryResponse toAdminCustomerSummary(CustomerEntity customer) {
        int activeTokenCount = adminService.getActiveTokenCount(customer.getId());
        int allowanceCount = adminService.getAllowanceCount(customer.getId());

        return new AdminCustomerSummaryResponse(
            customer.getId(),
            customer.getCustomerId(),
            customer.getName(),
            customer.isEnabled(),
            activeTokenCount,
            allowanceCount,
            customer.getCreatedAt(),
            customer.getUpdatedAt()
        );
    }

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
            null,
            null,
            false,
            customer.getCreatedAt(),
            customer.getUpdatedAt(),
            allowanceResponses,
            customer.getDefaultMinTokenNotificationThreshold(),
            customer.getNotificationWebhookUrl(),
            customer.getNotificationEmail()
        );
    }

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
        long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), nextReset);
        return Math.max(0, (int) daysUntil);
    }

    private CustomerAllowanceSummary toCustomerAllowanceSummary(CustomerModelAllowanceEntity allowance) {
        return new CustomerAllowanceSummary(
            allowance.getCustomer().getCustomerId(),
            allowance.getCustomer().getName(),
            allowance.getCustomer().isEnabled(),
            allowance.getAllowedTokens(),
            allowance.getTokensUsed(),
            allowance.getRemainingTokens(),
            allowance.isUnlimited(),
            allowance.getPolicyTypeName(),
            allowance.isEnabled()
        );
    }

    private ModelResponse toModelResponse(LlmModelEntity model) {
        return new ModelResponse(
            model.getModel(),
            model.getProvider(),
            model.getDisplayName(),
            model.getDescription(),
            model.getDefaultTokensForNewCustomers(),
            model.getInputTokenPricePerMillion(),
            model.getOutputTokenPricePerMillion(),
            model.isEnabled(),
            model.getCreatedAt(),
            model.getUpdatedAt()
        );
    }

    private PolicyTypeResponse toPolicyTypeResponse(PolicyTypeEntity policyType) {
        return new PolicyTypeResponse(
            policyType.getId(),
            policyType.getName(),
            policyType.getDescription(),
            policyType.getResetDays(),
            null,
            policyType.isEnabled()
        );
    }

    private AuditLogResponse toAuditLogResponse(SecurityAuditLogEntity log) {
        return new AuditLogResponse(
            log.getId(),
            log.getEventType().name(),
            log.getEventCategory().name(),
            log.getCustomerId(),
            log.getTokenId(),
            log.getSecretVersion(),
            log.getDescription(),
            log.getMetadata(),
            log.getActorType().name(),
            log.getActorId(),
            log.getCreatedAt()
        );
    }

    private AgentConfigResponse toAgentConfigResponse(AgentConfigEntity config) {
        AgentConfigResponse.ExecutionConfigResponse execConfigResponse = null;
        AgentExecutionConfigEntity execConfig = config.getDefaultExecutionConfig();
        if (execConfig != null) {
            execConfigResponse = new AgentConfigResponse.ExecutionConfigResponse(
                execConfig.getMaxTokens(),
                execConfig.getTemperature(),
                execConfig.getTimeoutSeconds(),
                execConfig.getRetryAttempts(),
                execConfig.getRetryDelayMs()
            );
        }

        LlmModelEntity model = config.getLlmModel();
        return new AgentConfigResponse(
            config.getId(),
            config.getAgentType().name(),
            config.getName(),
            config.getDescription(),
            model != null ? model.getModel() : null,
            model != null ? model.getDisplayName() : null,
            config.isEnabled(),
            config.getVersion(),
            execConfigResponse,
            config.getCreatedAt(),
            config.getUpdatedAt()
        );
    }

    private CustomerAgentTypeResponse toCustomerAgentTypeResponse(CustomerAgentTypeEntity assignment) {
        CustomerEntity customer = assignment.getCustomer();

        // Get agent name from config
        String agentName = agentConfigRepository.findByAgentType(assignment.getAgentType())
            .map(AgentConfigEntity::getName)
            .orElse(assignment.getAgentType().name());

        return new CustomerAgentTypeResponse(
            assignment.getId(),
            customer.getCustomerId(),
            customer.getName(),
            assignment.getAgentType().name(),
            agentName,
            assignment.isEnabled(),
            assignment.getCustomTokenLimit(),
            assignment.getPriority(),
            assignment.getCreatedAt(),
            assignment.getUpdatedAt()
        );
    }
}
