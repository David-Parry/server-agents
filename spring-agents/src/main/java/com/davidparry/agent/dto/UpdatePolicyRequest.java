package com.davidparry.agent.dto;

public record UpdatePolicyRequest(
    String policyTypeName,
    Long totalTokens        // Optional - customer-specific token limit
) {}
