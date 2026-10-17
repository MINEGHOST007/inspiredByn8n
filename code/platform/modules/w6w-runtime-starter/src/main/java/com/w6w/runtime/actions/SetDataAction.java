package com.w6w.runtime.actions;

import com.w6w.port.action.ActionContext;
import com.w6w.port.action.ActionResult;
import com.w6w.port.action.WorkflowAction;
import com.w6w.port.action.WorkflowActionHandler;
import com.w6w.runtime.SecretProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Standard action that writes explicit constants or existing data fields back to workflow data. */
@Component
@WorkflowAction("w6w.data.set")
public class SetDataAction implements WorkflowActionHandler {
    private final SecretProvider secrets;

    public SetDataAction(SecretProvider secrets) { this.secrets = secrets; }

    @Override
    public ActionResult execute(ActionContext context) {
        Object configuredValues = context.getConfig("values");
        if (!(configuredValues instanceof Map<?, ?> values) || values.isEmpty())
            throw new IllegalArgumentException("w6w.data.set requires a non-empty config.values object");
        Map<String, Object> outputs = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String field = String.valueOf(key);
            if (!field.matches("[A-Za-z_][A-Za-z0-9_]*"))
                throw new IllegalArgumentException("w6w.data.set output must be a top-level workflow field: " + field);
            outputs.put(field, ActionValueResolver.resolve(value, context, secrets));
        });
        return ActionResult.success(outputs);
    }
}
