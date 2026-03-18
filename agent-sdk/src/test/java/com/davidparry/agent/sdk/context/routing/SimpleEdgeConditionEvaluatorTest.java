package com.davidparry.agent.sdk.context.routing;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimpleEdgeConditionEvaluatorTest {

    private final SimpleEdgeConditionEvaluator evaluator = new SimpleEdgeConditionEvaluator();

    @Test
    void matchesDefaultCondition() {
        assertTrue(evaluator.matches("default", Map.of()));
    }

    @Test
    void matchesStringEquality() {
        assertTrue(evaluator.matches("$status == 'fail'", Map.of("status", "fail")));
        assertFalse(evaluator.matches("$status == 'fail'", Map.of("status", "ok")));
    }

    @Test
    void matchesBooleanInequality() {
        assertTrue(evaluator.matches("$schema_valid != true", Map.of("schema_valid", false)));
        assertFalse(evaluator.matches("$schema_valid != true", Map.of("schema_valid", true)));
    }

    @Test
    void returnsFalseForMalformedExpressions() {
        assertFalse(evaluator.matches("$status = 'fail'", Map.of("status", "fail")));
        assertFalse(evaluator.matches("status == 'fail'", Map.of("status", "fail")));
    }
}
