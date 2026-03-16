package com.davidparry.agent.dto;

public record UpdateTokenAllocationRequest(
    Long totalTokens        // Customer-specific token limit (null to use policy default)
) {}
