package com.w6w.engine.flowable;

import com.w6w.port.action.ActionContext;
import com.w6w.port.action.ActionResult;
import com.w6w.port.action.WorkflowActionHandler;
import com.w6w.runtime.ActionRegistry;
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

    private final ActionRegistry actionRegistry;

    private Expression actionName;
    private Expression stepId;

    public FlowableActionExecutor(ActionRegistry actionRegistry) {
        this.actionRegistry = actionRegistry;
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

        ActionContext ctx = new ActionContext(execution.getProcessInstanceId(), resolvedStep, execution.getVariables());
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
        } catch (Exception e) {
            log.error("Error executing action '{}' for instance '{}': {}", resolvedAction, execution.getProcessInstanceId(), e.getMessage(), e);
            throw new RuntimeException("Workflow action execution failed: " + e.getMessage(), e);
        }
    }
}
