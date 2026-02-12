package com.davidparry.agent.security;

import com.davidparry.agent.config.AdminProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter that validates admin API token for /api/admin/** endpoints.
 * Uses the Authorization header with Bearer token format.
 * 
 * This is separate from the customer JWT authentication used for WebSocket connections.
 */
@Component
@Order(1)
public class AdminAuthenticationFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminAuthenticationFilter.class);
    private static final String ADMIN_PATH_PREFIX = "/api/admin";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    
    private final AdminProperties adminProperties;
    
    public AdminAuthenticationFilter(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String path = httpRequest.getRequestURI();
        
        // Only filter admin endpoints
        if (!path.startsWith(ADMIN_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        
        // Check if admin API is enabled
        if (!adminProperties.enabled()) {
            logger.warn("Admin API is disabled, rejecting request to: {}", path);
            sendErrorResponse(httpResponse, HttpServletResponse.SC_SERVICE_UNAVAILABLE, 
                            "Admin API is disabled");
            return;
        }
        
        // Validate Authorization header
        String authHeader = httpRequest.getHeader(AUTHORIZATION_HEADER);
        
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            logger.warn("Missing or invalid Authorization header for admin endpoint: {}", path);
            sendErrorResponse(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, 
                            "Missing or invalid Authorization header");
            return;
        }
        
        String token = authHeader.substring(BEARER_PREFIX.length());
        
        if (!adminProperties.apiToken().equals(token)) {
            logger.warn("Invalid admin token for endpoint: {}", path);
            sendErrorResponse(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, 
                            "Invalid admin token");
            return;
        }
        
        logger.debug("Admin authentication successful for: {}", path);
        chain.doFilter(request, response);
    }
    
    private void sendErrorResponse(HttpServletResponse response, int status, String message) 
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(String.format("{\"error\": \"%s\"}", message));
    }
}
