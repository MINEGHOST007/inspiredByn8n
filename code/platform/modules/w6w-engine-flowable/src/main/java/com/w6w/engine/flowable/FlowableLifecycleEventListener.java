package com.w6w.engine.flowable;

import com.w6w.runtime.WorkflowLifecycleEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.ObjectProvider;
import org.flowable.task.api.Task;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Bridges engine lifecycle transitions to the host application's Spring event bus. */
@Component
public class FlowableLifecycleEventListener implements FlowableEventListener {
    private static final Logger log = LoggerFactory.getLogger(FlowableLifecycleEventListener.class);
    private final ApplicationEventPublisher events;
    private final ObjectProvider<RuntimeService> runtimeServices;
    private final ObjectProvider<HistoryService> historyServices;

    public FlowableLifecycleEventListener(ApplicationEventPublisher events,
                                          ObjectProvider<RuntimeService> runtimeServices,
                                          ObjectProvider<HistoryService> historyServices) {
        this.events = events;
        this.runtimeServices = runtimeServices;
        this.historyServices = historyServices;
    }

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEngineEvent engineEvent)) return;
        String processInstanceId = engineEvent.getProcessInstanceId();
        if (processInstanceId == null) return;

        if (event.getType() == FlowableEngineEventType.TASK_CREATED) {
            if (!(event instanceof FlowableEntityEvent entityEvent) || !(entityEvent.getEntity() instanceof Task task)) return;
            String businessKey = null;
            if (entityEvent.getEntity() instanceof ProcessInstance processInstance)
                businessKey = processInstance.getBusinessKey();
            publishAfterCommit(new WorkflowLifecycleEvent("TASK_CREATED", processInstanceId,
                    definitionKey(task.getProcessDefinitionId()), businessKey, task.getId(),
                    Map.of("stepId", safe(task.getTaskDefinitionKey()), "taskName", safe(task.getName())), Instant.now()));
            return;
        }

        String eventType = switch ((FlowableEngineEventType) event.getType()) {
            case PROCESS_STARTED -> "INSTANCE_STARTED";
            case PROCESS_COMPLETED -> "INSTANCE_COMPLETED";
            default -> null;
        };
        if (eventType == null) return;

        String businessKey = null;
        if (event instanceof FlowableEntityEvent entityEvent
                && entityEvent.getEntity() instanceof ProcessInstance processInstance)
            businessKey = processInstance.getBusinessKey();
        publishAfterCommit(new WorkflowLifecycleEvent(eventType, processInstanceId,
                definitionKey(engineEvent.getProcessDefinitionId()), businessKey, null,
                Map.of("processDefinitionId", safe(engineEvent.getProcessDefinitionId())), Instant.now()));
    }

    @Override public boolean isFailOnException() { return false; }
    @Override public boolean isFireOnTransactionLifecycleEvent() { return false; }
    @Override public String getOnTransaction() { return null; }

    @Override
    public Collection<org.flowable.common.engine.api.delegate.event.FlowableEventType> getTypes() {
        return List.of(FlowableEngineEventType.PROCESS_STARTED, FlowableEngineEventType.TASK_CREATED,
                FlowableEngineEventType.PROCESS_COMPLETED);
    }

    private String definitionKey(String processDefinitionId) {
        if (processDefinitionId == null) return null;
        int separator = processDefinitionId.indexOf(':');
        return separator < 0 ? processDefinitionId : processDefinitionId.substring(0, separator);
    }

    private void publishAfterCommit(WorkflowLifecycleEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { publish(event); }
        });
    }

    private void publish(WorkflowLifecycleEvent event) {
        WorkflowLifecycleEvent enriched = event;
        try {
            enriched = withBusinessKey(event);
        } catch (RuntimeException e) {
            log.warn("Could not enrich lifecycle event '{}' for instance '{}' with a business key",
                    event.eventType(), event.instanceId(), e);
        }
        try {
            events.publishEvent(enriched);
        } catch (RuntimeException e) {
            log.error("Host lifecycle event subscriber failed for '{}' on instance '{}'",
                    event.eventType(), event.instanceId(), e);
        }
    }

    private WorkflowLifecycleEvent withBusinessKey(WorkflowLifecycleEvent event) {
        if (event.businessKey() != null) return event;
        RuntimeService runtimeService = runtimeServices.getIfAvailable();
        if (runtimeService != null) {
            ProcessInstance active = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(event.instanceId()).singleResult();
            if (active != null) return copyWithBusinessKey(event, active.getBusinessKey());
        }
        HistoryService historyService = historyServices.getIfAvailable();
        if (historyService != null) {
            HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(event.instanceId()).singleResult();
            if (historic != null) return copyWithBusinessKey(event, historic.getBusinessKey());
        }
        return event;
    }

    private WorkflowLifecycleEvent copyWithBusinessKey(WorkflowLifecycleEvent event, String businessKey) {
        return new WorkflowLifecycleEvent(event.eventType(), event.instanceId(), event.workflowKey(), businessKey,
                event.taskId(), event.details(), event.occurredAt());
    }

    private String safe(String value) { return value == null ? "" : value; }

}
