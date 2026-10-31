package com.fintech.engine.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fintech Payment & Double-Entry Ledger Engine API")
                        .version("1.0.0")
                        .description("Production-grade fintech backend providing double-entry bookkeeping, " +
                                "atomic peer-to-peer transfers, strict idempotency enforcement, and real-time ledger balance derivation.")
                        .contact(new Contact()
                                .name("Engineering Team")
                                .email("dev@fintech.example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Server")
                ));
    }
}
