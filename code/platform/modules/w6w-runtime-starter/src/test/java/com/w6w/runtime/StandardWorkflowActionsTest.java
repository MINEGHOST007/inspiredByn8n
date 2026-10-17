package com.w6w.runtime;

import com.w6w.port.action.ActionContext;
import com.w6w.port.action.ActionResult;
import com.w6w.runtime.actions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.support.StaticApplicationContext;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandardWorkflowActionsTest {
    private final SecretProvider secrets = name -> "secret-value-" + name;

    @Test
    void setDataCopiesVariablesAndWritesConstants() throws Exception {
        Map<String, Object> config = Map.of("values", Map.of(
                "approvedAmount", Map.of("from", "request.amount"),
                "state", "approved"));
        ActionContext context = new ActionContext("instance-1", "set-data",
                Map.of("request", Map.of("amount", 42.5)), config);

        ActionResult result = new SetDataAction(secrets).execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputVariables()).containsEntry("approvedAmount", 42.5).containsEntry("state", "approved");
    }

    @Test
    void publishEventResolvesPayloadAndPublishesToHostListeners() throws Exception {
        AtomicReference<Object> received = new AtomicReference<>();
        ApplicationEventPublisher publisher = received::set;
        Map<String, Object> config = Map.of("eventName", "refund.approved", "payload", Map.of(
                "caseId", Map.of("from", "caseId"), "token", Map.of("secretRef", "EVENT_TOKEN")));
        ActionContext context = new ActionContext("instance-2", "publish-event", Map.of("caseId", "CASE-2"), config);

        ActionResult result = new PublishEventAction(publisher, secrets).execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(received.get()).isInstanceOf(WorkflowPublishedEvent.class);
        WorkflowPublishedEvent event = (WorkflowPublishedEvent) received.get();
        assertThat(event.eventName()).isEqualTo("refund.approved");
        assertThat(event.instanceId()).isEqualTo("instance-2");
        assertThat(event.payload()).containsEntry("caseId", "CASE-2").containsEntry("token", "secret-value-EVENT_TOKEN");
    }

    @Test
    void httpRequestUsesConfiguredMethodSecretAndWritesResponseVariables() throws Exception {
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> requestAuthorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/refund", exchange -> {
            requestMethod.set(exchange.getRequestMethod());
            requestAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"accepted\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("method", "POST");
            config.put("url", "http://127.0.0.1:" + server.getAddress().getPort() + "/refund");
            config.put("headers", Map.of("Authorization", Map.of("secretRef", "REFUND_API_TOKEN")));
            config.put("body", Map.of("caseId", Map.of("from", "caseId"), "amount", Map.of("from", "amount")));
            config.put("statusVariable", "httpStatus");
            config.put("responseBodyVariable", "httpResponse");
            ActionContext context = new ActionContext("instance-3", "send-refund", Map.of("caseId", "CASE-3", "amount", 12), config);

            ActionResult result = new HttpRequestAction(secrets, new ObjectMapper()).execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(requestMethod.get()).isEqualTo("POST");
            assertThat(requestAuthorization.get()).isEqualTo("secret-value-REFUND_API_TOKEN");
            assertThat(requestBody.get()).contains("\"caseId\":\"CASE-3\"").contains("\"amount\":12");
            assertThat(result.getOutputVariables()).containsEntry("httpStatus", 201)
                    .containsEntry("httpResponse", "{\"accepted\":true}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void emailActionDelegatesToHostGatewayAndFailsClearlyWhenUnavailable() throws Exception {
        AtomicReference<WorkflowEmailMessage> sent = new AtomicReference<>();
        StaticApplicationContext host = new StaticApplicationContext();
        host.getBeanFactory().registerSingleton("mailGateway", (WorkflowEmailGateway) sent::set);
        ObjectProvider<WorkflowEmailGateway> provider = host.getBeanProvider(WorkflowEmailGateway.class);
        Map<String, Object> config = Map.of("to", "ops@example.test", "cc", List.of("audit@example.test", Map.of("secretRef", "AUDIT_EMAIL")),
                "subject", Map.of("from", "subject"), "body", "Case {{caseId}}");
        ActionContext context = new ActionContext("instance-4", "send-email", Map.of("subject", "Approved", "caseId", "CASE-4"), config);

        ActionResult result = new SendEmailAction(provider, secrets).execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(sent.get().to()).containsExactly("ops@example.test");
        assertThat(sent.get().cc()).containsExactly("audit@example.test", "secret-value-AUDIT_EMAIL");
        assertThat(sent.get().subject()).isEqualTo("Approved");
        assertThat(sent.get().body()).isEqualTo("Case {{caseId}}");
        StaticApplicationContext noMailHost = new StaticApplicationContext();
        assertThatThrownBy(() -> new SendEmailAction(noMailHost.getBeanProvider(WorkflowEmailGateway.class), secrets)
                .execute(new ActionContext("i", "s", Map.of(), Map.of("to", "x@example.test", "subject", "x", "body", "x"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Register a WorkflowEmailGateway");
        noMailHost.close();
        host.close();
    }
}
