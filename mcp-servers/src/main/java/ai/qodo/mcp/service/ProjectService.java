/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp.service;

import ai.qodo.mcp.config.McpToolsConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service()
@ConditionalOnProperty(name = "mcp.project.enabled", havingValue = "true", matchIfMissing = true)
public class ProjectService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);
    private final McpToolsConfiguration mcpToolsConfiguration;

    public ProjectService(McpToolsConfiguration mcpToolsConfiguration) {
        this.mcpToolsConfiguration = mcpToolsConfiguration;
    }

    protected static String buildDirectoryTree(Path root) {
        StringBuilder sb = new StringBuilder();
        try {
            buildTree(sb, root, "", true);
        } catch (IOException | UncheckedIOException er) {
            logger.error("Failure building directory tree for path {}", root, er);
        }
        return sb.toString();
    }

    protected static void buildTree(StringBuilder sb, Path path, String prefix, boolean isLast) throws IOException {
        sb.append(prefix).append(isLast ? "└── " : "├── ").append(path.getFileName()).append(System.lineSeparator());

        if (Files.isDirectory(path)) {
            List<Path> children = Files
                    .list(path)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .collect(Collectors.toList());
            for (int i = 0; i < children.size(); i++) {
                buildTree(sb, children.get(i), prefix + (isLast ? "    " : "│   "), i == children.size() - 1);
            }
        }
    }

    @Tool(name = "get_project_tree", description = "Returns a hierarchical tree view of the project's directory structure, " +
            "showing all files and folders in a visual tree format. Use this to understand project organization, " +
            "locate files, explore the codebase layout, or get an overview of the project architecture.")
    public String projectStructure(ToolContext toolContext) throws InterruptedException {
        mcpToolsConfiguration.ensureRootsInitialized(toolContext);
        String path = mcpToolsConfiguration.getDefaultLocalPath();
        if (path != null && !path.isBlank()) {
            return buildDirectoryTree(Path.of(path));
        } else {
            return "/";
        }
    }
}
