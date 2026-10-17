package com.w6w.app;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI w6wOpenApi() {
        return new OpenAPI().info(new Info()
                .title("W6W Workflow API")
                .version("1.0.0")
                .description("Versioned local integration API for the W6W workflow workspace."));
    }
}
