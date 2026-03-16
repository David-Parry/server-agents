# MCP Internal Server

A Model Context Protocol (MCP) server implementation that provides Git operations, Jira integration, and Terminal command execution, enabling AI assistants and other MCP clients to interact with development tools.

## Overview

This MCP server exposes multiple development tools through a Spring Boot application using:
- **Spring AI MCP Server** (`spring-ai-starter-mcp-server`) for the MCP protocol implementation
- **JGit** (`org.eclipse.jgit:7.3.0.202506031305-r`) for Git operations
- **Jira REST Client** for Jira integration
- **Native Process Execution** for terminal command execution

## Features

The server provides comprehensive Git operations through the `GitService` with **MCP Roots Integration**:

### MCP Roots Support

The Git MCP Server intelligently integrates with the MCP protocol's roots capability:

- **Automatic Root Detection**: Checks if the MCP client supports the roots capability
- **Blocking Initialization**: Waits for roots to be provided before executing any Git operations
- **Default Path Resolution**: Uses the first root from the client as the default local path for cloning repositories
- **Timeout Protection**: Has a 30-second timeout to prevent indefinite blocking
- **Fallback Support**: If no roots are provided, operations can still proceed with explicit paths

When a client provides roots, the first root's URI is used as the base directory for cloning repositories. If you clone without specifying a local path, the repository will be cloned into `<first-root>/<repo-name>`.

### Available Operations

1. **Clone Repository** - Clone a Git repository from a remote URL
2. **Get Status** - Show working tree status (modified, added, removed, untracked files)
3. **Get Log** - View commit history
4. **List Branches** - List all branches in the repository
5. **Create Branch** - Create a new branch
6. **Checkout Branch** - Switch to a different branch
7. **Commit Changes** - Stage and commit changes
8. **Push** - Push commits to a remote repository
9. **Pull** - Pull changes from a remote repository
10. **Diff** - Show differences between commits

## Architecture

The project is structured as follows:

```
src/main/java/ai/qodo/mcp/
├── GitApplication.java              # Spring Boot application entry point
├── config/
│   └── GitMcpConfiguration.java     # MCP server configuration
└── service/
    └── GitService.java              # Git operations service using JGit
```

### Components

1. **GitApplication**: Main Spring Boot application class
2. **GitMcpConfiguration**: Configuration class that sets up the GitService bean
3. **GitService**: Core service that wraps JGit operations and provides a clean API for Git commands

## Building and Running

### Prerequisites
- Java 21 or higher
- Gradle

### Build
```bash
./gradlew build
```

### Run
```bash
./gradlew bootRun
```

Or use the provided script:
```bash
./run-mcp-server.sh
```

The server will start and communicate via STDIO (standard input/output) as per the MCP protocol specification.

## Configuration

The server is configured in `application.properties`:

```properties
spring.application.name=git

# MCP Server Configuration
spring.ai.mcp.server.transport=stdio
spring.ai.mcp.server.enabled=true

# Logging
logging.level.org.springframework.ai.mcp=DEBUG
logging.level.ai.qodo.mcp=DEBUG
```

## Usage

### As an MCP Server

The server communicates via STDIO and can be integrated with MCP clients like Claude Desktop or other AI assistants.

#### Example MCP Client Configuration

