/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp.config;

import ai.qodo.mcp.service.*;
import ai.qodo.mcp.service.AzureDevOpsService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.swing.text.html.Option;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Unified MCP Tools Configuration.
 * <p>
 * This configuration combines all available MCP tools (Git, Jira, etc.) into a single
 * List<ToolCallback> bean that Spring AI MCP Server can consume.
 * <p>
 * Each service's tools are conditionally included based on their respective
 * configuration properties.
 */
@Configuration
public class McpToolsConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(McpToolsConfiguration.class);

    private final CountDownLatch rootsLatch = new CountDownLatch(1);
    private String defaultLocalPath;
    private boolean rootsInitialized = false;
    private McpSyncServerExchange serverExchange;


    public String getDefaultLocalPath() {
        return this.defaultLocalPath;
    }

    public boolean isRootsInitialized() {
        return rootsInitialized;
    }

    public CountDownLatch getRootsLatch() {
        return this.rootsLatch;
    }

    /**
     * Sets the server exchange instance for communicating with the client.
     * This should be called when the exchange becomes available.
     */
    public void setServerExchange(McpSyncServerExchange exchange) {
        this.serverExchange = exchange;
        logger.info("Server exchange set, ready to communicate with client");
    }

    /**
     * Initializes the root path from the provided roots.
     * Takes the first root and converts it to an absolute file path.
     */
    protected void initRootPath(List<McpSchema.Root> roots) {
        McpSchema.Root firstRoot = roots.getFirst();
        String uri = firstRoot.uri();
        if (uri.startsWith("file://")) {
            uri = uri.substring(7);
        }
        if (uri.startsWith("//") && !uri.startsWith("///")) {
            uri = uri.substring(1);
        }
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        this.defaultLocalPath = new File(uri).getAbsolutePath();
        rootsInitialized = true;
        rootsLatch.countDown();
        logger.info("Root path initialized: {}", this.defaultLocalPath);
    }

    /**
     * Requests roots from the MCP client.
     * This sends a roots/list request to the client and waits for the response.
     * The response will be handled by the rootsChangeHandler bean.
     */
    public void requestRootsFromClient() {
        if (serverExchange == null) {
            logger.warn("Cannot request roots: server exchange not available");
            return;
        }

        try {
            logger.info("Requesting roots from client...");
            McpSchema.ListRootsResult roots = serverExchange.listRoots();

            if (roots != null && !roots.roots().isEmpty()) {
                logger.info("Received {} root(s) from client", roots.roots().size());
                initRootPath(roots.roots());
            } else {
                logger.warn("Client returned empty roots list");
            }
        } catch (Exception e) {
            logger.error("Error requesting roots from client", e);
        }
    }

    /**
     * Ensures roots are initialized before proceeding with operations.
     * This method blocks until roots are received or timeout occurs.
     *
     * @param toolContext The tool context containing the MCP exchange
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void ensureRootsInitialized(ToolContext toolContext) throws InterruptedException {
        if (!this.rootsInitialized) {
            Optional<McpSyncServerExchange> exchange = McpToolUtils.getMcpExchange(toolContext);
            if (exchange.isPresent()) {
                logger.info("Got exchange from tool context, requesting roots");
                setServerExchange(exchange.get());
                requestRootsFromClient();
            }

            logger.info("Waiting for roots to be initialized...");
            boolean received = this.rootsLatch.await(90, TimeUnit.SECONDS);
            if (!received) {
                logger.warn("Timeout waiting for roots. Proceeding without default path.");
            }
        }
    }

    /**
     * Bean that handles roots changes from the MCP client.
     * This is called when the client provides or updates roots information.
     */
    @Bean
    @Primary
    public BiConsumer<McpSyncServerExchange, List<McpSchema.Root>> rootsChangeHandler() {
        logger.info("Registering centralized rootsChangeHandler");

        return (exchange, roots) -> {
            setServerExchange(exchange);
            logger.info("Server exchange stored in centralized configuration");

            // If roots are provided, use them
            if (roots != null && !roots.isEmpty()) {
                logger.info("Using {} provided roots", roots.size());
                initRootPath(roots);
            }
            // If no roots provided, proactively request them from the client
            else {
                logger.warn("No roots provided, requesting from client...");
                requestRootsFromClient();
            }
        };
    }

    /**
     * Creates a unified list of all available MCP tools.
     * This bean is marked as @Primary to resolve the ambiguity when multiple
     * List<ToolCallback> beans exist.
     * <p>
     * Services are injected as Optional to handle cases where they're disabled
     * via configuration properties.
     *
     * @param gitService           Optional GitService (only present if mcp.git.enabled=true)
     * @param jiraService          Optional JiraService (only present if mcp.jira.enabled=true)
     * @param jiraMcpConfiguration JiraMcpConfiguration for validation
     * @return Combined list of all available tool callbacks
     */
    @Bean
    @Primary
    public List<ToolCallback> allMcpTools(Optional<GitService> gitService, Optional<JiraService> jiraService,
                                          Optional<TerminalService> terminalService,
                                          JiraMcpConfiguration jiraMcpConfiguration,
                                          GithubMcpConfiguration githubMcpConfiguration,
                                          Optional<GitHubService> gitHubService, 
                                          ConfluenceMcpConfiguration confluenceMcpConfiguration,
                                          Optional<ConfluenceService> confluenceService,
                                          AzureDevOpsMcpConfiguration azureDevOpsMcpConfiguration,
                                          Optional<AzureDevOpsService> azureDevOpsService) {

        List<ToolCallback> allTools = new ArrayList<>();

        // Add Git tools if GitService is available
        gitService.ifPresent(service -> {
            logger.info("Adding Git tools to MCP server");
            allTools.addAll(Arrays.asList(ToolCallbacks.from(service)));
        });

        // Add Jira tools if JiraService is available and properly configured
        jiraService.ifPresent(service -> {
            if (jiraMcpConfiguration.isConfigurationValid()) {
                logger.info("Adding Jira tools to MCP server");
                allTools.addAll(Arrays.asList(ToolCallbacks.from(service)));
            } else {
                logger.warn("Jira service is available but configuration is invalid. Skipping Jira tools.");
            }
        });

        terminalService.ifPresent(service -> {
            logger.info("Adding Terminal tools to MCP server");
            allTools.addAll(Arrays.asList(ToolCallbacks.from(service)));

        });

        gitHubService.ifPresent(service -> {
            if (githubMcpConfiguration.isConfigurationValid()) {
                logger.info("Adding Github tools to MCP server");
                allTools.addAll(Arrays.asList(ToolCallbacks.from(service)));
            } else {
                logger.warn("Github service is available but configuration is invalid. Skipping Github tools.");
            }
        });

        confluenceService.ifPresent(service -> {
            if (confluenceMcpConfiguration.isConfigurationValid()) {
                logger.info("Adding Confluence tools to MCP server");
                allTools.addAll(Arrays.asList(ToolCallbacks.from(service)));
            } else {
                logger.warn("Confluence service is available but configuration is invalid. Skipping Confluence tools.");
            }
        });

        azureDevOpsService.ifPresent(service -> {
            if (azureDevOpsMcpConfiguration.isConfigurationValid()) {
                logger.info("Adding Azure DevOps tools to MCP server");
                allTools.addAll(Arrays.asList(ToolCallbacks.from(service)));
            } else {
                logger.warn("Azure DevOps service is available but configuration is invalid. Skipping Azure DevOps tools.");
            }
        });

        logger.info("Total MCP tools registered: {}", allTools.size());
        return allTools;
    }
}
