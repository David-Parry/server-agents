/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp.service;

import ai.qodo.mcp.config.AzureDevOpsMcpConfiguration;
import ai.qodo.mcp.pojo.ToolOutputResult;
import ai.qodo.mcp.util.ParameterStringBuilder;
import jakarta.annotation.PostConstruct;
import org.azd.enums.PullRequestStatus;
import org.azd.exceptions.AzDException;
import org.azd.git.types.GitPullRequest;
import org.azd.git.types.GitPullRequestQueryParameters;
import org.azd.git.types.IdentityRefWithVote;
import org.azd.git.types.PullRequests;
import org.azd.interfaces.GitDetails;
import org.azd.utils.AzDClientApi;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

@Service
@ConditionalOnProperty(name = "mcp.azure-devops.enabled", havingValue = "true", matchIfMissing = true)
public class AzureDevOpsService {

    private static final Logger logger = LoggerFactory.getLogger(AzureDevOpsService.class);
    private final AzureDevOpsMcpConfiguration azureDevOpsMcpConfiguration;

    public AzureDevOpsService(AzureDevOpsMcpConfiguration azureDevOpsMcpConfiguration) {
        this.azureDevOpsMcpConfiguration = azureDevOpsMcpConfiguration;
    }

    /**
     * Validates Azure DevOps configuration at service initialization.
     */
    @PostConstruct
    public void validateConfiguration() {
        if (!azureDevOpsMcpConfiguration.isConfigurationValid()) {
            logger.error("Azure DevOps configuration is invalid - PAT or Organization URL is missing");
            return;
        }

        try {
            AzDClientApi client = createClient();
            logger.info("Azure DevOps API connection validated successfully for organization: {}",
                    azureDevOpsMcpConfiguration.getAzureDevOpsConfiguration().getOrganizationUrl());
        } catch (AzDException e) {
            logger.error("Azure DevOps API validation failed: {}. Please verify your PAT is valid and has appropriate permissions.", 
                    e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during Azure DevOps configuration validation: {}", e.getMessage(), e);
        }
    }

    /**
     * Creates an Azure DevOps client instance.
     * Note: AzDClientApi constructor order is (organizationName, projectName, personalAccessToken)
     */
    private AzDClientApi createClient() throws AzDException {
        String orgName = extractOrganizationName(azureDevOpsMcpConfiguration.getAzureDevOpsConfiguration().getOrganizationUrl());
        String pat = azureDevOpsMcpConfiguration.getAzureDevOpsConfiguration().getPersonalAccessToken();
        String defaultProject = azureDevOpsMcpConfiguration.getAzureDevOpsConfiguration().getDefaultProject();
        
        logger.debug("Creating Azure DevOps client for organization: {}, project: {}", orgName, defaultProject);
        return new AzDClientApi(orgName, defaultProject != null ? defaultProject : "", pat);
    }

    /**
     * Creates an Azure DevOps client instance with a specific project.
     * Note: AzDClientApi constructor order is (organizationName, projectName, personalAccessToken)
     */
    private AzDClientApi createClient(String project) throws AzDException {
        String orgName = extractOrganizationName(azureDevOpsMcpConfiguration.getAzureDevOpsConfiguration().getOrganizationUrl());
        String pat = azureDevOpsMcpConfiguration.getAzureDevOpsConfiguration().getPersonalAccessToken();
        
        logger.debug("Creating Azure DevOps client for organization: {}, project: {}", orgName, project);
        return new AzDClientApi(orgName, project != null ? project : "", pat);
    }

    /**
     * Extracts the organization name from an Azure DevOps URL.
     * Supports formats:
     * - https://dev.azure.com/{org}
     * - https://dev.azure.com/{org}/
     * - https://{org}.visualstudio.com
     * - {org} (just the organization name)
     * 
     * @param orgUrl The organization URL or name
     * @return The organization name
     */
    private String extractOrganizationName(String orgUrl) {
        if (orgUrl == null || orgUrl.isBlank()) {
            return "";
        }
        
        String url = orgUrl.trim();
        
        // Remove trailing slash
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        
        // Handle https://dev.azure.com/{org} format
        if (url.contains("dev.azure.com/")) {
            String afterDomain = url.substring(url.indexOf("dev.azure.com/") + 14);
            // Get just the org name (first path segment)
            int slashIndex = afterDomain.indexOf('/');
            if (slashIndex > 0) {
                return afterDomain.substring(0, slashIndex);
            }
            return afterDomain;
        }
        
        // Handle https://{org}.visualstudio.com format
        if (url.contains(".visualstudio.com")) {
            int start = url.indexOf("://");
            if (start >= 0) {
                start += 3;
            } else {
                start = 0;
            }
            int end = url.indexOf(".visualstudio.com");
            return url.substring(start, end);
        }
        
        // If it doesn't look like a URL, assume it's already the org name
        if (!url.contains("://") && !url.contains(".")) {
            return url;
        }
        
        logger.warn("Could not extract organization name from URL: {}. Using as-is.", orgUrl);
        return url;
    }

    @Tool(name = "azure_devops_create_pr", description = "Creates a new Pull Request in Azure DevOps for the current repository. "
            + "This tool opens a PR from a source branch (typically a feature branch) to a target branch (typically main/master). "
            + "Use this when you need to propose code changes for review and merging in Azure DevOps. The tool can automatically detect "
            + "the Azure DevOps repository from the local git configuration or you can specify the repository name explicitly. "
            + "Ensure both source and target branches exist on the remote repository before creating the PR.")
    public ToolOutputResult createPullRequest(
            @ToolParam(description = "The description/body of the Pull Request explaining what has been developed") String description,
            @ToolParam(description = "The title of the Pull Request") String title,
            @ToolParam(description = "The source branch for the Pull Request (the branch with your changes)") String sourceBranch,
            @ToolParam(description = "The target branch for the Pull Request. If not specified, defaults to 'main'.", required = false) String targetBranch,
            @ToolParam(description = "The Azure DevOps project name. If not specified, uses the default project from configuration.", required = false) String project,
            @ToolParam(description = "The repository name. If not specified, attempts to detect from local git configuration.", required = false) String repositoryName,
            ToolContext toolContext) {

        String params = ParameterStringBuilder.buildParameterString(
                "title", title,
                "sourceBranch", sourceBranch,
                "targetBranch", targetBranch != null ? targetBranch : "main",
                "project", project != null ? project : "default",
                "repositoryName", repositoryName != null ? repositoryName : "auto-detect"
        );
        logger.info("Attempting to create Azure DevOps Pull Request with parameters: {}", params);

        if (!azureDevOpsMcpConfiguration.isConfigurationValid()) {
            String errorMsg = "Azure DevOps configuration is invalid - PAT or Organization URL is missing";
            logger.error(errorMsg);
            return new ToolOutputResult("", errorMsg, -1, true, "ConfigurationError");
        }

        try {
            azureDevOpsMcpConfiguration.ensureRootsInitialized(toolContext);
        } catch (InterruptedException e) {
            logger.error("Could not initialize roots for Azure DevOps PR creation: {}", e.getMessage(), e);
            return new ToolOutputResult("Error in creating PR for parameters " + params,
                    "Failed to create PR: " + e.getMessage(), -1, true, "InterruptedException");
        }

        String effectiveTargetBranch = (targetBranch != null && !targetBranch.isBlank()) ? targetBranch : "main";
        String effectiveProject = project;
        String effectiveRepoName = repositoryName;

        // If repository name not provided, try to detect from local git
        if (effectiveRepoName == null || effectiveRepoName.isBlank()) {
            try {
                AzureDevOpsRepoInfo repoInfo = detectAzureDevOpsRepo();
                if (repoInfo != null) {
                    effectiveRepoName = repoInfo.repositoryName;
                    if (effectiveProject == null || effectiveProject.isBlank()) {
                        effectiveProject = repoInfo.projectName;
                    }
                    logger.info("Detected Azure DevOps repository: {} in project: {}", effectiveRepoName, effectiveProject);
                }
            } catch (Exception e) {
                logger.warn("Could not auto-detect Azure DevOps repository: {}", e.getMessage());
            }
        }

        // Use default project if still not set
        if (effectiveProject == null || effectiveProject.isBlank()) {
            effectiveProject = azureDevOpsMcpConfiguration.getAzureDevOpsConfiguration().getDefaultProject();
        }

        if (effectiveProject == null || effectiveProject.isBlank()) {
            String errorMsg = "Project name is required but not provided and could not be auto-detected. " +
                    "Please specify the project parameter or configure a default project.";
            logger.error(errorMsg);
            return new ToolOutputResult("", errorMsg, -1, true, "MissingProject");
        }

        if (effectiveRepoName == null || effectiveRepoName.isBlank()) {
            String errorMsg = "Repository name is required but not provided and could not be auto-detected. " +
                    "Please specify the repositoryName parameter.";
            logger.error(errorMsg);
            return new ToolOutputResult("", errorMsg, -1, true, "MissingRepository");
        }

        try {
            AzDClientApi client = createClient(effectiveProject);
            GitDetails gitApi = client.getGitApi();

            // Format branch refs (Azure DevOps requires refs/heads/ prefix)
            String sourceRef = formatBranchRef(sourceBranch);
            String targetRef = formatBranchRef(effectiveTargetBranch);

            logger.info("Creating PR in project '{}', repo '{}' from '{}' to '{}'",
                    effectiveProject, effectiveRepoName, sourceRef, targetRef);

            // Check for existing PR
            GitPullRequest existingPr = findExistingPullRequest(gitApi, effectiveRepoName, sourceRef, targetRef);
            if (existingPr != null) {
                logger.info("Found existing PR #{} for branch '{}' to '{}'",
                        existingPr.getPullRequestId(), sourceBranch, effectiveTargetBranch);
                return new ToolOutputResult(
                        String.format("A Pull Request already exists for branch '%s' to '%s'.\n" +
                                        "PR #%d: %s\n" +
                                        "Status: %s\n" +
                                        "URL: %s",
                                sourceBranch, effectiveTargetBranch,
                                existingPr.getPullRequestId(),
                                existingPr.getTitle(),
                                existingPr.getStatus(),
                                buildPrUrl(effectiveProject, effectiveRepoName, existingPr.getPullRequestId())),
                        "", 0, false, "");
            }

            // Create the pull request
            // Parameters: repositoryId, sourceRefName, targetRefName, title, description, reviewers[]
            GitPullRequest pr = gitApi.createPullRequest(
                    effectiveRepoName,
                    sourceRef,
                    targetRef,
                    title,
                    description,
                    new String[]{} // No reviewers initially
            );

            int prId = pr.getPullRequestId();
            String prUrl = buildPrUrl(effectiveProject, effectiveRepoName, prId);

            String result = String.format("Pull Request created successfully!\n" +
                            "PR #%d: %s\n" +
                            "Source: %s\n" +
                            "Target: %s\n" +
                            "Status: %s\n" +
                            "URL: %s",
                    prId, title, sourceBranch, effectiveTargetBranch, pr.getStatus(), prUrl);

            logger.info("Successfully created PR #{} in {}/{}", prId, effectiveProject, effectiveRepoName);
            return new ToolOutputResult(result, "", 0, false, "");

        } catch (AzDException e) {
            String errorMsg = String.format("Azure DevOps API error while creating PR: %s", e.getMessage());
            logger.error(errorMsg, e);
            return new ToolOutputResult("", errorMsg, -1, true, "AzDException");
        } catch (Exception e) {
            String errorMsg = String.format("Unexpected error while creating PR: %s", e.getMessage());
            logger.error(errorMsg, e);
            return new ToolOutputResult("", errorMsg, -1, true, "UnexpectedException");
        }
    }

    @Tool(name = "azure_devops_list_prs", description = "Lists Pull Requests in an Azure DevOps repository. "
            + "Can filter by status (active, completed, abandoned, all) and optionally by creator or reviewer. "
            + "Use this to see open PRs, check PR status, or find PRs that need review.")
    public ToolOutputResult listPullRequests(
            @ToolParam(description = "The Azure DevOps project name. If not specified, uses the default project.", required = false) String project,
            @ToolParam(description = "The repository name. If not specified, attempts to detect from local git.", required = false) String repositoryName,
            @ToolParam(description = "Filter by PR status: 'active', 'completed', 'abandoned', or 'all'. Defaults to 'active'.", required = false) String status,
            @ToolParam(description = "Maximum number of PRs to return. Defaults to 10.", required = false) Integer limit,
            ToolContext toolContext) {

        int maxPrs = (limit != null && limit > 0) ? limit : 10;
        String effectiveStatus = (status != null && !status.isBlank()) ? status.toLowerCase() : "active";

        String params = ParameterStringBuilder.buildParameterString(
                "project", project != null ? project : "default",
                "repositoryName", repositoryName != null ? repositoryName : "auto-detect",
                "status", effectiveStatus,
                "limit", String.valueOf(maxPrs)
        );
        logger.info("Listing Azure DevOps Pull Requests with parameters: {}", params);

        if (!azureDevOpsMcpConfiguration.isConfigurationValid()) {
            String errorMsg = "Azure DevOps configuration is invalid - PAT or Organization URL is missing";
            logger.error(errorMsg);
            return new ToolOutputResult("", errorMsg, -1, true, "ConfigurationError");
        }

        try {
            azureDevOpsMcpConfiguration.ensureRootsInitialized(toolContext);
        } catch (InterruptedException e) {
            logger.error("Could not initialize roots: {}", e.getMessage(), e);
            return new ToolOutputResult("", "Failed to initialize: " + e.getMessage(), -1, true, "InterruptedException");
        }

        String effectiveProject = project;
        String effectiveRepoName = repositoryName;

        // Auto-detect repository if not provided
        if (effectiveRepoName == null || effectiveRepoName.isBlank()) {
            try {
                AzureDevOpsRepoInfo repoInfo = detectAzureDevOpsRepo();
                if (repoInfo != null) {
                    effectiveRepoName = repoInfo.repositoryName;
                    if (effectiveProject == null || effectiveProject.isBlank()) {
                        effectiveProject = repoInfo.projectName;
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not auto-detect Azure DevOps repository: {}", e.getMessage());
            }
        }

        if (effectiveProject == null || effectiveProject.isBlank()) {
            effectiveProject = azureDevOpsMcpConfiguration.getAzureDevOpsConfiguration().getDefaultProject();
        }

        if (effectiveProject == null || effectiveProject.isBlank()) {
            return new ToolOutputResult("", "Project name is required but not provided", -1, true, "MissingProject");
        }

        if (effectiveRepoName == null || effectiveRepoName.isBlank()) {
            return new ToolOutputResult("", "Repository name is required but not provided", -1, true, "MissingRepository");
        }

        try {
            AzDClientApi client = createClient(effectiveProject);
            GitDetails gitApi = client.getGitApi();

            PullRequestStatus prStatus = mapStatus(effectiveStatus);

            PullRequests pullRequests = gitApi.getPullRequests(effectiveRepoName, prStatus);

            if (pullRequests == null || pullRequests.getPullRequests() == null || 
                pullRequests.getPullRequests().isEmpty()) {
                return new ToolOutputResult(
                        String.format("No %s pull requests found in %s/%s", effectiveStatus, effectiveProject, effectiveRepoName),
                        "", 0, false, "");
            }

            StringBuilder output = new StringBuilder();
            output.append(String.format("Pull Requests in %s/%s (Status: %s):\n\n", 
                    effectiveProject, effectiveRepoName, effectiveStatus));

            List<GitPullRequest> prs = pullRequests.getPullRequests();
            int count = 0;
            for (GitPullRequest pr : prs) {
                if (count >= maxPrs) break;
                output.append(formatPullRequestInfo(pr, effectiveProject, effectiveRepoName));
                output.append("\n---\n");
                count++;
            }

            logger.info("Found {} pull requests in {}/{}", count, effectiveProject, effectiveRepoName);
            return new ToolOutputResult(output.toString(), "", 0, false, "");

        } catch (AzDException e) {
            String errorMsg = String.format("Azure DevOps API error: %s", e.getMessage());
            logger.error(errorMsg, e);
            return new ToolOutputResult("", errorMsg, -1, true, "AzDException");
        } catch (Exception e) {
            String errorMsg = String.format("Unexpected error: %s", e.getMessage());
            logger.error(errorMsg, e);
            return new ToolOutputResult("", errorMsg, -1, true, "UnexpectedException");
        }
    }

    @Tool(name = "azure_devops_get_pr", description = "Gets detailed information about a specific Pull Request by its ID. "
            + "Returns PR details including title, description, source/target branches, status, reviewers, and more.")
    public ToolOutputResult getPullRequest(
            @ToolParam(description = "The Pull Request ID number") int pullRequestId,
            @ToolParam(description = "The Azure DevOps project name. If not specified, uses the default project.", required = false) String project,
            @ToolParam(description = "The repository name. If not specified, attempts to detect from local git.", required = false) String repositoryName,
            ToolContext toolContext) {

        String params = ParameterStringBuilder.buildParameterString(
                "pullRequestId", String.valueOf(pullRequestId),
                "project", project != null ? project : "default",
                "repositoryName", repositoryName != null ? repositoryName : "auto-detect"
        );
        logger.info("Getting Azure DevOps Pull Request with parameters: {}", params);

        if (!azureDevOpsMcpConfiguration.isConfigurationValid()) {
            return new ToolOutputResult("", "Azure DevOps configuration is invalid", -1, true, "ConfigurationError");
        }

        try {
            azureDevOpsMcpConfiguration.ensureRootsInitialized(toolContext);
        } catch (InterruptedException e) {
            return new ToolOutputResult("", "Failed to initialize: " + e.getMessage(), -1, true, "InterruptedException");
        }

        String effectiveProject = project;
        String effectiveRepoName = repositoryName;

        // Auto-detect if not provided
        if (effectiveRepoName == null || effectiveRepoName.isBlank()) {
            try {
                AzureDevOpsRepoInfo repoInfo = detectAzureDevOpsRepo();
                if (repoInfo != null) {
                    effectiveRepoName = repoInfo.repositoryName;
                    if (effectiveProject == null || effectiveProject.isBlank()) {
                        effectiveProject = repoInfo.projectName;
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not auto-detect repository: {}", e.getMessage());
            }
        }

        if (effectiveProject == null || effectiveProject.isBlank()) {
            effectiveProject = azureDevOpsMcpConfiguration.getAzureDevOpsConfiguration().getDefaultProject();
        }

        if (effectiveProject == null || effectiveProject.isBlank()) {
            return new ToolOutputResult("", "Project name is required", -1, true, "MissingProject");
        }

        if (effectiveRepoName == null || effectiveRepoName.isBlank()) {
            return new ToolOutputResult("", "Repository name is required", -1, true, "MissingRepository");
        }

        try {
            AzDClientApi client = createClient(effectiveProject);
            GitDetails gitApi = client.getGitApi();

            GitPullRequest pr = gitApi.getPullRequest(effectiveRepoName, pullRequestId);

            if (pr == null) {
                return new ToolOutputResult("", 
                        String.format("Pull Request #%d not found", pullRequestId), -1, true, "NotFound");
            }

            String output = formatPullRequestDetailedInfo(pr, effectiveProject, effectiveRepoName);
            return new ToolOutputResult(output, "", 0, false, "");

        } catch (AzDException e) {
            String errorMsg = String.format("Azure DevOps API error: %s", e.getMessage());
            logger.error(errorMsg, e);
            return new ToolOutputResult("", errorMsg, -1, true, "AzDException");
        } catch (Exception e) {
            String errorMsg = String.format("Unexpected error: %s", e.getMessage());
            logger.error(errorMsg, e);
            return new ToolOutputResult("", errorMsg, -1, true, "UnexpectedException");
        }
    }

    /**
     * Formats branch reference to include refs/heads/ prefix if not present.
     */
    private String formatBranchRef(String branch) {
        if (branch == null) return null;
        if (branch.startsWith("refs/heads/")) {
            return branch;
        }
        return "refs/heads/" + branch;
    }

    /**
     * Strips refs/heads/ prefix from branch name for display.
     */
    private String stripBranchRef(String ref) {
        if (ref == null) return null;
        if (ref.startsWith("refs/heads/")) {
            return ref.substring(11);
        }
        return ref;
    }

    /**
     * Maps string status to PullRequestStatus enum.
     */
    private PullRequestStatus mapStatus(String status) {
        return switch (status.toLowerCase()) {
            case "completed" -> PullRequestStatus.COMPLETED;
            case "abandoned" -> PullRequestStatus.ABANDONED;
            case "all" -> PullRequestStatus.ALL;
            default -> PullRequestStatus.ACTIVE;
        };
    }

    /**
     * Builds the URL for a pull request.
     */
    private String buildPrUrl(String project, String repository, int prId) {
        String orgUrl = azureDevOpsMcpConfiguration.getAzureDevOpsConfiguration().getOrganizationUrl();
        // Remove trailing slash if present
        if (orgUrl.endsWith("/")) {
            orgUrl = orgUrl.substring(0, orgUrl.length() - 1);
        }
        return String.format("%s/%s/_git/%s/pullrequest/%d", orgUrl, project, repository, prId);
    }

    /**
     * Finds an existing pull request for the given source and target branches.
     */
    private GitPullRequest findExistingPullRequest(GitDetails gitApi, String repository,
                                                    String sourceRef, String targetRef) {
        try {
            GitPullRequestQueryParameters queryParams = new GitPullRequestQueryParameters();
            queryParams.sourceRefName = sourceRef;
            queryParams.targetRefName = targetRef;
            queryParams.status = PullRequestStatus.ACTIVE;
            
            PullRequests prs = gitApi.getPullRequests(repository, queryParams);

            if (prs != null && prs.getPullRequests() != null && !prs.getPullRequests().isEmpty()) {
                // Return the first active PR matching the criteria
                return prs.getPullRequests().get(0);
            }
        } catch (AzDException e) {
            logger.warn("Error checking for existing PR: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Formats pull request information for list display.
     */
    private String formatPullRequestInfo(GitPullRequest pr, String project, String repository) {
        StringBuilder info = new StringBuilder();
        info.append(String.format("PR #%d: %s\n", pr.getPullRequestId(), pr.getTitle()));
        info.append(String.format("  Status: %s\n", pr.getStatus()));
        info.append(String.format("  Source: %s → Target: %s\n", 
                stripBranchRef(pr.getSourceRefName()), stripBranchRef(pr.getTargetRefName())));
        if (pr.getCreatedBy() != null) {
            info.append(String.format("  Created by: %s\n", pr.getCreatedBy().getDisplayName()));
        }
        if (pr.getCreationDate() != null) {
            info.append(String.format("  Created: %s\n", pr.getCreationDate()));
        }
        info.append(String.format("  URL: %s", buildPrUrl(project, repository, pr.getPullRequestId())));
        return info.toString();
    }

    /**
     * Formats detailed pull request information.
     */
    private String formatPullRequestDetailedInfo(GitPullRequest pr, String project, String repository) {
        StringBuilder info = new StringBuilder();
        info.append(String.format("Pull Request #%d\n", pr.getPullRequestId()));
        info.append("================\n\n");
        info.append(String.format("Title: %s\n", pr.getTitle()));
        info.append(String.format("Status: %s\n", pr.getStatus()));
        info.append(String.format("Source Branch: %s\n", stripBranchRef(pr.getSourceRefName())));
        info.append(String.format("Target Branch: %s\n", stripBranchRef(pr.getTargetRefName())));
        
        if (pr.getCreatedBy() != null) {
            info.append(String.format("Created By: %s\n", pr.getCreatedBy().getDisplayName()));
        }
        if (pr.getCreationDate() != null) {
            info.append(String.format("Created Date: %s\n", pr.getCreationDate()));
        }
        
        if (pr.getDescription() != null && !pr.getDescription().isBlank()) {
            info.append(String.format("\nDescription:\n%s\n", pr.getDescription()));
        }

        if (pr.getReviewers() != null && !pr.getReviewers().isEmpty()) {
            info.append("\nReviewers:\n");
            for (IdentityRefWithVote reviewer : pr.getReviewers()) {
                String vote = formatVote(reviewer.getVote());
                info.append(String.format("  - %s (%s)\n", reviewer.getDisplayName(), vote));
            }
        }

        if (pr.getMergeStatus() != null) {
            info.append(String.format("\nMerge Status: %s\n", pr.getMergeStatus()));
        }

        info.append(String.format("\nURL: %s", buildPrUrl(project, repository, pr.getPullRequestId())));
        return info.toString();
    }

    /**
     * Formats reviewer vote to human-readable string.
     */
    private String formatVote(int vote) {
        return switch (vote) {
            case 10 -> "Approved";
            case 5 -> "Approved with suggestions";
            case 0 -> "No vote";
            case -5 -> "Waiting for author";
            case -10 -> "Rejected";
            default -> "Unknown (" + vote + ")";
        };
    }

    /**
     * Detects Azure DevOps repository information from local git configuration.
     */
    private AzureDevOpsRepoInfo detectAzureDevOpsRepo() throws IOException, URISyntaxException {
        String localPath = azureDevOpsMcpConfiguration.getDefaultLocalPath();
        if (localPath == null || localPath.isBlank()) {
            return null;
        }

        File gitDir = findGitDirectory(localPath);
        if (gitDir == null) {
            return null;
        }

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.setGitDir(gitDir).readEnvironment().findGitDir().build()) {
            RemoteConfig remoteConfig = new RemoteConfig(repository.getConfig(), "origin");
            if (remoteConfig.getURIs().isEmpty()) {
                return null;
            }
            URIish uri = remoteConfig.getURIs().get(0);
            return parseAzureDevOpsUrl(uri.toString());
        }
    }

    /**
     * Parses Azure DevOps URL to extract organization, project, and repository names.
     * Supports both HTTPS and SSH formats:
     * - HTTPS: https://dev.azure.com/{org}/{project}/_git/{repo}
     * - HTTPS (old): https://{org}.visualstudio.com/{project}/_git/{repo}
     * - SSH: git@ssh.dev.azure.com:v3/{org}/{project}/{repo}
     */
    private AzureDevOpsRepoInfo parseAzureDevOpsUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        // SSH format: git@ssh.dev.azure.com:v3/{org}/{project}/{repo}
        if (url.startsWith("git@ssh.dev.azure.com:v3/")) {
            String path = url.substring("git@ssh.dev.azure.com:v3/".length());
            String[] parts = path.split("/");
            if (parts.length >= 3) {
                return new AzureDevOpsRepoInfo(parts[0], parts[1], parts[2]);
            }
        }

        // HTTPS format: https://dev.azure.com/{org}/{project}/_git/{repo}
        if (url.contains("dev.azure.com")) {
            String path = url.substring(url.indexOf("dev.azure.com/") + 14);
            String[] parts = path.split("/");
            if (parts.length >= 4 && "_git".equals(parts[2])) {
                String repoName = parts[3];
                // Remove .git suffix if present
                if (repoName.endsWith(".git")) {
                    repoName = repoName.substring(0, repoName.length() - 4);
                }
                return new AzureDevOpsRepoInfo(parts[0], parts[1], repoName);
            }
        }

        // Old HTTPS format: https://{org}.visualstudio.com/{project}/_git/{repo}
        if (url.contains(".visualstudio.com")) {
            int orgEnd = url.indexOf(".visualstudio.com");
            int orgStart = url.lastIndexOf("/", orgEnd - 1) + 1;
            if (orgStart < 0) orgStart = url.indexOf("://") + 3;
            String org = url.substring(orgStart, orgEnd);
            
            String path = url.substring(orgEnd + ".visualstudio.com/".length());
            String[] parts = path.split("/");
            if (parts.length >= 3 && "_git".equals(parts[1])) {
                String repoName = parts[2];
                if (repoName.endsWith(".git")) {
                    repoName = repoName.substring(0, repoName.length() - 4);
                }
                return new AzureDevOpsRepoInfo(org, parts[0], repoName);
            }
        }

        logger.warn("Could not parse Azure DevOps URL: {}", url);
        return null;
    }

    /**
     * Finds the .git directory starting from the given path.
     */
    private File findGitDirectory(String startPath) {
        if (startPath == null || startPath.isEmpty()) {
            return null;
        }

        File startDir = new File(startPath);
        if (!startDir.exists()) {
            return null;
        }

        if (!startDir.isDirectory()) {
            startDir = startDir.getParentFile();
        }

        return searchGitDirectoryRecursive(startDir);
    }

    /**
     * Recursively searches for .git directory.
     */
    private File searchGitDirectoryRecursive(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return null;
        }

        File gitDir = new File(directory, ".git");
        if (gitDir.exists() && gitDir.isDirectory()) {
            return gitDir;
        }

        File[] subdirs = directory.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                if (subdir.getName().startsWith(".") && !subdir.getName().equals(".git")) {
                    continue;
                }
                File found = searchGitDirectoryRecursive(subdir);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Helper class to hold Azure DevOps repository information.
     */
    private static class AzureDevOpsRepoInfo {
        final String organizationName;
        final String projectName;
        final String repositoryName;

        AzureDevOpsRepoInfo(String organizationName, String projectName, String repositoryName) {
            this.organizationName = organizationName;
            this.projectName = projectName;
            this.repositoryName = repositoryName;
        }
    }
}
