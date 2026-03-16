package com.davidparry.agent.sdk.mcp;

import com.davidparry.agent.protocol.mcp.McpConfig;
import com.davidparry.agent.protocol.mcp.StdioServer;
import com.davidparry.agent.sdk.config.AgentSdkProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class McpServerManagerTest {

    @TempDir
    Path tempDir;

    private AgentSdkProperties properties(String sandboxPath) {
        return new AgentSdkProperties(
            "api-key",
            "ws://localhost:8080/mcp-proxy",
            new AgentSdkProperties.ConnectionConfig(30, 30, 300, AgentSdkProperties.DEFAULT_TEXT_MESSAGE_BUFFER_SIZE),
            new AgentSdkProperties.ReconnectConfig(true, 3, 100, 1000, 2.0),
            new AgentSdkProperties.ChainConfig(),
            "agent.yml",
            new AgentSdkProperties.McpConfig(5, sandboxPath)
        );
    }

    @Test
    void init_createsSandboxDirectoryWhenMissing() {
        Path missingSandbox = tempDir.resolve("sandbox-root");
        McpServerManager manager = new McpServerManager(properties(missingSandbox.toString()), new ObjectMapper());

        assertFalse(Files.exists(missingSandbox));
        manager.init();

        assertTrue(Files.exists(missingSandbox));
        assertTrue(Files.isDirectory(missingSandbox));
        assertEquals(missingSandbox.toString(), manager.getSandboxBasePath());
    }

    @Test
    void init_throwsWhenSandboxPathIsAFile() throws Exception {
        Path filePath = tempDir.resolve("not-a-directory.txt");
        Files.writeString(filePath, "x");
        McpServerManager manager = new McpServerManager(properties(filePath.toString()), new ObjectMapper());

        IllegalStateException ex = assertThrows(IllegalStateException.class, manager::init);
        assertTrue(ex.getMessage().contains("not a directory"));
    }

    @Test
    void createDestroySessionManager_tracksLifecycleAndWorkspace() {
        McpServerManager manager = new McpServerManager(properties(tempDir.toString()), new ObjectMapper());
        manager.init();

        SessionMcpManager session = manager.createSessionManager("session-1");

        assertTrue(manager.hasSessionManager("session-1"));
        assertEquals(1, manager.getActiveSessionCount());
        assertTrue(manager.getSessionManager("session-1").isPresent());
        assertTrue(session.isInitialized());
        assertTrue(Files.exists(session.getSessionWorkspacePath()));

        manager.destroySessionManager("session-1");

        assertFalse(manager.hasSessionManager("session-1"));
        assertEquals(0, manager.getActiveSessionCount());
        assertFalse(Files.exists(session.getSessionWorkspacePath()));
    }

    @Test
    void executeTool_throwsWhenSessionDoesNotExist() {
        McpServerManager manager = new McpServerManager(properties(tempDir.toString()), new ObjectMapper());

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> manager.executeTool("missing-session", "tool", Map.of())
        );
        assertTrue(ex.getMessage().contains("No session manager found"));
    }

    @Test
    void loadFromConfig_supportsNullAndPopulatedConfig() {
        McpServerManager manager = new McpServerManager(properties(tempDir.toString()), new ObjectMapper());

        manager.loadFromConfig(null);
        assertFalse(manager.hasConfiguration());
        assertEquals(0, manager.getConfiguredServerCount());
        assertTrue(manager.getConfiguredServers().isEmpty());

        McpConfig config = new McpConfig(Map.of(
            "local-tools",
            new StdioServer("local-tools", "stdio", "echo", List.of("ok"), Map.of())
        ));
        manager.loadFromConfig(config);

        assertTrue(manager.hasConfiguration());
        assertEquals(1, manager.getConfiguredServerCount());
        assertEquals(Set.of("local-tools"), manager.getConfiguredServers());
    }

    @Test
    void shutdown_stopsAndClearsAllSessionManagers() {
        McpServerManager manager = new McpServerManager(properties(tempDir.toString()), new ObjectMapper());
        manager.init();
        SessionMcpManager sessionA = manager.createSessionManager("a");
        SessionMcpManager sessionB = manager.createSessionManager("b");

        manager.shutdown();

        assertEquals(0, manager.getActiveSessionCount());
        assertTrue(sessionA.isShutdown());
        assertTrue(sessionB.isShutdown());
    }
}
