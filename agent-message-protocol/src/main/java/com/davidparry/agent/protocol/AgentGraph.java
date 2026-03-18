package com.davidparry.agent.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Conditional transition graph for an agent.
 *
 * Edges are evaluated in order and the first matching edge wins.
 */
public record AgentGraph(
        @JsonProperty("edges")
        List<AgentTransitionEdge> edges
) {
    public AgentGraph {
        if (edges == null) {
            edges = List.of();
        }
    }

    public static AgentGraph empty() {
        return new AgentGraph(List.of());
    }

    public boolean hasEdges() {
        return !edges.isEmpty();
    }
}
