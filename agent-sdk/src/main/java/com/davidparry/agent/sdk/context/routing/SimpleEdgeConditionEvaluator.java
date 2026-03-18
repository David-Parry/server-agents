package com.davidparry.agent.sdk.context.routing;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small expression evaluator for graph edge conditions.
 *
 * Supports:
 * - default
 * - $field == 'value'
 * - $field != 'value'
 * - booleans/numbers/null literals
 */
public class SimpleEdgeConditionEvaluator implements EdgeConditionEvaluator {

    private static final Pattern EXPRESSION_PATTERN =
            Pattern.compile("^\\s*\\$([a-zA-Z_][a-zA-Z0-9_]*)\\s*(==|!=)\\s*(.+?)\\s*$");

    @Override
    public boolean matches(String expression, Map<String, Object> context) {
        if (expression == null) {
            return false;
        }

        String trimmed = expression.trim();
        if ("default".equalsIgnoreCase(trimmed)) {
            return true;
        }

        Matcher matcher = EXPRESSION_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return false;
        }

        String variable = matcher.group(1);
        String operator = matcher.group(2);
        String rawLiteral = matcher.group(3);

        Object left = context.get(variable);
        Object right = parseLiteral(rawLiteral);
        boolean equals = valuesEqual(left, right);

        return "==".equals(operator) ? equals : !equals;
    }

    private Object parseLiteral(String rawLiteral) {
        if (rawLiteral == null) {
            return null;
        }
        String literal = rawLiteral.trim();
        if (literal.length() >= 2
                && ((literal.startsWith("'") && literal.endsWith("'"))
                || (literal.startsWith("\"") && literal.endsWith("\"")))) {
            return literal.substring(1, literal.length() - 1);
        }
        if ("true".equalsIgnoreCase(literal)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(literal)) {
            return Boolean.FALSE;
        }
        if ("null".equalsIgnoreCase(literal)) {
            return null;
        }
        try {
            return new BigDecimal(literal);
        } catch (NumberFormatException ignored) {
            return literal;
        }
    }

    private boolean valuesEqual(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }

        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            BigDecimal leftDecimal = new BigDecimal(leftNumber.toString());
            BigDecimal rightDecimal = new BigDecimal(rightNumber.toString());
            return leftDecimal.compareTo(rightDecimal) == 0;
        }

        return left.equals(right);
    }
}
