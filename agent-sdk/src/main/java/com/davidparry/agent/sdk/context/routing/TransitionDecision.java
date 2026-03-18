package com.davidparry.agent.sdk.context.routing;

/**
 * Resolved transition outcome after evaluating graph/fallback rules.
 *
 * @param nextAgentKey resolved target key or reserved status value
 * @param schemaValid whether content matched configured output schema
 * @param effectiveSuccess whether the result should be treated as successful for routing defaults
 * @param matchedByGraph true when a graph edge produced the decision
 */
public record TransitionDecision(
        String nextAgentKey,
        boolean schemaValid,
        boolean effectiveSuccess,
        boolean matchedByGraph
) {
}
