 package com.davidparry.agent.protocol.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NextAgentStatus enum.
 */
class NextAgentStatusTest {

    @Test
    void testEndChainValue() {
        assertEquals("END_CHAIN", NextAgentStatus.END_CHAIN.name());
        assertEquals("END_CHAIN", NextAgentStatus.END_CHAIN.getValue());
    }

    @Test
    void testFailedAgentValue() {
        assertEquals("FAILED_AGENT", NextAgentStatus.FAILED_AGENT.name());
        assertEquals("FAILED_AGENT", NextAgentStatus.FAILED_AGENT.getValue());
    }

    @Test
    void testValueOf() {
        assertEquals(NextAgentStatus.END_CHAIN, NextAgentStatus.valueOf("END_CHAIN"));
        assertEquals(NextAgentStatus.FAILED_AGENT, NextAgentStatus.valueOf("FAILED_AGENT"));
    }

    @Test
    void testValues() {
        NextAgentStatus[] values = NextAgentStatus.values();
        assertEquals(2, values.length);
        assertArrayEquals(new NextAgentStatus[]{NextAgentStatus.END_CHAIN, NextAgentStatus.FAILED_AGENT}, values);
    }

    @Test
    void testEnumCanBeUsedAsStringInSessionResult() {
        // Demonstrates how the enum can be used with SessionResult
        String nextAgentValue = NextAgentStatus.END_CHAIN.getValue();
        assertEquals("END_CHAIN", nextAgentValue);
        
        nextAgentValue = NextAgentStatus.FAILED_AGENT.getValue();
        assertEquals("FAILED_AGENT", nextAgentValue);
    }
}
