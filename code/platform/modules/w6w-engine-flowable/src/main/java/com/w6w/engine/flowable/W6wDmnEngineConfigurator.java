package com.w6w.engine.flowable;

import org.flowable.dmn.engine.DmnEngine;
import org.flowable.dmn.engine.configurator.DmnEngineConfigurator;

/** Exposes the DMN engine attached to the process engine for catalog deployment and delegates. */
public class W6wDmnEngineConfigurator extends DmnEngineConfigurator {
    public DmnEngine getConfiguredDmnEngine() { return buildEngine; }
}
