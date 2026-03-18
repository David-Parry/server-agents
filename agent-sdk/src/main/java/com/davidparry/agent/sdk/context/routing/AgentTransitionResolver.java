package com.davidparry.agent.sdk.context.routing;

import com.davidparry.agent.protocol.Agent;
import com.davidparry.agent.protocol.AgentTransitionEdge;
import com.davidparry.agent.protocol.SessionResult;
import com.davidparry.agent.protocol.dto.NextAgentStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves the next transition target for a session result.
 *
 * Resolution order:
 * 1) graph edge match (if configured)
 * 2) websocket result next_agent (server directive)
 * 3) legacy configured next_agent (success path only)
 * 4) status default (END_CHAIN / FAILED_AGENT)
 */
public class AgentTransitionResolver {

    private final OutputSchemaValidator outputSchemaValidator;
    private final EdgeConditionEvaluator edgeConditionEvaluator;

    public AgentTransitionResolver(OutputSchemaValidator outputSchemaValidator,
                                   EdgeConditionEvaluator edgeConditionEvaluator) {
        this.outputSchemaValidator = outputSchemaValidator;
        this.edgeConditionEvaluator = edgeConditionEvaluator;
    }

    public TransitionDecision resolve(Agent agent, SessionResult result) {
        boolean schemaValid = outputSchemaValidator.isValid(agent.outputSchema(), result.getContent());
        boolean effectiveSuccess = result.isSuccess() && schemaValid;
        Map<String, Object> context = buildContext(result, schemaValid, effectiveSuccess);

        String graphTarget = resolveFromGraph(agent, context);
        if (graphTarget != null) {
            return new TransitionDecision(graphTarget, schemaValid, effectiveSuccess, true);
        }

        String resultDirectedTarget = normalizeTarget(result.getNextAgent());
        if (resultDirectedTarget != null) {
            return new TransitionDecision(resultDirectedTarget, schemaValid, effectiveSuccess, false);
        }

        if (!effectiveSuccess) {
            return new TransitionDecision(NextAgentStatus.FAILED_AGENT.getValue(), schemaValid, false, false);
        }

        String legacyTarget = normalizeTarget(agent.nextAgent());
        if (legacyTarget != null) {
            return new TransitionDecision(legacyTarget, schemaValid, true, false);
        }

        return new TransitionDecision(NextAgentStatus.END_CHAIN.getValue(), schemaValid, true, false);
    }

    private String resolveFromGraph(Agent agent, Map<String, Object> context) {
        if (!agent.hasGraph()) {
            return null;
        }
        for (AgentTransitionEdge edge : agent.graph().edges()) {
            if (edgeConditionEvaluator.matches(edge.when(), context)) {
                return normalizeTarget(edge.to());
            }
        }
        return null;
    }

    private Map<String, Object> buildContext(SessionResult result, boolean schemaValid, boolean effectiveSuccess) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("success", result.isSuccess());
        context.put("schema_valid", schemaValid);
        context.put("effective_success", effectiveSuccess);
        context.put("error_message", result.getErrorMessage());

        JsonNode content = result.getContent();
        if (content != null && content.isObject()) {
            content.properties().forEach(entry -> context.put(entry.getKey(), toPlainValue(entry.getValue())));
        }
        return context;
    }

    private Object toPlainValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        return node;
    }

    private String normalizeTarget(String rawTarget) {
        if (rawTarget == null) {
            return null;
        }
        String trimmed = rawTarget.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
