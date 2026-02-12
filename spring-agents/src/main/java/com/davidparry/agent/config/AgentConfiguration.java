package com.davidparry.agent.config;

/**
 * Configuration record for an agent type containing the system prompt and model.
 *
 * @param systemPrompt The system prompt that defines the agent's behavior and expertise
 * @param model        The LLM model to use for this agent type
 */
public record AgentConfiguration(String systemPrompt, String model) {

    /**
     * Default model to use when none is specified.
     */
    public static final String DEFAULT_MODEL = "claude-sonnet-4-5";

    /**
     * Creates an AgentConfiguration with the default model.
     *
     * @param systemPrompt The system prompt for the agent
     * @return A new AgentConfiguration with the default model
     */
    public static AgentConfiguration withDefaultModel(String systemPrompt) {
        return new AgentConfiguration(systemPrompt, DEFAULT_MODEL);
    }
}
