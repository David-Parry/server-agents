package com.davidparry.agent.dto;

public record GenerateTokenRequest(
    Integer expiresInDays,  // Optional - null means no expiration
    Boolean revokeExisting  // Optional - if true, revokes all existing tokens
) {}
