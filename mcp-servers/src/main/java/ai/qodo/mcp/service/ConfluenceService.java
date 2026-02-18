/*
 * Copyright (C) 2025 Qodo
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ai.qodo.mcp.service;

import ai.qodo.mcp.config.ConfluenceConfiguration;
import ai.qodo.mcp.config.ConfluenceMcpConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@ConditionalOnProperty(name = "mcp.confluence.enabled", havingValue = "true", matchIfMissing = true)
public class ConfluenceService {
    private static final Logger logger = LoggerFactory.getLogger(ConfluenceService.class);
    private static final String API_V2_PATH = "/wiki/api/v2";
    private static final String API_V1_PATH = "/wiki/rest/api";

    private final ConfluenceMcpConfiguration mcpConfiguration;
    private final ObjectMapper objectMapper;
    private WebClient webClient;

    public ConfluenceService(ConfluenceMcpConfiguration confluenceMcpConfiguration) {
        this.mcpConfiguration = confluenceMcpConfiguration;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        ConfluenceConfiguration config = mcpConfiguration.getConfluenceConfiguration();
        if (mcpConfiguration.isConfigurationValid()) {
            try {
                String credentials = config.getEmail() + ":" + config.getApiToken();
                String encodedCredentials = Base64.getEncoder().encodeToString(
                        credentials.getBytes(StandardCharsets.UTF_8));

                this.webClient = WebClient.builder()
                        .baseUrl(config.getSiteUrl())
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials)
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .build();

                logger.info("Confluence REST client created for: {}", config.getSiteUrl());
            } catch (Exception e) {
                logger.error("Failed to create Confluence REST client: {}", e.getMessage(), e);
                this.webClient = null;
            }
        } else {
            logger.warn("Confluence configuration is invalid. Service will not be initialized.");
            logger.warn("Please ensure CONFLUENCE_SITE_URL, CONFLUENCE_EMAIL, and CONFLUENCE_API_TOKEN environment variables are set.");
        }
    }

    @PreDestroy
    public void cleanup() {
        logger.debug("Confluence REST client cleanup completed");
    }

    /**
     * Ensures the Confluence client is initialized before executing operations.
     */
    private void ensureClientInitialized() {
        if (webClient == null) {
            throw new IllegalStateException("Confluence REST client is not initialized. Please check your configuration.");
        }
    }

    @Tool(name = "confluence_get_spaces", description = "Lists all accessible Confluence spaces. " +
            "Returns space keys, names, and types. Use this to discover available spaces before searching or creating content.")
    public String getSpaces(
            @ToolParam(description = "Maximum number of results to return (default: 25)", required = false) Integer limit) {
        ensureClientInitialized();

        int maxResults = limit != null ? limit : 25;

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(API_V2_PATH + "/spaces")
                            .queryParam("limit", maxResults)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");

            StringBuilder result = new StringBuilder("Available Spaces:\n\n");

            if (results != null && results.isArray()) {
                for (JsonNode space : results) {
                    result.append("Key: ").append(getTextValue(space, "key")).append("\n");
                    result.append("Name: ").append(getTextValue(space, "name")).append("\n");
                    result.append("Type: ").append(getTextValue(space, "type")).append("\n");
                    result.append("ID: ").append(getTextValue(space, "id")).append("\n");
                    if (space.has("description") && space.get("description").has("plain")) {
                        result.append("Description: ").append(
                                getTextValue(space.get("description").get("plain"), "value")).append("\n");
                    }
                    result.append("\n");
                }
            }

            return result.toString();
        } catch (WebClientResponseException e) {
            logger.error("Error fetching spaces: {}", e.getResponseBodyAsString(), e);
            return "Error fetching spaces: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error fetching spaces", e);
            return "Error fetching spaces: " + e.getMessage();
        }
    }

    @Tool(name = "confluence_get_space", description = "Retrieves detailed information about a specific Confluence space. " +
            "Returns space details including name, description, homepage, and permissions.")
    public String getSpace(
            @ToolParam(description = "The space ID to retrieve") String spaceId) {
        ensureClientInitialized();

        try {
            String response = webClient.get()
                    .uri(API_V2_PATH + "/spaces/" + spaceId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode space = objectMapper.readTree(response);

            StringBuilder result = new StringBuilder("Space Details:\n\n");
            result.append("ID: ").append(getTextValue(space, "id")).append("\n");
            result.append("Key: ").append(getTextValue(space, "key")).append("\n");
            result.append("Name: ").append(getTextValue(space, "name")).append("\n");
            result.append("Type: ").append(getTextValue(space, "type")).append("\n");
            result.append("Status: ").append(getTextValue(space, "status")).append("\n");

            if (space.has("homepageId")) {
                result.append("Homepage ID: ").append(getTextValue(space, "homepageId")).append("\n");
            }

            if (space.has("description") && space.get("description").has("plain")) {
                result.append("Description: ").append(
                        getTextValue(space.get("description").get("plain"), "value")).append("\n");
            }

            result.append("\nURL: ").append(mcpConfiguration.getConfluenceConfiguration().getSiteUrl())
                    .append("/wiki/spaces/").append(getTextValue(space, "key"));

            return result.toString();
        } catch (WebClientResponseException e) {
            logger.error("Error fetching space: {}", e.getResponseBodyAsString(), e);
            return "Error fetching space: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error fetching space", e);
            return "Error fetching space: " + e.getMessage();
        }
    }

    @Tool(name = "confluence_search_content", description = "Searches for Confluence content using CQL (Confluence Query Language). " +
            "Returns pages, blog posts, and other content matching the search criteria. " +
            "Example CQL: 'space = DEV AND type = page AND text ~ \"API documentation\"'")
    public String searchContent(
            @ToolParam(description = "CQL query string for searching content") String cql,
            @ToolParam(description = "Maximum number of results to return (default: 25)", required = false) Integer limit) {
        ensureClientInitialized();

        int maxResults = limit != null ? limit : 25;

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(API_V1_PATH + "/content/search")
                            .queryParam("cql", cql)
                            .queryParam("limit", maxResults)
                            .queryParam("expand", "space,version,ancestors")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");

            StringBuilder result = new StringBuilder("Search Results:\n\n");

            if (results != null && results.isArray()) {
                int count = 0;
                for (JsonNode content : results) {
                    count++;
                    result.append(formatContentSummary(content)).append("\n");
                }
                result.insert(0, "Found " + count + " results\n\n");
            }

            return result.toString();
        } catch (WebClientResponseException e) {
            logger.error("Error searching content: {}", e.getResponseBodyAsString(), e);
            return "Error searching content: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error searching content", e);
            return "Error searching content: " + e.getMessage();
        }
    }

    @Tool(name = "confluence_get_page", description = "Retrieves detailed information about a specific Confluence page. " +
            "Returns page details including title, content, version, and metadata.")
    public String getPage(
            @ToolParam(description = "The page ID to retrieve") String pageId,
            @ToolParam(description = "Include page body content (default: true)", required = false) Boolean includeBody) {
        ensureClientInitialized();

        boolean getBody = includeBody == null || includeBody;

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(API_V2_PATH + "/pages/" + pageId)
                            .queryParam("body-format", "storage")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode page = objectMapper.readTree(response);

            return formatPageDetails(page, getBody);
        } catch (WebClientResponseException e) {
            logger.error("Error fetching page: {}", e.getResponseBodyAsString(), e);
            return "Error fetching page: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error fetching page", e);
            return "Error fetching page: " + e.getMessage();
        }
    }

    @Tool(name = "confluence_get_page_by_title", description = "Retrieves a Confluence page by its title within a specific space. " +
            "Returns page details including content, version, and metadata.")
    public String getPageByTitle(
            @ToolParam(description = "The space key where the page is located") String spaceKey,
            @ToolParam(description = "The title of the page to retrieve") String title,
            @ToolParam(description = "Include page body content (default: true)", required = false) Boolean includeBody) {
        ensureClientInitialized();

        boolean getBody = includeBody == null || includeBody;

        try {
            // First, search for the page by title in the space
            String cql = String.format("space = \"%s\" AND title = \"%s\" AND type = page", spaceKey, title);
            
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(API_V1_PATH + "/content/search")
                            .queryParam("cql", cql)
                            .queryParam("limit", 1)
                            .queryParam("expand", "body.storage,version,space,ancestors")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");

            if (results == null || !results.isArray() || results.isEmpty()) {
                return "Page not found with title '" + title + "' in space '" + spaceKey + "'";
            }

            JsonNode page = results.get(0);
            return formatPageDetailsV1(page, getBody);
        } catch (WebClientResponseException e) {
            logger.error("Error fetching page by title: {}", e.getResponseBodyAsString(), e);
            return "Error fetching page: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error fetching page by title", e);
            return "Error fetching page: " + e.getMessage();
        }
    }

    @Tool(name = "confluence_create_page", description = "Creates a new Confluence page in the specified space. " +
            "The content should be in Confluence storage format (XHTML-based). " +
            "Use this to create new wiki pages with formatted content.")
    public String createPage(
            @ToolParam(description = "The space ID where the page will be created") String spaceId,
            @ToolParam(description = "The title of the new page") String title,
            @ToolParam(description = "The page content in Confluence storage format (XHTML)") String content,
            @ToolParam(description = "Parent page ID (optional, for creating child pages)", required = false) String parentId) {
        ensureClientInitialized();

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("spaceId", spaceId);
            requestBody.put("status", "current");
            requestBody.put("title", title);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("representation", "storage");
            body.put("value", content);
            requestBody.set("body", body);

            if (parentId != null && !parentId.isEmpty()) {
                requestBody.put("parentId", parentId);
            }

            String response = webClient.post()
                    .uri(API_V2_PATH + "/pages")
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode createdPage = objectMapper.readTree(response);

            String pageId = getTextValue(createdPage, "id");
            String pageTitle = getTextValue(createdPage, "title");

            return "Page created successfully!\n" +
                    "ID: " + pageId + "\n" +
                    "Title: " + pageTitle + "\n" +
                    "URL: " + mcpConfiguration.getConfluenceConfiguration().getSiteUrl() +
                    "/wiki/spaces/" + getSpaceKeyById(spaceId) + "/pages/" + pageId;
        } catch (WebClientResponseException e) {
            logger.error("Error creating page: {}", e.getResponseBodyAsString(), e);
            return "Error creating page: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error creating page", e);
            return "Error creating page: " + e.getMessage();
        }
    }

    @Tool(name = "confluence_update_page", description = "Updates an existing Confluence page. " +
            "Requires the current version number to prevent conflicts. " +
            "The content should be in Confluence storage format (XHTML-based).")
    public String updatePage(
            @ToolParam(description = "The page ID to update") String pageId,
            @ToolParam(description = "The new title for the page") String title,
            @ToolParam(description = "The new content in Confluence storage format (XHTML)") String content,
            @ToolParam(description = "The current version number of the page (required for update)") Integer version) {
        ensureClientInitialized();

        try {
            // First get the current page to get spaceId and status
            String currentPageResponse = webClient.get()
                    .uri(API_V2_PATH + "/pages/" + pageId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode currentPage = objectMapper.readTree(currentPageResponse);
            String spaceId = getTextValue(currentPage, "spaceId");
            String status = getTextValue(currentPage, "status");

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("id", pageId);
            requestBody.put("spaceId", spaceId);
            requestBody.put("status", status);
            requestBody.put("title", title);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("representation", "storage");
            body.put("value", content);
            requestBody.set("body", body);

            ObjectNode versionNode = objectMapper.createObjectNode();
            versionNode.put("number", version + 1);
            versionNode.put("message", "Updated via MCP");
            requestBody.set("version", versionNode);

            String response = webClient.put()
                    .uri(API_V2_PATH + "/pages/" + pageId)
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode updatedPage = objectMapper.readTree(response);

            return "Page updated successfully!\n" +
                    "ID: " + getTextValue(updatedPage, "id") + "\n" +
                    "Title: " + getTextValue(updatedPage, "title") + "\n" +
                    "New Version: " + (version + 1);
        } catch (WebClientResponseException e) {
            logger.error("Error updating page: {}", e.getResponseBodyAsString(), e);
            return "Error updating page: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error updating page", e);
            return "Error updating page: " + e.getMessage();
        }
    }

    @Tool(name = "confluence_delete_page", description = "Deletes a Confluence page. " +
            "This action moves the page to trash. Use with caution.")
    public String deletePage(
            @ToolParam(description = "The page ID to delete") String pageId) {
        ensureClientInitialized();

        try {
            webClient.delete()
                    .uri(API_V2_PATH + "/pages/" + pageId)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            return "Page " + pageId + " deleted successfully (moved to trash)";
        } catch (WebClientResponseException e) {
            logger.error("Error deleting page: {}", e.getResponseBodyAsString(), e);
            return "Error deleting page: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error deleting page", e);
            return "Error deleting page: " + e.getMessage();
        }
    }

    @Tool(name = "confluence_get_page_children", description = "Retrieves all child pages of a specific Confluence page. " +
            "Use this to navigate the page hierarchy.")
    public String getPageChildren(
            @ToolParam(description = "The parent page ID") String pageId,
            @ToolParam(description = "Maximum number of results to return (default: 25)", required = false) Integer limit) {
        ensureClientInitialized();

        int maxResults = limit != null ? limit : 25;

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(API_V2_PATH + "/pages/" + pageId + "/children")
                            .queryParam("limit", maxResults)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");

            StringBuilder result = new StringBuilder("Child Pages:\n\n");

            if (results != null && results.isArray()) {
                if (results.isEmpty()) {
                    return "No child pages found for page ID: " + pageId;
                }
                for (JsonNode child : results) {
                    result.append("ID: ").append(getTextValue(child, "id")).append("\n");
                    result.append("Title: ").append(getTextValue(child, "title")).append("\n");
                    result.append("Status: ").append(getTextValue(child, "status")).append("\n");
                    result.append("\n");
                }
            }

            return result.toString();
        } catch (WebClientResponseException e) {
            logger.error("Error fetching child pages: {}", e.getResponseBodyAsString(), e);
            return "Error fetching child pages: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error fetching child pages", e);
            return "Error fetching child pages: " + e.getMessage();
        }
    }

    @Tool(name = "confluence_get_space_pages", description = "Retrieves all pages in a specific Confluence space. " +
            "Returns a list of pages with their basic information.")
    public String getSpacePages(
            @ToolParam(description = "The space ID to get pages from") String spaceId,
            @ToolParam(description = "Maximum number of results to return (default: 25)", required = false) Integer limit) {
        ensureClientInitialized();

        int maxResults = limit != null ? limit : 25;

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(API_V2_PATH + "/spaces/" + spaceId + "/pages")
                            .queryParam("limit", maxResults)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");

            StringBuilder result = new StringBuilder("Pages in Space:\n\n");

            if (results != null && results.isArray()) {
                for (JsonNode page : results) {
                    result.append("ID: ").append(getTextValue(page, "id")).append("\n");
                    result.append("Title: ").append(getTextValue(page, "title")).append("\n");
                    result.append("Status: ").append(getTextValue(page, "status")).append("\n");
                    if (page.has("parentId")) {
                        result.append("Parent ID: ").append(getTextValue(page, "parentId")).append("\n");
                    }
                    result.append("\n");
                }
            }

            return result.toString();
        } catch (WebClientResponseException e) {
            logger.error("Error fetching space pages: {}", e.getResponseBodyAsString(), e);
            return "Error fetching space pages: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error fetching space pages", e);
            return "Error fetching space pages: " + e.getMessage();
        }
    }

    @Tool(name = "confluence_add_comment", description = "Adds a comment to a Confluence page. " +
            "Comments can be used for discussions and feedback on page content.")
    public String addComment(
            @ToolParam(description = "The page ID to add a comment to") String pageId,
            @ToolParam(description = "The comment content in Confluence storage format") String commentBody) {
        ensureClientInitialized();

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("pageId", pageId);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("representation", "storage");
            body.put("value", commentBody);
            requestBody.set("body", body);

            String response = webClient.post()
                    .uri(API_V2_PATH + "/footer-comments")
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode comment = objectMapper.readTree(response);

            return "Comment added successfully!\n" +
                    "Comment ID: " + getTextValue(comment, "id") + "\n" +
                    "Page ID: " + pageId;
        } catch (WebClientResponseException e) {
            logger.error("Error adding comment: {}", e.getResponseBodyAsString(), e);
            return "Error adding comment: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error adding comment", e);
            return "Error adding comment: " + e.getMessage();
        }
    }

    @Tool(name = "confluence_get_page_comments", description = "Retrieves all comments on a specific Confluence page. " +
            "Returns comment content, authors, and timestamps.")
    public String getPageComments(
            @ToolParam(description = "The page ID to get comments from") String pageId,
            @ToolParam(description = "Maximum number of results to return (default: 25)", required = false) Integer limit) {
        ensureClientInitialized();

        int maxResults = limit != null ? limit : 25;

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(API_V2_PATH + "/pages/" + pageId + "/footer-comments")
                            .queryParam("limit", maxResults)
                            .queryParam("body-format", "storage")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");

            StringBuilder result = new StringBuilder("Page Comments:\n\n");

            if (results != null && results.isArray()) {
                if (results.isEmpty()) {
                    return "No comments found for page ID: " + pageId;
                }
                for (JsonNode comment : results) {
                    result.append("Comment ID: ").append(getTextValue(comment, "id")).append("\n");
                    result.append("Created: ").append(getTextValue(comment, "createdAt")).append("\n");
                    if (comment.has("body") && comment.get("body").has("storage")) {
                        result.append("Content: ").append(
                                getTextValue(comment.get("body").get("storage"), "value")).append("\n");
                    }
                    result.append("\n");
                }
            }

            return result.toString();
        } catch (WebClientResponseException e) {
            logger.error("Error fetching comments: {}", e.getResponseBodyAsString(), e);
            return "Error fetching comments: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error fetching comments", e);
            return "Error fetching comments: " + e.getMessage();
        }
    }

    @Tool(name = "confluence_get_page_labels", description = "Retrieves all labels attached to a specific Confluence page. " +
            "Labels are used for categorization and organization of content.")
    public String getPageLabels(
            @ToolParam(description = "The page ID to get labels from") String pageId) {
        ensureClientInitialized();

        try {
            String response = webClient.get()
                    .uri(API_V2_PATH + "/pages/" + pageId + "/labels")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");

            StringBuilder result = new StringBuilder("Page Labels:\n\n");

            if (results != null && results.isArray()) {
                if (results.isEmpty()) {
                    return "No labels found for page ID: " + pageId;
                }
                for (JsonNode label : results) {
                    result.append("- ").append(getTextValue(label, "name")).append("\n");
                }
            }

            return result.toString();
        } catch (WebClientResponseException e) {
            logger.error("Error fetching labels: {}", e.getResponseBodyAsString(), e);
            return "Error fetching labels: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error fetching labels", e);
            return "Error fetching labels: " + e.getMessage();
        }
    }

    @Tool(name = "confluence_add_label", description = "Adds a label to a Confluence page. " +
            "Labels help organize and categorize content for easier discovery.")
    public String addLabel(
            @ToolParam(description = "The page ID to add a label to") String pageId,
            @ToolParam(description = "The label name to add") String labelName) {
        ensureClientInitialized();

        try {
            ArrayNode labels = objectMapper.createArrayNode();
            ObjectNode label = objectMapper.createObjectNode();
            label.put("name", labelName);
            labels.add(label);

            String response = webClient.post()
                    .uri(API_V2_PATH + "/pages/" + pageId + "/labels")
                    .bodyValue(labels.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return "Label '" + labelName + "' added successfully to page " + pageId;
        } catch (WebClientResponseException e) {
            logger.error("Error adding label: {}", e.getResponseBodyAsString(), e);
            return "Error adding label: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error adding label", e);
            return "Error adding label: " + e.getMessage();
        }
    }

    @Tool(name = "confluence_get_page_versions", description = "Retrieves the version history of a Confluence page. " +
            "Shows all previous versions with their authors and timestamps.")
    public String getPageVersions(
            @ToolParam(description = "The page ID to get version history from") String pageId,
            @ToolParam(description = "Maximum number of versions to return (default: 10)", required = false) Integer limit) {
        ensureClientInitialized();

        int maxResults = limit != null ? limit : 10;

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(API_V2_PATH + "/pages/" + pageId + "/versions")
                            .queryParam("limit", maxResults)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");

            StringBuilder result = new StringBuilder("Page Version History:\n\n");

            if (results != null && results.isArray()) {
                for (JsonNode version : results) {
                    result.append("Version: ").append(getTextValue(version, "number")).append("\n");
                    result.append("Created: ").append(getTextValue(version, "createdAt")).append("\n");
                    if (version.has("message") && !version.get("message").isNull()) {
                        result.append("Message: ").append(getTextValue(version, "message")).append("\n");
                    }
                    if (version.has("authorId")) {
                        result.append("Author ID: ").append(getTextValue(version, "authorId")).append("\n");
                    }
                    result.append("\n");
                }
            }

            return result.toString();
        } catch (WebClientResponseException e) {
            logger.error("Error fetching page versions: {}", e.getResponseBodyAsString(), e);
            return "Error fetching page versions: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error fetching page versions", e);
            return "Error fetching page versions: " + e.getMessage();
        }
    }

    @Tool(name = "confluence_get_content_by_label", description = "Retrieves all Confluence content with a specific label. " +
            "Use this to find related pages and content across spaces.")
    public String getContentByLabel(
            @ToolParam(description = "The label name to search for") String labelName,
            @ToolParam(description = "Maximum number of results to return (default: 25)", required = false) Integer limit) {
        ensureClientInitialized();

        int maxResults = limit != null ? limit : 25;

        try {
            String cql = "label = \"" + labelName + "\"";

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(API_V1_PATH + "/content/search")
                            .queryParam("cql", cql)
                            .queryParam("limit", maxResults)
                            .queryParam("expand", "space,version")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");

            StringBuilder result = new StringBuilder("Content with label '" + labelName + "':\n\n");

            if (results != null && results.isArray()) {
                if (results.isEmpty()) {
                    return "No content found with label: " + labelName;
                }
                for (JsonNode content : results) {
                    result.append(formatContentSummary(content)).append("\n");
                }
            }

            return result.toString();
        } catch (WebClientResponseException e) {
            logger.error("Error fetching content by label: {}", e.getResponseBodyAsString(), e);
            return "Error fetching content by label: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error fetching content by label", e);
            return "Error fetching content by label: " + e.getMessage();
        }
    }

    // Helper methods

    private String getTextValue(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "N/A";
        }
        return node.get(field).asText();
    }

    private String formatContentSummary(JsonNode content) {
        StringBuilder result = new StringBuilder();
        result.append("ID: ").append(getTextValue(content, "id")).append("\n");
        result.append("Title: ").append(getTextValue(content, "title")).append("\n");
        result.append("Type: ").append(getTextValue(content, "type")).append("\n");

        if (content.has("space")) {
            JsonNode space = content.get("space");
            result.append("Space: ").append(getTextValue(space, "name"))
                    .append(" (").append(getTextValue(space, "key")).append(")\n");
        }

        if (content.has("version")) {
            JsonNode version = content.get("version");
            result.append("Version: ").append(getTextValue(version, "number")).append("\n");
        }

        if (content.has("_links") && content.get("_links").has("webui")) {
            result.append("URL: ").append(mcpConfiguration.getConfluenceConfiguration().getSiteUrl())
                    .append("/wiki").append(getTextValue(content.get("_links"), "webui")).append("\n");
        }

        return result.toString();
    }

    private String formatPageDetails(JsonNode page, boolean includeBody) {
        StringBuilder result = new StringBuilder("Page Details:\n\n");

        result.append("ID: ").append(getTextValue(page, "id")).append("\n");
        result.append("Title: ").append(getTextValue(page, "title")).append("\n");
        result.append("Status: ").append(getTextValue(page, "status")).append("\n");
        result.append("Space ID: ").append(getTextValue(page, "spaceId")).append("\n");

        if (page.has("parentId") && !page.get("parentId").isNull()) {
            result.append("Parent ID: ").append(getTextValue(page, "parentId")).append("\n");
        }

        if (page.has("version")) {
            JsonNode version = page.get("version");
            result.append("Version: ").append(getTextValue(version, "number")).append("\n");
            result.append("Created: ").append(getTextValue(version, "createdAt")).append("\n");
        }

        if (page.has("authorId")) {
            result.append("Author ID: ").append(getTextValue(page, "authorId")).append("\n");
        }

        if (includeBody && page.has("body") && page.get("body").has("storage")) {
            result.append("\nContent:\n");
            result.append(getTextValue(page.get("body").get("storage"), "value")).append("\n");
        }

        return result.toString();
    }

    private String formatPageDetailsV1(JsonNode page, boolean includeBody) {
        StringBuilder result = new StringBuilder("Page Details:\n\n");

        result.append("ID: ").append(getTextValue(page, "id")).append("\n");
        result.append("Title: ").append(getTextValue(page, "title")).append("\n");
        result.append("Type: ").append(getTextValue(page, "type")).append("\n");
        result.append("Status: ").append(getTextValue(page, "status")).append("\n");

        if (page.has("space")) {
            JsonNode space = page.get("space");
            result.append("Space: ").append(getTextValue(space, "name"))
                    .append(" (").append(getTextValue(space, "key")).append(")\n");
        }

        if (page.has("version")) {
            JsonNode version = page.get("version");
            result.append("Version: ").append(getTextValue(version, "number")).append("\n");
            if (version.has("when")) {
                result.append("Last Modified: ").append(getTextValue(version, "when")).append("\n");
            }
            if (version.has("by")) {
                result.append("Modified By: ").append(getTextValue(version.get("by"), "displayName")).append("\n");
            }
        }

        if (page.has("ancestors") && page.get("ancestors").isArray() && !page.get("ancestors").isEmpty()) {
            result.append("Parent: ").append(getTextValue(page.get("ancestors").get(
                    page.get("ancestors").size() - 1), "title")).append("\n");
        }

        if (page.has("_links") && page.get("_links").has("webui")) {
            result.append("URL: ").append(mcpConfiguration.getConfluenceConfiguration().getSiteUrl())
                    .append("/wiki").append(getTextValue(page.get("_links"), "webui")).append("\n");
        }

        if (includeBody && page.has("body") && page.get("body").has("storage")) {
            result.append("\nContent:\n");
            result.append(getTextValue(page.get("body").get("storage"), "value")).append("\n");
        }

        return result.toString();
    }

    private String getSpaceKeyById(String spaceId) {
        try {
            String response = webClient.get()
                    .uri(API_V2_PATH + "/spaces/" + spaceId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode space = objectMapper.readTree(response);
            return getTextValue(space, "key");
        } catch (Exception e) {
            logger.warn("Could not fetch space key for ID: {}", spaceId);
            return spaceId;
        }
    }
}
