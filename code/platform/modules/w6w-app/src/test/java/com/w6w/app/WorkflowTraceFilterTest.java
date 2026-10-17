package com.w6w.app;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowTraceFilterTest {
    private final WorkflowTraceFilter filter = new WorkflowTraceFilter();

    @Test
    void continuesValidW3cTraceAndAddsRequestIdentifiersToLogContext() throws Exception {
        String incomingTraceId = "0af7651916cd43dd8448eb211c80319c";
        String incomingSpanId = "b7ad6b7169203331";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tasks/task-123");
        request.addHeader("traceparent", "00-" + incomingTraceId + "-" + incomingSpanId + "-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceDuringRequest = new AtomicReference<>();
        AtomicReference<String> taskDuringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            traceDuringRequest.set(MDC.get("traceId"));
            taskDuringRequest.set(MDC.get("taskId"));
        });

        assertThat(traceDuringRequest.get()).isEqualTo(incomingTraceId);
        assertThat(taskDuringRequest.get()).isEqualTo("task-123");
        assertThat(response.getHeader("X-W6W-Trace-Id")).isEqualTo(incomingTraceId);
        assertThat(response.getHeader("traceparent")).matches("00-" + incomingTraceId + "-[0-9a-f]{16}-01");
        assertThat(response.getHeader("traceparent")).doesNotContain(incomingSpanId);
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("taskId")).isNull();
    }

    @Test
    void invalidOrMissingTraceParentCreatesFreshNonzeroContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/instances");
        request.addHeader("traceparent", "00-00000000000000000000000000000000-0000000000000000-01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        String traceId = response.getHeader("X-W6W-Trace-Id");
        assertThat(traceId).matches(Pattern.compile("[0-9a-f]{32}"));
        assertThat(traceId).isNotEqualTo("00000000000000000000000000000000");
        assertThat(response.getHeader("traceparent")).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-00");
    }
}
