package com.w6w.compiler;

import com.w6w.model.ConditionDefinition;
import java.util.ArrayList;
import java.util.List;

public class ConditionCompiler {

    public static String toJuelExpression(ConditionDefinition cond) {
        if (cond == null) {
            return "";
        }
        String expr = compileCondition(cond);
        return "${" + expr + "}";
    }

    private static String compileCondition(ConditionDefinition cond) {
        if (cond.getNot() != null) {
            return "!(" + compileCondition(cond.getNot()) + ")";
        }
        if (cond.getAnd() != null && !cond.getAnd().isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (ConditionDefinition child : cond.getAnd()) {
                parts.add(compileCondition(child));
            }
            return "(" + String.join(" && ", parts) + ")";
        }
        if (cond.getOr() != null && !cond.getOr().isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (ConditionDefinition child : cond.getOr()) {
                parts.add(compileCondition(child));
            }
            return "(" + String.join(" || ", parts) + ")";
        }

        String field = cond.getField();
        if (field == null) {
            return "true";
        }
        // Normalize field name to variable notation if starts with data.
        String varName = field.startsWith("data.") ? field.substring(5) : field;
        String op = cond.getOperator() != null ? cond.getOperator().toLowerCase() : "eq";
        Object val = cond.getValue();

        switch (op) {
            case "eq":
                return varName + " == " + formatValue(val);
            case "ne":
                return varName + " != " + formatValue(val);
            case "lt":
                return varName + " < " + formatValue(val);
            case "lte":
                return varName + " <= " + formatValue(val);
            case "gt":
                return varName + " > " + formatValue(val);
            case "gte":
                return varName + " >= " + formatValue(val);
            case "exists":
                return "execution.hasVariable('" + varName + "')";
            default:
                return varName + " == " + formatValue(val);
        }
    }

    private static String formatValue(Object val) {
        if (val == null) {
            return "null";
        }
        if (val instanceof String) {
            return "'" + val.toString().replace("'", "\\'") + "'";
        }
        if (val instanceof Boolean || val instanceof Number) {
            return val.toString();
        }
        return "'" + val.toString() + "'";
    }
}
