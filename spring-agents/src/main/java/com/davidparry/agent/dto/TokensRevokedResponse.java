package com.davidparry.agent.dto;

public record TokensRevokedResponse(
    int revokedCount,
    String message
) {}
