package com.w6w.engine.flowable;

import com.w6w.port.action.ActionContext;
import com.w6w.port.action.ActionResult;
import com.w6w.port.action.WorkflowActionHandler;
import com.w6w.runtime.ActionRegistry;
import com.w6w.runtime.WorkflowDataSchemaRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("w6wActionExecutor")
public class FlowableActionExecutor implements JavaDelegate {
    private static final Logger log = LoggerFactory.getLogger(FlowableActionExecutor.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ActionRegistry actionRegistry;
    private final WorkflowDataSchemaRegistry dataSchemas;
    private final RepositoryService repositoryService;

    private Expression actionName;
    private Expression stepId;
    private Expression actionConfig;

    @Autowired
    public FlowableActionExecutor(ActionRegistry actionRegistry, WorkflowDataSchemaRegistry dataSchemas,
                                  RepositoryService repositoryService) {
        this.actionRegistry = actionRegistry;
        this.dataSchemas = dataSchemas;
        this.repositoryService = repositoryService;
    }

    public FlowableActionExecutor(ActionRegistry actionRegistry) {
        this.actionRegistry = actionRegistry;
        this.dataSchemas = null;
        this.repositoryService = null;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String resolvedAction = actionName != null ? (String) actionName.getValue(execution) : null;
        String resolvedStep = stepId != null ? (String) stepId.getValue(execution) : execution.getCurrentActivityId();

        if (resolvedAction == null || resolvedAction.isEmpty()) {
            throw new IllegalStateException("Missing required field 'actionName' for step: " + resolvedStep);
        }

        WorkflowActionHandler handler = actionRegistry.getHandler(resolvedAction);
        if (handler == null) {
            throw new IllegalStateException("No registered WorkflowActionHandler for action: '" + resolvedAction + "'");
        }

        Map<String, Object> configuration = readConfiguration(execution);
        ActionContext ctx = new ActionContext(execution.getProcessInstanceId(), resolvedStep,
                execution.getVariables(), configuration);
        log.info("Executing action '{}' with IdempotencyKey='{}'", resolvedAction, ctx.getIdempotencyKey());

        try {
            ActionResult result = handler.execute(ctx);
            if (!result.isSuccess()) {
                throw new RuntimeException("Action '" + resolvedAction + "' failed: " + result.getErrorMessage());
            }

            if (result.getOutputVariables() != null && !result.getOutputVariables().isEmpty()) {
                for (Map.Entry<String, Object> entry : result.getOutputVariables().entrySet()) {
                    execution.setVariable(entry.getKey(), entry.getValue());
                }
            }
            validateWorkflowData(execution);
        } catch (Exception e) {
            log.error("Error executing action '{}' for instance '{}': {}", resolvedAction, execution.getProcessInstanceId(), e.getMessage(), e);
            throw new RuntimeException("Workflow action execution failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> readConfiguration(DelegateExecution execution) {
        if (actionConfig == null) return Map.of();
        Object value = actionConfig.getValue(execution);
        if (value == null || value.toString().isBlank()) return Map.of();
        try { return JSON.readValue(value.toString(), new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalStateException("Invalid action configuration for step "
                + execution.getCurrentActivityId(), e); }
    }

    private void validateWorkflowData(DelegateExecution execution) {
        if (dataSchemas == null || repositoryService == null) return;
        ProcessDefinition definition = repositoryService.getProcessDefinition(execution.getProcessDefinitionId());
        if (definition != null) dataSchemas.validate(definition.getId(), execution.getVariables());
    }
}
