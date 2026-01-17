package com.davidparry.agent.dto;

import java.time.LocalDateTime;

public record TokenGeneratedResponse(
    String apiToken,
    LocalDateTime expiresAt,
    String message
) {}
