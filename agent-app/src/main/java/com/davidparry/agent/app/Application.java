package com.davidparry.agent.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Agent Application - Spring Boot consumer of the agent-sdk library.
 *
 * This application acts as a bridge between local STDIO-based MCP servers
 * and a remote main server via WebSocket connections.
 */
@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
