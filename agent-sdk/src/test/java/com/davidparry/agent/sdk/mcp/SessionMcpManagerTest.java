package com.davidparry.agent.sdk.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SessionMcpManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void initialize_withoutServers_marksInitializedAndCreatesWorkspace() {
        SessionMcpManager manager = new SessionMcpManager(
            "session-1",
            new ObjectMapper(),
            Duration.ofSeconds(2),
            null,
            tempDir.toString()
        );

        manager.initialize();

        assertTrue(manager.isInitialized());
        assertFalse(manager.isShutdown());
        assertEquals(0, manager.getServerCount());
        assertEquals(0, manager.getToolCount());
        assertTrue(Files.exists(manager.getSessionWorkspacePath()));
        assertEquals(tempDir.toString(), manager.getSandboxBasePath());
    }

    @Test
    void initialize_calledTwice_isSafeAndIdempotent() {
        SessionMcpManager manager = new SessionMcpManager(
            "session-2",
            new ObjectMapper(),
            Duration.ofSeconds(2),
            null,
            tempDir.toString()
        );

        manager.initialize();
        manager.initialize();

        assertTrue(manager.isInitialized());
        assertEquals(0, manager.getServerCount());
    }

    @Test
    void executeTool_throwsWhenNotInitialized() {
        SessionMcpManager manager = new SessionMcpManager(
            "session-3",
            new ObjectMapper(),
            Duration.ofSeconds(2),
            null,
            tempDir.toString()
        );

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> manager.executeTool("missing-tool", Map.of())
        );

        assertTrue(ex.getMessage().contains("not initialized"));
    }

    @Test
    void executeTool_throwsAfterShutdown() {
        SessionMcpManager manager = new SessionMcpManager(
            "session-4",
            new ObjectMapper(),
            Duration.ofSeconds(2),
            null,
            tempDir.toString()
        );
        manager.initialize();
        manager.shutdown();

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> manager.executeTool("missing-tool", Map.of())
        );

        assertTrue(ex.getMessage().contains("has been shutdown"));
    }

    @Test
    void shutdown_deletesWorkspaceDirectory() throws Exception {
        SessionMcpManager manager = new SessionMcpManager(
            "session-5",
            new ObjectMapper(),
            Duration.ofSeconds(2),
            null,
            tempDir.toString()
        );
        manager.initialize();
        Path nestedFile = manager.getSessionWorkspacePath().resolve("nested.txt");
        Files.writeString(nestedFile, "content");
        assertTrue(Files.exists(nestedFile));

        manager.shutdown();

        assertTrue(manager.isShutdown());
        assertFalse(Files.exists(manager.getSessionWorkspacePath()));
    }

    @Test
    void lookups_returnEmptyWhenNoServersOrToolsRegistered() {
        SessionMcpManager manager = new SessionMcpManager(
            "session-6",
            new ObjectMapper(),
            Duration.ofSeconds(2),
            null,
            tempDir.toString()
        );
        manager.initialize();

        assertTrue(manager.getAvailableTools().isEmpty());
        assertTrue(manager.getAvailableToolsAsMcpTools().isEmpty());
        assertTrue(manager.getRunningServers().isEmpty());
        assertTrue(manager.getServerConfig("missing").isEmpty());
        assertTrue(manager.getServerForTool("missing-tool").isEmpty());
        assertTrue(manager.getTool("missing-tool").isEmpty());
        assertTrue(manager.getToolsForServer("missing-server").isEmpty());
    }
}
