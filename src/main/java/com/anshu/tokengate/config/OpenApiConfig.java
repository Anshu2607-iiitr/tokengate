package com.anshu.tokengate.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tokenGateOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TokenGate API")
                        .version("1.0")
                        .description("LLM API gateway with per-team API keys, rate limiting, response caching, "
                                + "daily budgets, multi-provider fallback and Kafka-based cost analytics."))
                .components(new Components()
                        .addSecuritySchemes("teamApiKey", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("Team API key (tg_...) for /v1 endpoints"))
                        .addSecuritySchemes("adminToken", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Admin-Token")
                                .description("Admin token for /admin endpoints")))
                .addSecurityItem(new SecurityRequirement().addList("teamApiKey"))
                .addSecurityItem(new SecurityRequirement().addList("adminToken"));
    }
}