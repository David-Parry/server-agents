package com.davidparry.agent.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Single conditional transition in an agent graph.
 *
 * @param when condition expression (or "default")
 * @param to target agent key or reserved status value
 */
public record AgentTransitionEdge(
        @JsonProperty("when")
        String when,
        @JsonProperty("to")
        String to
) {
}
