package com.w6w.engine.flowable;

import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.dmn.engine.DmnEngine;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Supplier;

/** Executes a version-pinned DMN table and writes its outputs into process variables. */
@Component("w6wDecisionExecutor")
public class FlowableDecisionExecutor implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(FlowableDecisionExecutor.class);
    private final Supplier<DmnEngine> dmnEngineProvider;
    private Expression decisionKey;
    private Expression stepId;
    private Expression allowedOutcomes;

    @Autowired
    public FlowableDecisionExecutor(ObjectProvider<DmnEngine> dmnEngineProvider) { this.dmnEngineProvider = dmnEngineProvider::getObject; }

    public FlowableDecisionExecutor(DmnEngine dmnEngine) { this.dmnEngineProvider = () -> dmnEngine; }

    @Override
    public void execute(DelegateExecution execution) {
        String key = decisionKey == null ? null : String.valueOf(decisionKey.getValue(execution));
        String resolvedStep = stepId == null ? execution.getCurrentActivityId() : String.valueOf(stepId.getValue(execution));
        if (key == null || key.isBlank()) throw new IllegalStateException("Missing decision key for step " + resolvedStep);
        Map<String, Object> result = dmnEngineProvider.get().getDmnDecisionService().createExecuteDecisionBuilder()
                .decisionKey(key).variables(execution.getVariables()).instanceId(execution.getProcessInstanceId())
                .activityId(execution.getCurrentActivityId()).executeWithSingleResult();
        if (result == null || result.isEmpty()) throw new IllegalStateException("Decision '" + key + "' returned no result for step " + resolvedStep);
        result.forEach(execution::setVariable);
        Object outcome = result.get("outcome");
        if (!(outcome instanceof String value) || value.isBlank())
            throw new IllegalStateException("Decision '" + key + "' must produce a non-blank string output named 'outcome'");
        Set<String> allowed = allowedOutcomes == null ? Set.of() : Arrays.stream(String.valueOf(allowedOutcomes.getValue(execution)).split(",", -1))
                .collect(Collectors.toSet());
        if (!allowed.contains(value)) throw new IllegalStateException("Decision '" + key + "' returned undeclared outcome '" + value + "' for step " + resolvedStep);
        log.info("Evaluated decision '{}' for instance '{}' at step '{}': outcome='{}'", key, execution.getProcessInstanceId(), resolvedStep, value);
    }
}
