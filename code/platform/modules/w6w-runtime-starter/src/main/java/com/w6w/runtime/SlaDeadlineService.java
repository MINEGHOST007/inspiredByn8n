package com.w6w.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** Resolves elapsed-time SLA deadlines when Flowable creates a human task. */
public class SlaDeadlineService {
    public Date deadline(String iso8601Duration) {
        return Date.from(Instant.now().plus(Duration.parse(iso8601Duration)));
    }
}
