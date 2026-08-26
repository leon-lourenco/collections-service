package com.cardbilling.collections.infrastructure.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI metadata, including the Bearer token every endpoint here requires. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI collectionsServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("collections-service")
                        .version("0.1.0")
                        .description(
                                "Delinquency escalation and interest accrual for card-billing-modernization. "
                                        + "Owns no database: reads overdue invoices from billing-service and calls "
                                        + "back into billing-service and notification-service to act on them."))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearer-jwt",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
