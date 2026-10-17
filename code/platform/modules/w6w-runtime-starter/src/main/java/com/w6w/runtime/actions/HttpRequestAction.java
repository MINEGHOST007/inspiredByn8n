package com.w6w.runtime.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.w6w.port.action.ActionContext;
import com.w6w.port.action.ActionResult;
import com.w6w.port.action.WorkflowAction;
import com.w6w.port.action.WorkflowActionHandler;
import com.w6w.runtime.SecretProvider;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Standard bounded HTTP/REST action. Credentials are resolved by the host secret provider. */
@Component
@WorkflowAction("w6w.http.request")
public class HttpRequestAction implements WorkflowActionHandler {
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final SecretProvider secrets;
    private final ObjectMapper json;

    public HttpRequestAction(SecretProvider secrets, ObjectMapper json) {
        this.secrets = secrets;
        this.json = json;
    }

    @Override
    public ActionResult execute(ActionContext context) {
        try {
            String method = requiredString(context.getConfig("method"), "method").toUpperCase(Locale.ROOT);
            if (!java.util.Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method))
                throw new IllegalArgumentException("w6w.http.request method must be GET, POST, PUT, PATCH, or DELETE");
            Object resolvedUrl = ActionValueResolver.resolve(context.getConfig("url"), context, secrets);
            URI uri = URI.create(requiredString(resolvedUrl, "url"));
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null)
                throw new IllegalArgumentException("w6w.http.request url must be an absolute HTTP or HTTPS URL");

            int timeoutSeconds = integer(context.getConfig("timeoutSeconds"), 15);
            if (timeoutSeconds < 1 || timeoutSeconds > 120)
                throw new IllegalArgumentException("w6w.http.request timeoutSeconds must be from 1 to 120");
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeoutSeconds));
            Map<String, Object> headers = context.getConfig("headers") == null ? Map.of()
                    : ActionValueResolver.resolveObject(context.getConfig("headers"), context, secrets, "headers");
            headers.forEach((name, value) -> request.header(name, String.valueOf(value)));
            request.header("Idempotency-Key", context.getIdempotencyKey());

            Object configuredBody = context.getConfig("body");
            if (configuredBody == null) request.method(method, HttpRequest.BodyPublishers.noBody());
            else {
                Object resolvedBody = ActionValueResolver.resolve(configuredBody, context, secrets);
                String body = resolvedBody instanceof String text ? text : json.writeValueAsString(resolvedBody);
                if (headers.keySet().stream().noneMatch(name -> "content-type".equalsIgnoreCase(name)))
                    request.header("Content-Type", "application/json");
                request.method(method, HttpRequest.BodyPublishers.ofString(body));
            }

            HttpResponse<java.io.InputStream> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] responseBody;
            try (java.io.InputStream body = response.body()) {
                responseBody = body.readNBytes(MAX_RESPONSE_BYTES + 1);
            }
            if (responseBody.length > MAX_RESPONSE_BYTES)
                return ActionResult.failure("HTTP response exceeded the 1 MiB action limit");
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                return ActionResult.failure("HTTP request returned status " + response.statusCode());

            Map<String, Object> outputs = new LinkedHashMap<>();
            putOutput(context, outputs, "statusVariable", response.statusCode());
            String responseVariable = optionalString(context.getConfig("responseBodyVariable"));
            if (responseVariable != null) putOutputName(responseVariable, outputs, new String(responseBody, java.nio.charset.StandardCharsets.UTF_8));
            return ActionResult.success(outputs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ActionResult.failure("HTTP request was interrupted");
        } catch (Exception e) {
            return ActionResult.failure(e.getMessage() == null ? "HTTP request failed" : e.getMessage());
        }
    }

    private void putOutput(ActionContext context, Map<String, Object> outputs, String configKey, Object value) {
        String name = optionalString(context.getConfig(configKey));
        if (name != null) putOutputName(name, outputs, value);
    }

    private void putOutputName(String name, Map<String, Object> outputs, Object value) {
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException("HTTP output variable must be a top-level field identifier");
        outputs.put(name, value);
    }

    private String requiredString(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank())
            throw new IllegalArgumentException("w6w.http.request requires config." + field);
        return text;
    }

    private String optionalString(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private int integer(Object value, int fallback) {
        if (value == null) return fallback;
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue())
            throw new IllegalArgumentException("w6w.http.request timeoutSeconds must be an integer");
        return number.intValue();
    }
}
