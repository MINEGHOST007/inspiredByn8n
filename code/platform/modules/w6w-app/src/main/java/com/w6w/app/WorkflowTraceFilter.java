package com.w6w.app;

import com.w6w.runtime.WorkflowMdcContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates or creates a W3C trace context and adds it to response headers and log MDC. */
@Component
public class WorkflowTraceFilter extends OncePerRequestFilter {
    private static final Pattern TRACE_PARENT = Pattern.compile("^00-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-([0-9a-fA-F]{2})$");
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        TraceContext trace = traceContext(request.getHeader("traceparent"));
        String[] pathIds = pathIdentifiers(request.getRequestURI());
        response.setHeader("traceparent", "00-" + trace.traceId() + "-" + trace.spanId() + "-" + trace.flags());
        response.setHeader("X-W6W-Trace-Id", trace.traceId());
        try (var ignored = WorkflowMdcContext.open(trace.traceId(), pathIds[0], pathIds[1], null)) {
            filterChain.doFilter(request, response);
        }
    }

    static TraceContext traceContext(String incoming) {
        if (incoming != null) {
            Matcher matcher = TRACE_PARENT.matcher(incoming.trim());
            if (matcher.matches()) {
                String traceId = matcher.group(1).toLowerCase(Locale.ROOT);
                String parentId = matcher.group(2).toLowerCase(Locale.ROOT);
                if (!allZero(traceId) && !allZero(parentId))
                    return new TraceContext(traceId, newId().substring(0, 16), matcher.group(3).toLowerCase(Locale.ROOT));
            }
        }
        return new TraceContext(newId(), newId().substring(0, 16), "00");
    }

    private static String[] pathIdentifiers(String path) {
        String[] segments = path == null ? new String[0] : path.split("/");
        for (int i = 0; i + 1 < segments.length; i++) {
            if (("instances".equals(segments[i]) || "tasks".equals(segments[i]))
                    && SAFE_ID.matcher(segments[i + 1]).matches()) {
                if ("instances".equals(segments[i])) return new String[]{segments[i + 1], null};
                return new String[]{null, segments[i + 1]};
            }
        }
        return new String[]{null, null};
    }

    private static boolean allZero(String value) { return value.chars().allMatch(character -> character == '0'); }
    private static String newId() { return UUID.randomUUID().toString().replace("-", ""); }

    record TraceContext(String traceId, String spanId, String flags) { }
}
