package com.w6w.runtime.actions;

import com.w6w.port.action.ActionContext;
import com.w6w.port.action.ActionResult;
import com.w6w.port.action.WorkflowAction;
import com.w6w.port.action.WorkflowActionHandler;
import com.w6w.runtime.SecretProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/** Standard email action delegating delivery and credentials to the host application. */
@Component
@WorkflowAction("w6w.email.send")
public class SendEmailAction implements WorkflowActionHandler {
    private final ObjectProvider<WorkflowEmailGateway> gateway;
    private final SecretProvider secrets;

    public SendEmailAction(ObjectProvider<WorkflowEmailGateway> gateway, SecretProvider secrets) {
        this.gateway = gateway;
        this.secrets = secrets;
    }

    @Override
    public ActionResult execute(ActionContext context) {
        WorkflowEmailGateway sender = gateway.getIfAvailable();
        if (sender == null) throw new IllegalStateException("Register a WorkflowEmailGateway in the host application before using w6w.email.send");
        try {
            String from = string(context, "from", true);
            String to = string(context, "to", false);
            String subject = string(context, "subject", false);
            String body = string(context, "body", false);
            Object configuredCc = ActionValueResolver.resolve(context.getConfig("cc"), context, secrets);
            List<String> cc = addresses(configuredCc);
            sender.send(new WorkflowEmailMessage(from, List.of(to.split(",")), cc, subject, body));
            return ActionResult.success();
        } catch (Exception e) {
            return ActionResult.failure(e.getMessage() == null ? "Email delivery failed" : e.getMessage());
        }
    }

    private String string(ActionContext context, String key, boolean optional) {
        Object value = ActionValueResolver.resolve(context.getConfig(key), context, secrets);
        if (value == null && optional) return null;
        if (!(value instanceof String text) || text.isBlank())
            throw new IllegalArgumentException("w6w.email.send requires config." + key);
        return text;
    }

    private List<String> addresses(Object value) {
        if (value == null) return List.of();
        if (value instanceof String addressList) return java.util.Arrays.stream(addressList.split(","))
                .map(String::trim).filter(address -> !address.isBlank()).toList();
        if (value instanceof List<?> list) return list.stream().map(item -> String.valueOf(item).trim())
                .filter(address -> !address.isBlank()).toList();
        throw new IllegalArgumentException("w6w.email.send config.cc must be a string or list of addresses");
    }
}
