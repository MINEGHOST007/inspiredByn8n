package com.w6w.app;

import org.springframework.stereotype.Component;

/** Resolves configured elapsed-time deadlines when a Flowable human task is created. */
@Component("w6wSlaService")
public class SlaService extends com.w6w.runtime.SlaDeadlineService { }
