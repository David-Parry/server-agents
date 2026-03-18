package com.davidparry.agent.sdk.context.routing;

import java.util.Map;

/**
 * Evaluates edge condition expressions against a typed context map.
 */
public interface EdgeConditionEvaluator {

    /**
     * @param expression condition expression (or "default")
     * @param context variable map keyed by names without '$'
     * @return true if expression matches
     */
    boolean matches(String expression, Map<String, Object> context);
}
