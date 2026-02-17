package com.davidparry.agent.app.controller;

import com.davidparry.agent.sdk.context.*;
import com.davidparry.agent.protocol.Agent;
import com.davidparry.agent.protocol.SessionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for agent operations using AgentApplicationContext.
 *
 * This controller provides a clean API for:
 * - Listing available agents
 * - Activating agents with prompt parameters
 * - Monitoring active sessions
 * - Cancelling sessions
 *
 * All agent activation goes through AgentApplicationContext, which handles:
 * - Agent lookup from configuration
 * - Session ID generation
 * - MCP server management
 * - Session lifecycle events
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController extends SessionEventListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);

    private final AgentApplicationContext context;
    private final ObjectMapper objectMapper;

    public AgentController(AgentApplicationContext context, ObjectMapper objectMapper) {
        this.context = context;
        this.objectMapper = objectMapper;
    }

    /**
     * List all available agents.
     *
     * @return set of agent keys
     */
    @GetMapping
    public ResponseEntity<Set<String>> listAgents() {
        return ResponseEntity.ok(context.getAgentKeys());
    }

    /**
     * Get details of a specific agent.
     *
     * @param agentKey the agent key
     * @return agent details or 404 if not found
     */
    @GetMapping("/{agentKey}")
    public ResponseEntity<Map<String, Object>> getAgent(@PathVariable String agentKey) {
        Optional<Agent> agentOpt = context.getAgent(agentKey);

        if (agentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Agent agent = agentOpt.get();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", agent.name());
        details.put("description", agent.description());
        details.put("type", agent.type());
        details.put("hasInstructions", agent.instructions() != null && !agent.instructions().isBlank());
        details.put("hasMcpConfig", agent.hasMcpConfig());
        details.put("hasTools", agent.hasTools());
        details.put("hasOutputSchema", agent.hasOutputSchema());

        if (agent.hasTools()) {
            details.put("tools", agent.tools());
        }

        return ResponseEntity.ok(details);
    }

    /**
     * Activate an agent with the given parameters.
     *
     * This endpoint starts an agent session asynchronously and returns immediately
     * with the session ID. Use the session endpoints to monitor progress.
     *
     * @param agentKey the agent key
     * @param params prompt parameters to substitute in the agent's instructions
     * @return session information including session ID
     */
    @PostMapping("/{agentKey}/activate")
    public ResponseEntity<Map<String, Object>> activateAgent(
            @PathVariable String agentKey,
            @RequestBody Map<String, Object> params) {

        logger.info("Activating agent '{}' with params: {}", agentKey, params.keySet());

        if (!context.hasAgent(agentKey)) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Agent not found: " + agentKey);
            error.put("availableAgents", context.getAgentKeys());
            return ResponseEntity.notFound().build();
        }

        if (!context.isConnected()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Not connected to server");
            error.put("connected", false);
            return ResponseEntity.status(503).body(error);
        }

        try {
            JsonNode promptParams = objectMapper.valueToTree(params);

            // Use activateAgentWithResult to get the session ID directly
            ActivationResult activation = context.activateAgent(agentKey, promptParams, this);
            String sessionId = activation.sessionId();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("sessionId", sessionId);
            response.put("agentKey", agentKey);
            response.put("status", "activated");
            response.put("message", "Agent session started. Use /api/agents/sessions/" + sessionId + " to monitor progress.");

            // Handle the result asynchronously without blocking
            activation.resultFuture().whenComplete((chainResult, throwable) -> {
                if (throwable != null) {
                    logger.error("#############!!!!!!!!!!!!!!Agent session {} failed with error", sessionId, throwable);
                } else if (chainResult != null) {
                    if (chainResult.isFullySuccessful()) {
                        logger.info("!!#####@@@@@@@@@@@@@@@@@@ Agent chain {} completed successfully with {} agents",
                            sessionId, chainResult.getExecutedAgentCount());
                        chainResult.getFinalResult().ifPresent(result ->
                            logger.info("Final result content: {}", result.getContent()));
                    } else {
                        logger.warn(":-( !!!@@@######### Agent chain {} completed with status {}: {} agents executed, {} successful",
                            sessionId,
                            chainResult.status(),
                            chainResult.getExecutedAgentCount(),
                            chainResult.getSuccessfulAgentCount());
                        if (chainResult.failureCause() != null) {
                            logger.warn("Failure cause: {}", chainResult.failureCause().getMessage());
                        }
                    }
                }
            });

            return ResponseEntity.accepted().body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request for agent activation", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (IllegalStateException e) {
            logger.error("Cannot activate agent - invalid state", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(503).body(error);
        }
    }


    /**
     * List all active sessions.
     *
     * @return list of active session summaries
     */
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> listSessions() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("activeSessionCount", context.getActiveSessionCount());
        response.put("connected", context.isConnected());

        List<Map<String, Object>> sessions = new ArrayList<>();
        for (AgentSession session : context.getActiveSessions()) {
            Map<String, Object> sessionInfo = new LinkedHashMap<>();
            sessionInfo.put("sessionId", session.getSessionId());
            sessionInfo.put("agentKey", session.getAgentKey());
            sessionInfo.put("state", session.getState().name());
            sessionInfo.put("durationMs", session.getDurationMs());
            sessionInfo.put("startTimeMs", session.getStartTimeMs());
            sessions.add(sessionInfo);
        }
        response.put("sessions", sessions);

        return ResponseEntity.ok(response);
    }

    /**
     * Get details of a specific session.
     *
     * @param sessionId the session ID
     * @return session details or 404 if not found
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionId) {
        Optional<AgentSession> sessionOpt = context.getSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AgentSession session = sessionOpt.get();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sessionId", session.getSessionId());
        response.put("agentKey", session.getAgentKey());
        response.put("state", session.getState().name());
        response.put("isActive", session.isActive());
        response.put("isCompleted", session.isCompleted());
        response.put("isFailed", session.isFailed());
        response.put("isCancelled", session.isCancelled());
        response.put("durationMs", session.getDurationMs());
        response.put("startTimeMs", session.getStartTimeMs());
        response.put("endTimeMs", session.getEndTimeMs());

        if (session.getPromptParams() != null) {
            response.put("promptParams", session.getPromptParams());
        }

        if (session.isCompleted() && session.getResult() != null) {
            response.put("success", session.getResult().isSuccess());
            response.put("content", session.getResult().getContent());
        }

        if (session.isFailed() && session.getError() != null) {
            response.put("errorMessage", session.getError().getMessage());
        }

        if (session.isCancelled()) {
            response.put("cancellationReason", session.getCancellationReason());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Cancel an active session.
     *
     * @param sessionId the session ID to cancel
     * @param request optional request body with reason
     * @return cancellation confirmation
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> cancelSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, String> request) {

        Optional<AgentSession> sessionOpt = context.getSession(sessionId);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String reason = request != null && request.containsKey("reason")
            ? request.get("reason")
            : "Cancelled via API";

        logger.info("Cancelling session {} with reason: {}", sessionId, reason);
        context.cancelSession(sessionId, reason);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sessionId", sessionId);
        response.put("status", "cancelled");
        response.put("reason", reason);

        return ResponseEntity.ok(response);
    }

    /**
     * Get context statistics.
     *
     * @return statistics about agents and sessions
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        return ResponseEntity.ok(context.getStatistics());
    }

    /**
     * Reload agent configurations.
     *
     * @return reload status
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reloadConfigurations() {
        logger.info("Reloading agent configurations");
        context.reloadAgentConfigurations();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "reloaded");
        response.put("agentCount", context.getAgentKeys().size());
        response.put("agents", context.getAgentKeys());

        return ResponseEntity.ok(response);
    }

    /**
     * Check connection status.
     *
     * @return connection status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("connected", context.isConnected());
        status.put("agentCount", context.getAgentKeys().size());
        status.put("activeSessionCount", context.getActiveSessionCount());
        return ResponseEntity.ok(status);
    }

}
