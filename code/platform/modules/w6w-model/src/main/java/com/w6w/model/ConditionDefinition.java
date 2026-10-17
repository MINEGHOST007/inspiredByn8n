package com.w6w.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public class ConditionDefinition {
    private String field;
    private String operator; // eq, ne, lt, lte, gt, gte, in, exists
    private Object value;
    private List<ConditionDefinition> and;
    private List<ConditionDefinition> or;
    private ConditionDefinition not;

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }

    public List<ConditionDefinition> getAnd() { return and; }
    public void setAnd(List<ConditionDefinition> and) { this.and = and; }

    public List<ConditionDefinition> getOr() { return or; }
    public void setOr(List<ConditionDefinition> or) { this.or = or; }

    public ConditionDefinition getNot() { return not; }
    public void setNot(ConditionDefinition not) { this.not = not; }
}