Add to your MCP client configuration (e.g., `claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "git": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/git-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

### Direct API Usage

You can also use the `GitService` directly in your Java applications:

```java
@Autowired
private GitService gitService;

// Clone a repository
String result = gitService.cloneRepository(
    "https://github.com/user/repo.git", 
    "/tmp/myrepo"
);

// Get status
String status = gitService.getStatus("/path/to/repo");

// Get commit log
String log = gitService.getLog("/path/to/repo", 10);

// Create and checkout a branch
gitService.createBranch("/path/to/repo", "feature-branch");
gitService.checkoutBranch("/path/to/repo", "feature-branch");

// Commit changes
gitService.commit("/path/to/repo", "Initial commit", List.of());

// Push to remote
gitService.push("/path/to/repo", "origin", "main");
```

## API Reference

### GitService Methods

#### `setClientCapabilities(McpSchema.ClientCapabilities capabilities)`
Sets the MCP client capabilities. Called automatically during MCP initialization.

**Parameters:**
- `capabilities`: The client capabilities object from the MCP protocol

---

#### `setRoots(List<McpSchema.Root> roots)`
Sets the roots received from the MCP client. The first root's URI becomes the default local path.

**Parameters:**
- `roots`: List of root objects from the MCP client

---

#### `hasRootsCapability()`
Checks if the connected MCP client supports the roots capability.

**Returns:** `true` if client supports roots, `false` otherwise

---

#### `getDefaultLocalPath()`
Gets the default local path from the first root provided by the client.

**Returns:** The default path, or `null` if no roots were provided

---

#### `cloneRepository(String remoteUrl, String localPath)`
Clones a repository from a remote URL to a local directory.

**Behavior:**
- Blocks until roots are initialized (if client supports roots capability)
- If `localPath` is null/empty and roots are available, uses `<first-root>/<repo-name>`
- Extracts repository name from the remote URL automatically

**Parameters:**
- `remoteUrl`: The remote repository URL (e.g., https://github.com/user/repo.git)
- `localPath`: The local directory path where the repository will be cloned (optional if roots are available)

**Returns:** Success message with the actual clone path

**Example with roots:**
```java
// If first root is "file:///home/user/projects"
// This will clone to "/home/user/projects/myrepo"
gitService.cloneRepository("https://github.com/user/myrepo.git", null);
```

---

#### `getStatus(String repositoryPath)`
Gets the status of a Git repository showing modified, added, removed, and untracked files.

**Parameters:**
- `repositoryPath`: The path to the Git repository

**Returns:** Formatted status string

---

#### `getLog(String repositoryPath, int maxCount)`
Gets the commit history of a Git repository.

**Parameters:**
- `repositoryPath`: The path to the Git repository
- `maxCount`: Maximum number of commits to retrieve

**Returns:** Formatted log string with commit details

---

#### `listBranches(String repositoryPath)`
Lists all branches in the repository.

**Parameters:**
- `repositoryPath`: The path to the Git repository

**Returns:** Formatted list of branches (current branch marked with *)

---

#### `createBranch(String repositoryPath, String branchName)`
Creates a new branch.

**Parameters:**
- `repositoryPath`: The path to the Git repository
- `branchName`: Name of the branch to create

**Returns:** Success message

---

#### `checkoutBranch(String repositoryPath, String branchName)`
Switches to a different branch.

**Parameters:**
- `repositoryPath`: The path to the Git repository
- `branchName`: Name of the branch to checkout

**Returns:** Success message

---

#### `commit(String repositoryPath, String message, List<String> files)`
Stages and commits changes.

**Parameters:**
- `repositoryPath`: The path to the Git repository
- `message`: The commit message
- `files`: List of files to commit (empty list commits all changes)

**Returns:** Commit hash and message

---

#### `push(String repositoryPath, String remote, String branch)`
Pushes commits to a remote repository.

**Parameters:**
- `repositoryPath`: The path to the Git repository
- `remote`: The remote name (e.g., "origin")
- `branch`: The branch name to push

**Returns:** Success message

---

#### `pull(String repositoryPath)`
Pulls changes from a remote repository.

**Parameters:**
- `repositoryPath`: The path to the Git repository

**Returns:** Success message

---

#### `diff(String repositoryPath, String oldCommit, String newCommit)`
Shows differences between commits.

**Parameters:**
- `repositoryPath`: The path to the Git repository
- `oldCommit`: The old commit reference (null defaults to "HEAD^")
- `newCommit`: The new commit reference (null defaults to "HEAD")

**Returns:** Diff output

---

## Terminal Service

The `TerminalService` provides secure terminal command execution with MCP Roots integration, command blocking, and timeout protection.

### Features

- **MCP Roots Integration**: Automatically uses the first root as the working directory for command execution
- **Command Blocking**: Blocks dangerous commands (rm, sudo, shutdown, etc.) for security
- **Timeout Protection**: Commands automatically timeout after a configurable duration (default: 30 seconds)
- **Separate Shell Execution**: Each command runs in a separate shell process that exits after completion
- **Error Handling**: Returns detailed error information including exit codes and error messages
- **Cross-Platform**: Works on both Unix-like systems (sh) and Windows (cmd.exe)

### Security Features

The following commands are blocked for security:
- File deletion: `rm`, `rmdir`, `del`
- Disk operations: `format`, `mkfs`, `dd`, `fdisk`, `parted`
- System control: `shutdown`, `reboot`, `halt`, `poweroff`, `init`, `systemctl`
- Process control: `kill`, `killall`, `pkill`
- Permission changes: `chmod`, `chown`, `chgrp`
- User management: `passwd`, `sudo`, `su`, `visudo`, `usermod`, `useradd`, `userdel`
- Fork bombs and malicious scripts

### TerminalService Methods

#### `executeCommand(String command, Integer timeoutSeconds, ToolContext toolContext)`
Executes a shell command in a separate process and returns the output.

**Behavior:**
- Blocks until roots are initialized (if client supports roots capability)
- Uses the first root as the working directory
- Validates command against blocked list before execution
- Runs command with timeout protection
- Returns both stdout and stderr
- Automatically kills hanging processes

**Parameters:**
- `command`: The shell command to execute (required)
- `timeoutSeconds`: Optional timeout in seconds (default: 30)
- `toolContext`: The tool context for MCP integration

**Returns:** `TerminalResult` object containing:
- `output`: Standard output from the command
- `error`: Standard error from the command
- `exitCode`: Process exit code
- `isError`: Boolean indicating if an error occurred
- `errorMessage`: Human-readable error message if applicable

**Example:**
```java
@Autowired
private TerminalService terminalService;

// Execute a simple command
TerminalResult result = terminalService.executeCommand("ls -la", null, toolContext);
System.out.println(result.getOutput());

// Execute with custom timeout
TerminalResult result = terminalService.executeCommand("npm install", 120, toolContext);
if (result.isError()) {
    System.err.println("Command failed: " + result.getErrorMessage());
}
```

### TerminalResult Class

The `TerminalResult` class encapsulates the result of a terminal command execution:

```java
public class TerminalResult {
    private final String output;        // stdout content
    private final String error;         // stderr content
    private final int exitCode;         // process exit code
    private final boolean isError;      // true if command failed
    private final String errorMessage;  // error description
    
    // Getters...
    public String toString();  // Formatted output for display
}
```

### Usage Examples

#### Example 1: Build a Project
```java
TerminalResult result = terminalService.executeCommand(
    "./gradlew build", 
    300,  // 5 minute timeout
    toolContext
);

if (!result.isError()) {
    System.out.println("Build successful!");
} else {
    System.err.println("Build failed: " + result.getErrorMessage());
}
```

#### Example 2: Run Tests
```java
TerminalResult result = terminalService.executeCommand(
    "npm test", 
    180,  // 3 minute timeout
    toolContext
);

System.out.println(result.getOutput());
```

#### Example 3: Check System Information
```java
TerminalResult result = terminalService.executeCommand(
    "uname -a", 
    null,  // use default timeout
    toolContext
);

System.out.println("System: " + result.getOutput());
```

### Error Handling

The service handles various error conditions:

1. **Blocked Commands**: Returns error immediately without execution
2. **Timeout**: Kills the process and returns timeout error
3. **Non-zero Exit Code**: Returns output with error flag set
4. **IOException**: Returns error with exception message
5. **Empty Command**: Returns validation error

Example error handling:
```java
TerminalResult result = terminalService.executeCommand(command, timeout, toolContext);

if (result.isError()) {
    switch (result.getErrorMessage()) {
        case "Timeout":
            System.err.println("Command took too long to execute");
            break;
        case "Command validation failed":
            System.err.println("Command is blocked: " + result.getError());
            break;
        default:
            System.err.println("Command failed with exit code: " + result.getExitCode());
            System.err.println("Error: " + result.getError());
    }
}
```

### Configuration

Enable/disable the Terminal service in `application.properties`:

```properties
# Terminal MCP Configuration
mcp.terminal.enabled=${TERMINAL_MCP_ENABLED:true}
```

Set via environment variable:
```bash
export TERMINAL_MCP_ENABLED=true
```

## Dependencies

```gradle
dependencies {
    implementation 'org.springframework.ai:spring-ai-starter-mcp-server'
    implementation 'org.eclipse.jgit:org.eclipse.jgit:7.3.0.202506031305-r'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'
}
```

## Testing

Run the tests:
```bash
./gradlew test
```

The project includes comprehensive unit tests:

### GitService Tests
- Repository status retrieval
- Commit log retrieval
- Branch listing and creation
- Commit operations
- Status with changes

### TerminalService Tests
- Simple command execution
- Command timeout handling
- Blocked command validation
- Non-zero exit code handling
- Working directory execution
- Empty command validation
- Multiple blocked commands
- stderr output handling
- Result formatting

## Example Scenarios

### Scenario 1: Clone and Inspect a Repository
```java
// Clone
gitService.cloneRepository("https://github.com/user/repo.git", "/tmp/repo");

// Check status
String status = gitService.getStatus("/tmp/repo");

// View recent commits
String log = gitService.getLog("/tmp/repo", 5);
```

### Scenario 2: Create a Feature Branch and Commit
```java
// Create and checkout feature branch
gitService.createBranch("/path/to/repo", "feature-x");
gitService.checkoutBranch("/path/to/repo", "feature-x");

// Make changes (outside of this service)
// ...

// Commit changes
gitService.commit("/path/to/repo", "Add feature X", List.of());

// Push to remote
gitService.push("/path/to/repo", "origin", "feature-x");
```

### Scenario 3: Review Changes
```java
// Check what's changed
String status = gitService.getStatus("/path/to/repo");

// View diff
String diff = gitService.diff("/path/to/repo", "HEAD^", "HEAD");
```

## GitHub Service

The `GitHubService` provides GitHub Pull Request creation functionality with comprehensive error handling and validation.

### Features

- **Automatic Repository Detection**: Extracts repository information from git remote URL
- **Configuration Validation**: Validates GitHub API token at startup
- **Enhanced Error Handling**: Provides detailed error messages for common issues
- **Multiple URL Format Support**: Handles SSH, HTTPS, and git:// protocols
- **Rate Limit Monitoring**: Logs API rate limit information
- **Branch Detection**: Automatically uses repository default branch if not specified

### Configuration

Set your GitHub API token in `application.properties` or via environment variable:

```properties
# GitHub MCP Configuration
mcp.github.enabled=${GITHUB_MCP_ENABLED:true}
mcp.github.api-token=${GITHUB_API_TOKEN}
```

Set via environment variable:
```bash
export GITHUB_API_TOKEN="ghp_your_token_here"
```

### Required API Token Scopes

- **Public repositories**: `public_repo` scope
- **Private repositories**: `repo` scope (full control)

Generate a token at: https://github.com/settings/tokens

### GitHubService Methods

#### `createPullRequest(String body, String title, String sourceBranch, String targetBranch, ToolContext toolContext)`
Creates a new Pull Request on GitHub for the current repository.

**Behavior:**
- Automatically detects repository from git remote URL
- Uses repository default branch if targetBranch is not specified
- Validates repository access before creating PR
- Provides detailed error messages for troubleshooting

**Parameters:**
- `body`: The detailed description of the Pull Request
- `title`: The title of the Pull Request
- `sourceBranch`: The source branch (feature branch)
- `targetBranch`: The target branch (optional, defaults to repository default branch)
- `toolContext`: The tool context for MCP integration

**Returns:** `ToolOutputResult` containing:
- Success: PR ID and confirmation message
- Error: Detailed error message with troubleshooting steps

**Example:**
```java
@Autowired
private GitHubService gitHubService;

ToolOutputResult result = gitHubService.createPullRequest(
    "This PR adds feature X with the following changes:\n- Added new API endpoint\n- Updated documentation",
    "Add Feature X",
    "feature/add-x",
    "main",  // or null to use default branch
    toolContext
);

if (!result.isError()) {
    System.out.println("PR created: " + result.getOutput());
} else {
    System.err.println("Failed to create PR: " + result.getError());
}
```

### Error Handling

The service provides specific error messages for common issues:

1. **Repository Not Found (404)**
   - Repository doesn't exist or no access
   - Provides verification checklist

2. **Unauthorized (401)**
   - Invalid or expired API token
   - Suggests token regeneration

3. **Forbidden (403)**
   - Insufficient permissions or rate limit exceeded
   - Suggests checking token scopes

4. **Validation Failed (422)**
   - Invalid PR parameters (e.g., branch doesn't exist)
   - Suggests verifying branch existence


