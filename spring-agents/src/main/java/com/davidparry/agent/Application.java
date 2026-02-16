package com.davidparry.agent;

import com.davidparry.agent.config.AdminProperties;
import com.davidparry.agent.config.JwtProperties;
import com.davidparry.agent.config.McpProxyProperties;
import com.davidparry.agent.config.TokenHashingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties({McpProxyProperties.class, TokenHashingProperties.class, JwtProperties.class, AdminProperties.class})
public final class Application {

    private Application() {
        // Spring Boot application entry point
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
