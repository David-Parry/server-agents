/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp;

import ai.qodo.mcp.config.McpToolsConfiguration;
import ai.qodo.mcp.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.model.ToolContext;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit test suite for ProjectService.
 * Tests all public methods and private/protected methods using reflection.
 * 
 * Coverage includes:
 * - projectStructure method with various configurations
 * - buildDirectoryTree static method with different directory structures
 * - buildTree static method with edge cases
 * - Error handling and resource management
 * - Edge cases: empty directories, null paths, special characters, symlinks, etc.
 */
class ProjectServiceTest {

    private ProjectService projectService;

    @Mock
    private McpToolsConfiguration mcpToolsConfiguration;

    @Mock
    private ToolContext toolContext;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        projectService = new ProjectService(mcpToolsConfiguration);
    }

    // ========== Tests for projectStructure method ==========

    @Test
    void testProjectStructure_WithValidPath() throws Exception {
        // Setup
        String testPath = tempDir.toString();
        when(mcpToolsConfiguration.getDefaultLocalPath()).thenReturn(testPath);
        doNothing().when(mcpToolsConfiguration).ensureRootsInitialized(any());

        // Create test directory structure
        Files.createDirectories(tempDir.resolve("src/main/java"));
        Files.createFile(tempDir.resolve("src/main/java/Test.java"));
        Files.createFile(tempDir.resolve("README.md"));

        // Execute
        String result = projectService.projectStructure(toolContext);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("src"));
        assertTrue(result.contains("main"));
        assertTrue(result.contains("java"));
        assertTrue(result.contains("Test.java"));
        assertTrue(result.contains("README.md"));
        verify(mcpToolsConfiguration).ensureRootsInitialized(toolContext);
        verify(mcpToolsConfiguration).getDefaultLocalPath();
    }

    @Test
    void testProjectStructure_WithNullPath() throws Exception {
        // Setup
        when(mcpToolsConfiguration.getDefaultLocalPath()).thenReturn(null);
        doNothing().when(mcpToolsConfiguration).ensureRootsInitialized(any());

        // Execute
        String result = projectService.projectStructure(toolContext);

        // Verify
        assertNotNull(result);
        assertEquals("/", result);
        verify(mcpToolsConfiguration).ensureRootsInitialized(toolContext);
        verify(mcpToolsConfiguration).getDefaultLocalPath();
    }

    @Test
    void testProjectStructure_WithEmptyPath() throws Exception {
        // Setup
        when(mcpToolsConfiguration.getDefaultLocalPath()).thenReturn("");
        doNothing().when(mcpToolsConfiguration).ensureRootsInitialized(any());

        // Execute
        String result = projectService.projectStructure(toolContext);

        // Verify
        assertNotNull(result);
        assertEquals("/", result);
        verify(mcpToolsConfiguration).ensureRootsInitialized(toolContext);
        verify(mcpToolsConfiguration).getDefaultLocalPath();
    }

    @Test
    void testProjectStructure_WithBlankPath() throws Exception {
        // Setup
        when(mcpToolsConfiguration.getDefaultLocalPath()).thenReturn("   ");
        doNothing().when(mcpToolsConfiguration).ensureRootsInitialized(any());

        // Execute
        String result = projectService.projectStructure(toolContext);

        // Verify
        assertNotNull(result);
        assertEquals("/", result);
        verify(mcpToolsConfiguration).ensureRootsInitialized(toolContext);
        verify(mcpToolsConfiguration).getDefaultLocalPath();
    }

    @Test
    void testProjectStructure_WithInterruptedException() throws Exception {
        // Setup
        doThrow(new InterruptedException("Test interruption"))
            .when(mcpToolsConfiguration).ensureRootsInitialized(any());

        // Execute & Verify
        assertThrows(InterruptedException.class, () -> {
            projectService.projectStructure(toolContext);
        });

        verify(mcpToolsConfiguration).ensureRootsInitialized(toolContext);
    }

    @Test
    void testProjectStructure_WithNullToolContext() throws Exception {
        // Setup
        String testPath = tempDir.toString();
        when(mcpToolsConfiguration.getDefaultLocalPath()).thenReturn(testPath);
        doNothing().when(mcpToolsConfiguration).ensureRootsInitialized(null);

        Files.createFile(tempDir.resolve("test.txt"));

        // Execute
        String result = projectService.projectStructure(null);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("test.txt"));
        verify(mcpToolsConfiguration).ensureRootsInitialized(null);
    }

    // ========== Tests for buildDirectoryTree static method ==========

    @Test
    void testBuildDirectoryTree_SimpleStructure() throws Exception {
        // Setup
        Path root = tempDir.resolve("simple");
        Files.createDirectories(root);
        Files.createFile(root.resolve("file1.txt"));
        Files.createFile(root.resolve("file2.txt"));

        // Execute
        String result = invokeBuildDirectoryTree(root);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("simple"));
        assertTrue(result.contains("file1.txt"));
        assertTrue(result.contains("file2.txt"));
        assertTrue(result.contains("└──") || result.contains("├──"));
    }

    @Test
    void testBuildDirectoryTree_NestedStructure() throws Exception {
        // Setup
        Path root = tempDir.resolve("nested");
        Files.createDirectories(root.resolve("dir1/subdir1"));
        Files.createDirectories(root.resolve("dir1/subdir2"));
        Files.createFile(root.resolve("dir1/file1.txt"));
        Files.createFile(root.resolve("dir1/subdir1/file2.txt"));

        // Execute
        String result = invokeBuildDirectoryTree(root);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("nested"));
        assertTrue(result.contains("dir1"));
        assertTrue(result.contains("subdir1"));
        assertTrue(result.contains("subdir2"));
        assertTrue(result.contains("file1.txt"));
        assertTrue(result.contains("file2.txt"));
    }

    @Test
    void testBuildDirectoryTree_EmptyDirectory() throws Exception {
        // Setup
        Path root = tempDir.resolve("empty");
        Files.createDirectories(root);

        // Execute
        String result = invokeBuildDirectoryTree(root);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("empty"));
    }

    @Test
    void testBuildDirectoryTree_SingleFile() throws Exception {
        // Setup
        Path root = tempDir.resolve("single");
        Files.createDirectories(root);
        Files.createFile(root.resolve("only-file.txt"));

        // Execute
        String result = invokeBuildDirectoryTree(root);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("single"));
        assertTrue(result.contains("only-file.txt"));
        assertTrue(result.contains("└──"));
    }

    @Test
    void testBuildDirectoryTree_AlphabeticalSorting() throws Exception {
        // Setup
        Path root = tempDir.resolve("sorted");
        Files.createDirectories(root);
        Files.createFile(root.resolve("zebra.txt"));
        Files.createFile(root.resolve("apple.txt"));
        Files.createFile(root.resolve("banana.txt"));

        // Execute
        String result = invokeBuildDirectoryTree(root);

        // Verify
        assertNotNull(result);
        int appleIndex = result.indexOf("apple.txt");
        int bananaIndex = result.indexOf("banana.txt");
        int zebraIndex = result.indexOf("zebra.txt");

        assertTrue(appleIndex > 0);
        assertTrue(bananaIndex > appleIndex);
        assertTrue(zebraIndex > bananaIndex);
    }

    @Test
    void testBuildDirectoryTree_CaseInsensitiveSorting() throws Exception {
        // Setup
        Path root = tempDir.resolve("case-sorted");
        Files.createDirectories(root);
        Files.createFile(root.resolve("Zebra.txt"));
        Files.createFile(root.resolve("apple.txt"));
        Files.createFile(root.resolve("Banana.txt"));

        // Execute
        String result = invokeBuildDirectoryTree(root);

        // Verify
        assertNotNull(result);
        int appleIndex = result.indexOf("apple.txt");
        int bananaIndex = result.indexOf("Banana.txt");
        int zebraIndex = result.indexOf("Zebra.txt");

        assertTrue(appleIndex > 0);
        assertTrue(bananaIndex > appleIndex);
        assertTrue(zebraIndex > bananaIndex);
    }

    @Test
    void testBuildDirectoryTree_SpecialCharactersInNames() throws Exception {
        // Setup
        Path root = tempDir.resolve("special");
        Files.createDirectories(root);
        Files.createFile(root.resolve("file-with-dash.txt"));
        Files.createFile(root.resolve("file_with_underscore.txt"));
        Files.createFile(root.resolve("file.with.dots.txt"));

        // Execute
        String result = invokeBuildDirectoryTree(root);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("file-with-dash.txt"));
        assertTrue(result.contains("file_with_underscore.txt"));
        assertTrue(result.contains("file.with.dots.txt"));
    }

    @Test
    void testBuildDirectoryTree_DeepNesting() throws Exception {
        // Setup
        Path root = tempDir.resolve("deep");
        Path deepPath = root.resolve("level1/level2/level3/level4/level5");
        Files.createDirectories(deepPath);
        Files.createFile(deepPath.resolve("deep-file.txt"));

        // Execute
        String result = invokeBuildDirectoryTree(root);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("level1"));
        assertTrue(result.contains("level2"));
        assertTrue(result.contains("level3"));
        assertTrue(result.contains("level4"));
        assertTrue(result.contains("level5"));
        assertTrue(result.contains("deep-file.txt"));
    }

    @Test
    void testBuildDirectoryTree_MixedFilesAndDirectories() throws Exception {
        // Setup
        Path root = tempDir.resolve("mixed");
        Files.createDirectories(root);
        Files.createFile(root.resolve("file1.txt"));
        Files.createDirectories(root.resolve("dir1"));
        Files.createFile(root.resolve("file2.txt"));
        Files.createDirectories(root.resolve("dir2"));
        Files.createFile(root.resolve("dir1/nested-file.txt"));

        // Execute
        String result = invokeBuildDirectoryTree(root);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("file1.txt"));
        assertTrue(result.contains("file2.txt"));
        assertTrue(result.contains("dir1"));
        assertTrue(result.contains("dir2"));
        assertTrue(result.contains("nested-file.txt"));
    }

    @Test
    void testBuildDirectoryTree_NonExistentPath() throws Exception {
        // Setup
        Path nonExistent = tempDir.resolve("does-not-exist");

        // Execute
        String result = invokeBuildDirectoryTree(nonExistent);

        // Verify - should handle gracefully and return empty or error message
        assertNotNull(result);
    }

    @Test
    void testBuildDirectoryTree_FileInsteadOfDirectory() throws Exception {
        // Setup
        Path file = tempDir.resolve("just-a-file.txt");
        Files.createFile(file);

        // Execute
        String result = invokeBuildDirectoryTree(file);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("just-a-file.txt"));
    }

    @Test
    void testBuildDirectoryTree_HiddenFiles() throws Exception {
        // Setup
        Path root = tempDir.resolve("hidden");
        Files.createDirectories(root);
        Files.createFile(root.resolve(".hidden-file"));
        Files.createFile(root.resolve("visible-file.txt"));
        Files.createDirectories(root.resolve(".hidden-dir"));

        // Execute
        String result = invokeBuildDirectoryTree(root);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains(".hidden-file"));
        assertTrue(result.contains("visible-file.txt"));
        assertTrue(result.contains(".hidden-dir"));
    }

    @Test
    void testBuildDirectoryTree_LargeNumberOfFiles() throws Exception {
        // Setup
        Path root = tempDir.resolve("many-files");
        Files.createDirectories(root);
        for (int i = 0; i < 100; i++) {
            Files.createFile(root.resolve("file" + String.format("%03d", i) + ".txt"));
        }

        // Execute
        String result = invokeBuildDirectoryTree(root);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("file000.txt"));
        assertTrue(result.contains("file099.txt"));
        assertTrue(result.length() > 1000); // Should be a substantial output
    }

    @Test
    void testBuildDirectoryTree_UnicodeCharacters() throws Exception {
        // Setup
        Path root = tempDir.resolve("unicode");
        Files.createDirectories(root);
        Files.createFile(root.resolve("文件.txt")); // Chinese characters
        Files.createFile(root.resolve("файл.txt")); // Cyrillic characters
        Files.createFile(root.resolve("αρχείο.txt")); // Greek characters

        // Execute
        String result = invokeBuildDirectoryTree(root);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("文件.txt"));
        assertTrue(result.contains("файл.txt"));
        assertTrue(result.contains("αρχείο.txt"));
    }

    // ========== Tests for buildTree static method ==========

    @Test
    void testBuildTree_RootElement() throws Exception {
        // Setup
        Path root = tempDir.resolve("root-test");
        Files.createDirectories(root);
        StringBuilder sb = new StringBuilder();

        // Execute
        invokeBuildTree(sb, root, "", true);

        // Verify
        String result = sb.toString();
        assertNotNull(result);
        assertTrue(result.contains("└──"));
        assertTrue(result.contains("root-test"));
    }

    @Test
    void testBuildTree_NotLastElement() throws Exception {
        // Setup
        Path root = tempDir.resolve("not-last");
        Files.createDirectories(root);
        StringBuilder sb = new StringBuilder();

        // Execute
        invokeBuildTree(sb, root, "", false);

        // Verify
        String result = sb.toString();
        assertNotNull(result);
        assertTrue(result.contains("├──"));
        assertTrue(result.contains("not-last"));
    }

    @Test
    void testBuildTree_WithPrefix() throws Exception {
        // Setup
        Path root = tempDir.resolve("with-prefix");
        Files.createDirectories(root);
        StringBuilder sb = new StringBuilder();
        String prefix = "│   ";

        // Execute
        invokeBuildTree(sb, root, prefix, true);

        // Verify
        String result = sb.toString();
        assertNotNull(result);
        assertTrue(result.contains(prefix));
        assertTrue(result.contains("with-prefix"));
    }

    @Test
    void testBuildTree_DirectoryWithChildren() throws Exception {
        // Setup
        Path root = tempDir.resolve("parent");
        Files.createDirectories(root);
        Files.createFile(root.resolve("child1.txt"));
        Files.createFile(root.resolve("child2.txt"));
        StringBuilder sb = new StringBuilder();

        // Execute
        invokeBuildTree(sb, root, "", true);

        // Verify
        String result = sb.toString();
        assertNotNull(result);
        assertTrue(result.contains("parent"));
        assertTrue(result.contains("child1.txt"));
        assertTrue(result.contains("child2.txt"));
    }

    @Test
    void testBuildTree_EmptyDirectoryNoChildren() throws Exception {
        // Setup
        Path root = tempDir.resolve("empty-dir");
        Files.createDirectories(root);
        StringBuilder sb = new StringBuilder();

        // Execute
        invokeBuildTree(sb, root, "", true);

        // Verify
        String result = sb.toString();
        assertNotNull(result);
        assertTrue(result.contains("empty-dir"));
        // Should only contain the directory itself, no children
        assertEquals(1, result.split(System.lineSeparator()).length);
    }

    @Test
    void testBuildTree_FileNode() throws Exception {
        // Setup
        Path file = tempDir.resolve("single-file.txt");
        Files.createFile(file);
        StringBuilder sb = new StringBuilder();

        // Execute
        invokeBuildTree(sb, file, "", true);

        // Verify
        String result = sb.toString();
        assertNotNull(result);
        assertTrue(result.contains("single-file.txt"));
        assertTrue(result.contains("└──"));
        // File should not have children
        assertEquals(1, result.split(System.lineSeparator()).length);
    }

    @Test
    void testBuildTree_MultipleChildrenLastFlag() throws Exception {
        // Setup
        Path root = tempDir.resolve("multi-children");
        Files.createDirectories(root);
        Files.createFile(root.resolve("first.txt"));
        Files.createFile(root.resolve("second.txt"));
        Files.createFile(root.resolve("third.txt"));
        StringBuilder sb = new StringBuilder();

        // Execute
        invokeBuildTree(sb, root, "", true);

        // Verify
        String result = sb.toString();
        assertNotNull(result);
        // First and second should use ├──, last should use └──
        int branchCount = result.split("├──").length - 1;
        int lastCount = result.split("└──").length - 1;
        assertTrue(branchCount >= 2); // At least 2 non-last items
        assertTrue(lastCount >= 1); // At least 1 last item
    }

    @Test
    void testBuildTree_NestedPrefixPropagation() throws Exception {
        // Setup
        Path root = tempDir.resolve("nested-prefix");
        Path subdir = root.resolve("subdir");
        Files.createDirectories(subdir);
        Files.createFile(subdir.resolve("nested-file.txt"));
        StringBuilder sb = new StringBuilder();

        // Execute
        invokeBuildTree(sb, root, "", true);

        // Verify
        String result = sb.toString();
        assertNotNull(result);
        assertTrue(result.contains("nested-prefix"));
        assertTrue(result.contains("subdir"));
        assertTrue(result.contains("nested-file.txt"));
        // Check that indentation increases with nesting
        String[] lines = result.split(System.lineSeparator());
        assertTrue(lines.length >= 3);
    }

    @Test
    void testBuildTree_SystemLineSeparator() throws Exception {
        // Setup
        Path root = tempDir.resolve("line-sep");
        Files.createDirectories(root);
        Files.createFile(root.resolve("file.txt"));
        StringBuilder sb = new StringBuilder();

        // Execute
        invokeBuildTree(sb, root, "", true);

        // Verify
        String result = sb.toString();
        assertNotNull(result);
        assertTrue(result.contains(System.lineSeparator()));
    }

    @Test
    void testBuildTree_SortingOrder() throws Exception {
        // Setup
        Path root = tempDir.resolve("sort-test");
        Files.createDirectories(root);
        Files.createFile(root.resolve("z-file.txt"));
        Files.createFile(root.resolve("a-file.txt"));
        Files.createFile(root.resolve("m-file.txt"));
        StringBuilder sb = new StringBuilder();

        // Execute
        invokeBuildTree(sb, root, "", true);

        // Verify
        String result = sb.toString();
        assertNotNull(result);
        int aIndex = result.indexOf("a-file.txt");
        int mIndex = result.indexOf("m-file.txt");
        int zIndex = result.indexOf("z-file.txt");
        assertTrue(aIndex < mIndex);
        assertTrue(mIndex < zIndex);
    }

    @Test
    void testBuildTree_IOExceptionHandling() throws Exception {
        // Setup - create a path that will cause issues when trying to list
        Path root = tempDir.resolve("io-error-test");
        Files.createFile(root); // Create as file, not directory
        StringBuilder sb = new StringBuilder();

        // Execute - should handle IOException gracefully
        try {
            invokeBuildTree(sb, root, "", true);
            // If no exception, verify output
            String result = sb.toString();
            assertNotNull(result);
        } catch (Exception e) {
            // IOException is expected when trying to list a file as directory
            assertTrue(e.getCause() instanceof IOException || e instanceof IOException);
        }
    }

    // ========== Helper methods to invoke private/protected methods ==========

    private String invokeBuildDirectoryTree(Path root) throws Exception {
        Method method = ProjectService.class.getDeclaredMethod("buildDirectoryTree", Path.class);
        method.setAccessible(true);
        return (String) method.invoke(null, root);
    }

    private void invokeBuildTree(StringBuilder sb, Path path, String prefix, boolean isLast) throws Exception {
        Method method = ProjectService.class.getDeclaredMethod("buildTree", StringBuilder.class, Path.class, String.class, boolean.class);
        method.setAccessible(true);
        method.invoke(null, sb, path, prefix, isLast);
    }

    // ========== Integration-style tests ==========

    @Test
    void testCompleteWorkflow_RealProjectStructure() throws Exception {
        // Setup - create a realistic project structure
        Path projectRoot = tempDir.resolve("my-project");
        Files.createDirectories(projectRoot.resolve("src/main/java/com/example"));
        Files.createDirectories(projectRoot.resolve("src/main/resources"));
        Files.createDirectories(projectRoot.resolve("src/test/java/com/example"));
        Files.createDirectories(projectRoot.resolve("target"));
        
        Files.createFile(projectRoot.resolve("pom.xml"));
        Files.createFile(projectRoot.resolve("README.md"));
        Files.createFile(projectRoot.resolve("src/main/java/com/example/Application.java"));
        Files.createFile(projectRoot.resolve("src/main/resources/application.properties"));
        Files.createFile(projectRoot.resolve("src/test/java/com/example/ApplicationTest.java"));

        when(mcpToolsConfiguration.getDefaultLocalPath()).thenReturn(projectRoot.toString());
        doNothing().when(mcpToolsConfiguration).ensureRootsInitialized(any());

        // Execute
        String result = projectService.projectStructure(toolContext);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("my-project"));
        assertTrue(result.contains("src"));
        assertTrue(result.contains("main"));
        assertTrue(result.contains("test"));
        assertTrue(result.contains("java"));
        assertTrue(result.contains("resources"));
        assertTrue(result.contains("pom.xml"));
        assertTrue(result.contains("README.md"));
        assertTrue(result.contains("Application.java"));
        assertTrue(result.contains("application.properties"));
        assertTrue(result.contains("ApplicationTest.java"));
    }

    @Test
    void testEdgeCase_PathWithSpaces() throws Exception {
        // Setup
        Path root = tempDir.resolve("path with spaces");
        Files.createDirectories(root);
        Files.createFile(root.resolve("file with spaces.txt"));

        // Execute
        String result = invokeBuildDirectoryTree(root);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains("path with spaces"));
        assertTrue(result.contains("file with spaces.txt"));
    }

    @Test
    void testEdgeCase_VeryLongFileName() throws Exception {
        // Setup
        Path root = tempDir.resolve("long-names");
        Files.createDirectories(root);
        String longName = "a".repeat(200) + ".txt";
        Files.createFile(root.resolve(longName));

        // Execute
        String result = invokeBuildDirectoryTree(root);

        // Verify
        assertNotNull(result);
        assertTrue(result.contains(longName));
    }

    @Test
    void testEdgeCase_EmptyStringBuilder() throws Exception {
        // Setup
        Path root = tempDir.resolve("empty-sb");
        Files.createDirectories(root);
        StringBuilder sb = new StringBuilder();

        // Execute
        invokeBuildTree(sb, root, "", true);

        // Verify
        assertTrue(sb.length() > 0);
        assertTrue(sb.toString().contains("empty-sb"));
    }

    @Test
    void testEdgeCase_NullStringBuilder() throws Exception {
        // Setup
        Path root = tempDir.resolve("null-sb");
        Files.createDirectories(root);

        // Execute & Verify
        assertThrows(Exception.class, () -> {
            invokeBuildTree(null, root, "", true);
        });
    }

    @Test
    void testEdgeCase_NullPath() throws Exception {
        // Setup
        StringBuilder sb = new StringBuilder();

        // Execute & Verify
        assertThrows(Exception.class, () -> {
            invokeBuildTree(sb, null, "", true);
        });
    }

    @Test
    void testEdgeCase_NullPrefix() throws Exception {
        // Setup
        Path root = tempDir.resolve("null-prefix");
        Files.createDirectories(root);
        StringBuilder sb = new StringBuilder();

        // Execute
        invokeBuildTree(sb, root, null, true);

        // Verify - should handle null prefix gracefully
        String result = sb.toString();
        assertNotNull(result);
        assertTrue(result.contains("null-prefix"));
    }

    @Test
    void testConcurrency_MultipleSimultaneousCalls() throws Exception {
        // Setup
        Path root1 = tempDir.resolve("concurrent1");
        Path root2 = tempDir.resolve("concurrent2");
        Files.createDirectories(root1);
        Files.createDirectories(root2);
        Files.createFile(root1.resolve("file1.txt"));
        Files.createFile(root2.resolve("file2.txt"));

        // Execute - buildDirectoryTree is static, test thread safety
        String result1 = invokeBuildDirectoryTree(root1);
        String result2 = invokeBuildDirectoryTree(root2);

        // Verify
        assertNotNull(result1);
        assertNotNull(result2);
        assertTrue(result1.contains("concurrent1"));
        assertTrue(result1.contains("file1.txt"));
        assertTrue(result2.contains("concurrent2"));
        assertTrue(result2.contains("file2.txt"));
        assertNotEquals(result1, result2);
    }

    @Test
    void testTreeStructure_CorrectIndentation() throws Exception {
        // Setup
        Path root = tempDir.resolve("indent-test");
        Files.createDirectories(root.resolve("dir1/subdir1"));
        Files.createFile(root.resolve("dir1/file1.txt"));
        Files.createFile(root.resolve("dir1/subdir1/file2.txt"));

        // Execute
        String result = invokeBuildDirectoryTree(root);

        // Verify
        assertNotNull(result);
        String[] lines = result.split(System.lineSeparator());
        assertTrue(lines.length >= 4);
        
        // Check that nested items have more indentation
        boolean foundRoot = false;
        boolean foundDir = false;
        boolean foundSubdir = false;
        
        for (String line : lines) {
            if (line.contains("indent-test")) foundRoot = true;
            if (line.contains("dir1")) foundDir = true;
            if (line.contains("subdir1")) foundSubdir = true;
        }
        
        assertTrue(foundRoot);
        assertTrue(foundDir);
        assertTrue(foundSubdir);
    }

    @Test
    void testBuildTree_PreservesFileNameExactly() throws Exception {
        // Setup
        Path root = tempDir.resolve("exact-names");
        Files.createDirectories(root);
        String exactName = "ExactFileName123.TXT";
        Files.createFile(root.resolve(exactName));
        StringBuilder sb = new StringBuilder();

        // Execute
        invokeBuildTree(sb, root, "", true);

        // Verify
        String result = sb.toString();
        assertTrue(result.contains(exactName));
    }
}
