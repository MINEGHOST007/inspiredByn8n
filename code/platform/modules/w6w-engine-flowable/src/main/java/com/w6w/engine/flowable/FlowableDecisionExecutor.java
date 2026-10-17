package com.w6w.engine.flowable;

import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.dmn.engine.DmnEngine;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import com.w6w.runtime.WorkflowDataSchemaRegistry;
import com.w6w.runtime.WorkflowMdcContext;

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
    private final WorkflowDataSchemaRegistry dataSchemas;
    private final RepositoryService repositoryService;
    private Expression decisionKey;
    private Expression stepId;
    private Expression allowedOutcomes;

    @Autowired
    public FlowableDecisionExecutor(ObjectProvider<DmnEngine> dmnEngineProvider,
                                    WorkflowDataSchemaRegistry dataSchemas,
                                    RepositoryService repositoryService) {
        this.dmnEngineProvider = dmnEngineProvider::getObject;
        this.dataSchemas = dataSchemas;
        this.repositoryService = repositoryService;
    }

    public FlowableDecisionExecutor(DmnEngine dmnEngine) {
        this.dmnEngineProvider = () -> dmnEngine;
        this.dataSchemas = null;
        this.repositoryService = null;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String key = decisionKey == null ? null : String.valueOf(decisionKey.getValue(execution));
        String resolvedStep = stepId == null ? execution.getCurrentActivityId() : String.valueOf(stepId.getValue(execution));
        if (key == null || key.isBlank()) throw new IllegalStateException("Missing decision key for step " + resolvedStep);
        Object traceValue = execution.getVariable(WorkflowMdcContext.TRACE_VARIABLE);
        String traceId = traceValue instanceof String value ? value : null;
        try (var ignored = WorkflowMdcContext.open(traceId, execution.getProcessInstanceId(), null, resolvedStep)) {
            evaluate(execution, key, resolvedStep);
        }
    }

    private void evaluate(DelegateExecution execution, String key, String resolvedStep) {
        Map<String, Object> result = dmnEngineProvider.get().getDmnDecisionService().createExecuteDecisionBuilder()
                .decisionKey(key).variables(WorkflowMdcContext.workflowData(execution.getVariables())).instanceId(execution.getProcessInstanceId())
                .activityId(execution.getCurrentActivityId()).executeWithSingleResult();
        if (result == null || result.isEmpty()) throw new IllegalStateException("Decision '" + key + "' returned no result for step " + resolvedStep);
        result.forEach(execution::setVariable);
        Object outcome = result.get("outcome");
        if (!(outcome instanceof String value) || value.isBlank())
            throw new IllegalStateException("Decision '" + key + "' must produce a non-blank string output named 'outcome'");
        Set<String> allowed = allowedOutcomes == null ? Set.of() : Arrays.stream(String.valueOf(allowedOutcomes.getValue(execution)).split(",", -1))
                .collect(Collectors.toSet());
        if (!allowed.contains(value)) throw new IllegalStateException("Decision '" + key + "' returned undeclared outcome '" + value + "' for step " + resolvedStep);
        if (dataSchemas != null && repositoryService != null) {
            ProcessDefinition definition = repositoryService.getProcessDefinition(execution.getProcessDefinitionId());
            if (definition != null) dataSchemas.validate(definition.getId(), WorkflowMdcContext.workflowData(execution.getVariables()));
        }
        log.info("Evaluated decision '{}' for instance '{}' at step '{}': outcome='{}'", key, execution.getProcessInstanceId(), resolvedStep, value);
    }
}
