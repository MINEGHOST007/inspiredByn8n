package com.w6w.port.action;

public interface WorkflowActionHandler {
    ActionResult execute(ActionContext context) throws Exception;
}
