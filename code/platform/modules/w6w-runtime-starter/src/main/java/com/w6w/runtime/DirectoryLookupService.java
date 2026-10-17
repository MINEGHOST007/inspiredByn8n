package com.w6w.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component("w6wDirectoryService")
public class DirectoryLookupService {
    private static final Logger log = LoggerFactory.getLogger(DirectoryLookupService.class);
    private final Map<String, Function<Object, String>> lookupHandlers = new ConcurrentHashMap<>();

    public DirectoryLookupService() {
        // Default resolver for tests & local development
        lookupHandlers.put("managerOfRequester", exec -> "manager.jones");
    }

    public void registerLookup(String name, Function<Object, String> resolver) {
        lookupHandlers.put(name, resolver);
        log.info("Registered dynamic directory lookup resolver for '{}'", name);
    }

    public String lookup(String lookupName, Object execution) {
        Function<Object, String> resolver = lookupHandlers.get(lookupName);
        if (resolver != null) {
            String resolvedUser = resolver.apply(execution);
            log.info("Resolved dynamic lookup '{}' -> '{}'", lookupName, resolvedUser);
            return resolvedUser;
        }
        log.warn("No custom resolver for lookup '{}', returning fallback default 'manager.user'", lookupName);
        return "manager.user";
    }
}
