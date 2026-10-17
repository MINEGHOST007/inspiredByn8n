package com.w6w.app;

import com.w6w.engine.flowable.W6wDmnEngineConfigurator;
import com.w6w.engine.flowable.FlowableLifecycleEventListener;
import org.flowable.dmn.engine.DmnEngine;
import org.flowable.dmn.engine.DmnEngineConfiguration;
import org.flowable.dmn.engine.configurator.DmnEngineConfigurator;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;

import java.util.ArrayList;

import javax.sql.DataSource;

@Configuration
public class DmnEngineIntegrationConfiguration {
    @Bean
    W6wDmnEngineConfigurator w6wDmnEngineConfigurator(DataSource dataSource) {
        DmnEngineConfiguration configuration = DmnEngineConfiguration.createStandaloneDmnEngineConfiguration();
        configuration.setDataSource(dataSource);
        configuration.setDatabaseSchemaUpdate(DmnEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        return (W6wDmnEngineConfigurator) new W6wDmnEngineConfigurator().setDmnEngineConfiguration(configuration);
    }

    @Bean
    ProcessEngineConfigurationConfigurer processEngineDmnConfigurer(W6wDmnEngineConfigurator configurator,
                                                                     FlowableLifecycleEventListener lifecycleEvents) {
        return processEngineConfiguration -> {
            processEngineConfiguration.addConfigurator(configurator);
            ArrayList<FlowableEventListener> listeners = new ArrayList<>();
            if (processEngineConfiguration.getEventListeners() != null)
                listeners.addAll(processEngineConfiguration.getEventListeners());
            listeners.add(lifecycleEvents);
            processEngineConfiguration.setEventListeners(listeners);
        };
    }

    @Bean
    DmnEngine w6wDmnEngine(ProcessEngine processEngine, W6wDmnEngineConfigurator configurator) {
        DmnEngine engine = configurator.getConfiguredDmnEngine();
        if (engine == null) throw new IllegalStateException("Flowable DMN configurator did not initialize with the process engine");
        return engine;
    }
}
