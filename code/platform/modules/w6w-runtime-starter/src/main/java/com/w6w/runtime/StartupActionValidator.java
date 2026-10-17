package com.w6w.runtime;

import com.w6w.model.StageDefinition;
import com.w6w.model.StepDefinition;
import com.w6w.model.WorkflowDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class StartupActionValidator {
    private static final Logger log = LoggerFactory.getLogger(StartupActionValidator.class);
    private final ActionRegistry actionRegistry;

    public StartupActionValidator(ActionRegistry actionRegistry) {
        this.actionRegistry = actionRegistry;
    }

    public void validateWorkflowActions(WorkflowDefinition workflow) {
        String workflowKey = workflow.getMetadata() != null ? workflow.getMetadata().getKey() : "unknown";
        List<String> missingActions = new ArrayList<>();

        for (StageDefinition stage : workflow.getStages()) {
            for (StepDefinition step : stage.getSteps()) {
                if ("serviceTask".equals(step.getType()) && step.getAction() != null) {
                    String actionName = step.getAction();
                    if (!actionRegistry.hasHandler(actionName)) {
                        missingActions.add(String.format("Step '%s' in Stage '%s' requires action '%s'", 
                            step.getId(), stage.getId(), actionName));
                    }
                }
            }
        }

        if (!missingActions.isEmpty()) {
            String errorMsg = String.format(
                "W6W Startup Validation Failure for Workflow '%s'. Unregistered Actions Detected:\n  - %s\n" +
                "Ensure Spring beans implementing @WorkflowAction are registered for these actions.",
                workflowKey, String.join("\n  - ", missingActions));
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        log.info("W6W Startup Validation Passed: All service task actions verified for workflow '{}'", workflowKey);
    }
}
