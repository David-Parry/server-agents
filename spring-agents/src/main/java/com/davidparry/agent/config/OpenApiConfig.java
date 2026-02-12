package com.davidparry.agent.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;



/**
 * OpenAPI/Swagger configuration for the Spring Agents API.
 * 
 * Access the Swagger UI at: /swagger-ui.html
 * Access the OpenAPI spec at: /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI springAgentsOpenAPI() {
        return new OpenAPI()
            .info(apiInfo())
            .servers(List.of(
                new Server()
                    .url("http://localhost:" + serverPort)
                    .description("Local Development Server")
            ))
            .tags(List.of(
                new Tag()
                    .name("Admin - Customers")
                    .description("Administrative operations for customer management"),
                new Tag()
                    .name("Admin - Models")
                    .description("Administrative operations for LLM model management"),
                new Tag()
                    .name("Admin - Policy Types")
                    .description("Administrative operations for policy type management"),
                new Tag()
                    .name("Admin - Allowances")
                    .description("Administrative operations for token allowance management"),
                new Tag()
                    .name("Admin - Audit")
                    .description("Administrative operations for audit log management"),
                new Tag()
                    .name("Admin - Statistics")
                    .description("System statistics and metrics"),
                new Tag()
                    .name("Customers")
                    .description("Customer management operations"),
                new Tag()
                    .name("Models")
                    .description("LLM model management operations")
            ))
            .components(new Components()
                .addSecuritySchemes("AdminBearerAuth", adminSecurityScheme())
                .addSecuritySchemes("CustomerApiKey", customerApiKeyScheme())
            )
            .addSecurityItem(new SecurityRequirement().addList("AdminBearerAuth"));
    }

    private Info apiInfo() {
        return new Info()
            .title("Spring Agents API")
            .description("""
                REST API for the Spring Agents MCP Proxy service.
                
                ## Authentication
                
                ### Admin API (`/api/admin/**`)
                Admin endpoints require a Bearer token in the Authorization header:
                ```
                Authorization: Bearer <admin-token>
                ```
                The admin token is configured via the `AGENT_ADMIN_API_TOKEN` environment variable.
                
                ### Customer API (`/api/customers/**`, `/api/models/**`)
                Customer endpoints are currently open but may require authentication in production.
                
                ### WebSocket API (`/agent`)
                WebSocket connections require a customer JWT token via the `X-API-Key` header or `apiKey` query parameter.
                
                ## Rate Limiting
                REST endpoints have no rate limiting. Token usage limits are enforced per-customer per-model.
                """)
            .version("1.0.0")
            .contact(new Contact()
                .name("Spring Agents Team")
                .email("support@example.com"))
            .license(new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0"));
    }

    private SecurityScheme adminSecurityScheme() {
        return new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("token")
            .description("Admin API token for administrative operations. " +
                        "Set via AGENT_ADMIN_API_TOKEN environment variable.");
    }

    private SecurityScheme customerApiKeyScheme() {
        return new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .in(SecurityScheme.In.HEADER)
            .name("X-API-Key")
            .description("Customer JWT token for WebSocket authentication.");
    }
}
