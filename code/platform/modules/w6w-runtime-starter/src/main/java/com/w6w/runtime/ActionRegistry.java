package com.w6w.runtime;

import com.w6w.port.action.WorkflowAction;
import com.w6w.port.action.WorkflowActionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ActionRegistry implements BeanPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(ActionRegistry.class);
    private final Map<String, WorkflowActionHandler> handlers = new ConcurrentHashMap<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof WorkflowActionHandler) {
            WorkflowAction annotation = bean.getClass().getAnnotation(WorkflowAction.class);
            if (annotation != null) {
                String actionName = annotation.value();
                handlers.put(actionName, (WorkflowActionHandler) bean);
                log.info("Registered W6W action handler: '{}' -> {}", actionName, bean.getClass().getSimpleName());
            }
        }
        return bean;
    }

    public void register(String actionName, WorkflowActionHandler handler) {
        handlers.put(actionName, handler);
        log.info("Programmatically registered W6W action handler: '{}'", actionName);
    }

    public WorkflowActionHandler getHandler(String actionName) {
        return handlers.get(actionName);
    }

    public boolean hasHandler(String actionName) {
        return handlers.containsKey(actionName);
    }

    public Map<String, WorkflowActionHandler> getAllHandlers() {
        return Collections.unmodifiableMap(handlers);
    }
}
