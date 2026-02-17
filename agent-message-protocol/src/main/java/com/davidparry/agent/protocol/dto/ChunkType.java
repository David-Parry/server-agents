package com.davidparry.agent.protocol.dto;

/**
 * Types of streaming response chunks.
 * Enums are inherently thread-safe.
 */
public enum ChunkType {
    TEXT,
    TOOL_START,
    TOOL_END,
    THINKING,
    ERROR
}
