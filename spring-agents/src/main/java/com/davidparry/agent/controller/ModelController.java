package com.davidparry.agent.controller;

import com.davidparry.agent.dto.CreateModelRequest;
import com.davidparry.agent.dto.ModelResponse;
import com.davidparry.agent.entity.LlmModelEntity;
import com.davidparry.agent.entity.PolicyTypeEntity;
import com.davidparry.agent.repository.LlmModelRepository;
import com.davidparry.agent.repository.PolicyTypeRepository;
import com.davidparry.agent.service.LlmModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST API for managing LLM models.
 */
@RestController
@RequestMapping("/api/models")
@Tag(name = "Models", description = "LLM model management operations")
public class ModelController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelController.class);

    private final LlmModelService llmModelService;
    private final LlmModelRepository modelRepository;
    private final PolicyTypeRepository policyTypeRepository;

    public ModelController(LlmModelService llmModelService, LlmModelRepository modelRepository,
                          PolicyTypeRepository policyTypeRepository) {
        this.llmModelService = llmModelService;
        this.modelRepository = modelRepository;
        this.policyTypeRepository = policyTypeRepository;
    }

    @Operation(
        summary = "List all models",
        description = "Returns all LLM models including disabled ones."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved model list")
    })
    @GetMapping
    public ResponseEntity<List<ModelResponse>> listModels() {
        List<ModelResponse> models = modelRepository.findAll().stream()
            .map(this::toModelResponse)
            .toList();
        return ResponseEntity.ok(models);
    }

    @Operation(
        summary = "List enabled models",
        description = "Returns only enabled LLM models."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved enabled model list")
    })
    @GetMapping("/enabled")
    public ResponseEntity<List<ModelResponse>> listEnabledModels() {
        List<ModelResponse> models = modelRepository.findByEnabledTrue().stream()
            .map(this::toModelResponse)
            .toList();
        return ResponseEntity.ok(models);
    }

    @Operation(
        summary = "Get model details",
        description = "Returns detailed information about a specific model."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved model"),
        @ApiResponse(responseCode = "404", description = "Model not found")
    })
    @GetMapping("/{model}")
    public ResponseEntity<ModelResponse> getModel(
            @Parameter(description = "Model identifier (e.g., claude-sonnet-4-5)") @PathVariable String model) {
        return modelRepository.findById(model)
            .map(this::toModelResponse)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Create a new model",
        description = "Creates a new LLM model and automatically links it to all existing customers with the default unlimited policy."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Model successfully created"),
        @ApiResponse(responseCode = "400", description = "Invalid request - model and provider are required"),
        @ApiResponse(responseCode = "409", description = "Conflict - model already exists")
    })
    @PostMapping
    public ResponseEntity<ModelResponse> createModel(@RequestBody CreateModelRequest request) {
        if (request.model() == null || request.model().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.provider() == null || request.provider().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            LlmModelEntity model = llmModelService.createModel(
                request.model(),
                request.provider(),
                request.displayName(),
                request.description(),
                request.defaultTokensForNewCustomers()
            );

            LOGGER.info("Created model: {} and linked to all customers", model.getModel());
            return ResponseEntity.status(HttpStatus.CREATED).body(toModelResponse(model));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @Operation(
        summary = "Update default tokens",
        description = "Updates the default token allowance for new customers on this model."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Default tokens successfully updated"),
        @ApiResponse(responseCode = "404", description = "Model not found")
    })
    @PatchMapping("/{model}/default-tokens")
    public ResponseEntity<ModelResponse> updateDefaultTokens(
            @Parameter(description = "Model identifier") @PathVariable String model,
            @RequestBody Map<String, Long> request) {

        Long defaultTokens = request.get("defaultTokensForNewCustomers");

        try {
            LlmModelEntity updated = llmModelService.updateDefaultTokens(model, defaultTokens);
            return ResponseEntity.ok(toModelResponse(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
        summary = "Set model enabled status",
        description = "Enables or disables a model. Disabled models cannot be used for new requests."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status successfully updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request - enabled field is required"),
        @ApiResponse(responseCode = "404", description = "Model not found")
    })
    @PatchMapping("/{model}/enabled")
    public ResponseEntity<ModelResponse> setModelEnabled(
            @Parameter(description = "Model identifier") @PathVariable String model,
            @RequestBody Map<String, Boolean> request) {

        Boolean enabled = request.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            LlmModelEntity updated = llmModelService.setModelEnabled(model, enabled);
            return ResponseEntity.ok(toModelResponse(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
        summary = "Link model to all customers",
        description = "Creates allowances for this model for all customers that don't have one. "
                + "Uses the default UNLIMITED policy type. Useful for syncing after manual database changes."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Model successfully linked to customers"),
        @ApiResponse(responseCode = "404", description = "Model not found")
    })
    @PostMapping("/{model}/link-all-customers")
    public ResponseEntity<Map<String, Object>> linkModelToAllCustomers(
            @Parameter(description = "Model identifier") @PathVariable String model) {
        LlmModelEntity llmModel = modelRepository.findById(model).orElse(null);
        if (llmModel == null) {
            return ResponseEntity.notFound().build();
        }

        // Use default unlimited policy type for new allowances
        PolicyTypeEntity defaultPolicy = policyTypeRepository.findUnlimitedPolicyType()
            .orElse(null);

        int linked = llmModelService.linkModelToAllCustomers(llmModel, defaultPolicy);

        return ResponseEntity.ok(Map.of(
            "model", model,
            "customersLinked", linked,
            "policyType", defaultPolicy != null ? defaultPolicy.getName() : "NONE"
        ));
    }

    private ModelResponse toModelResponse(LlmModelEntity model) {
        return new ModelResponse(
            model.getModel(),
            model.getProvider(),
            model.getDisplayName(),
            model.getDescription(),
            model.getDefaultTokensForNewCustomers(),
            model.isEnabled(),
            model.getCreatedAt(),
            model.getUpdatedAt()
        );
    }
}
