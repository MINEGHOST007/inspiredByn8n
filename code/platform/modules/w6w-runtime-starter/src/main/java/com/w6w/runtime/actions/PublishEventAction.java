package com.w6w.runtime.actions;

import com.w6w.port.action.ActionContext;
import com.w6w.port.action.ActionResult;
import com.w6w.port.action.WorkflowAction;
import com.w6w.port.action.WorkflowActionHandler;
import com.w6w.runtime.SecretProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/** Publishes a host-consumable Spring event from a service task. */
@Component
@WorkflowAction("w6w.events.publish")
public class PublishEventAction implements WorkflowActionHandler {
    private final ApplicationEventPublisher events;
    private final SecretProvider secrets;

    public PublishEventAction(ApplicationEventPublisher events, SecretProvider secrets) {
        this.events = events;
        this.secrets = secrets;
    }

    @Override
    public ActionResult execute(ActionContext context) {
        Object nameValue = context.getConfig("eventName");
        if (!(nameValue instanceof String eventName) || !eventName.matches("[A-Za-z][A-Za-z0-9_.-]{0,127}"))
            throw new IllegalArgumentException("w6w.events.publish requires a valid config.eventName");
        Object payloadValue = context.getConfig("payload");
        Map<String, Object> payload = payloadValue == null ? Map.of()
                : ActionValueResolver.resolveObject(payloadValue, context, secrets, "w6w.events.publish config.payload");
        events.publishEvent(new WorkflowPublishedEvent(eventName, context.getInstanceId(), context.getStepId(),
                payload, Instant.now()));
        return ActionResult.success();
    }
}
