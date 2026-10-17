package com.w6w.runtime.actions;

import com.w6w.port.action.ActionContext;
import com.w6w.runtime.SecretProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves action values from constants, workflow variables, or host-managed secret references. */
final class ActionValueResolver {
    private ActionValueResolver() {}

    static Object resolve(Object value, ActionContext context, SecretProvider secrets) {
        if (value instanceof Map<?, ?> map) {
            if (map.size() == 1 && map.get("from") instanceof String path) return lookup(context, path);
            if (map.size() == 1 && map.get("secretRef") instanceof String reference) return secrets.getSecret(reference);
            Map<String, Object> resolved = new LinkedHashMap<>();
            map.forEach((key, item) -> resolved.put(String.valueOf(key), resolve(item, context, secrets)));
            return resolved;
        }
        if (value instanceof java.util.List<?> list)
            return list.stream().map(item -> resolve(item, context, secrets)).toList();
        return value;
    }

    static Map<String, Object> resolveObject(Object value, ActionContext context, SecretProvider secrets, String label) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(label + " must be an object");
        Object resolved = resolve(map, context, secrets);
        @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) resolved;
        return result;
    }

    private static Object lookup(ActionContext context, String path) {
        if (path == null || !path.matches("[A-Za-z_][A-Za-z0-9_.-]*"))
            throw new IllegalArgumentException("Variable reference must be a field path");
        Object value = context.getVariables();
        for (String segment : path.split("\\.")) {
            if (!(value instanceof Map<?, ?> map) || !map.containsKey(segment))
                throw new IllegalArgumentException("Workflow variable does not exist: " + path);
            value = map.get(segment);
        }
        return value;
    }
}
