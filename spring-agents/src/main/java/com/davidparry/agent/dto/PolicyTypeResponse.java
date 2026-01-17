package com.davidparry.agent.dto;

import java.util.UUID;

public record PolicyTypeResponse(
    UUID id,
    String name,
    String description,
    Integer resetDays,
    Long defaultTokens,
    boolean enabled
) {}
