package com.davidparry.agent.sdk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Agent Client Application - Bridge Proxy for MCP Server Communication.
 * 
 * This application acts as a bridge between local STDIO-based MCP servers
 * and a remote main server via WebSocket connections.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AgentSdkApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentSdkApplication.class, args);
    }
}
