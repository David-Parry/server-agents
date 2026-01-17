package com.davidparry.agent.security;

import com.davidparry.agent.config.AdminProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.Mockito.*;

/**
 * Unit tests for AdminAuthenticationFilter.
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthenticationFilterTest {

    private static final String VALID_TOKEN = "valid-admin-token";
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private FilterChain filterChain;

    private AdminAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        AdminProperties properties = new AdminProperties(VALID_TOKEN, true);
        filter = new AdminAuthenticationFilter(properties);
    }
    
    private void setupResponseWriter() throws Exception {
        StringWriter responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    void shouldPassThroughNonAdminEndpoints() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/customers");

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void shouldRejectAdminEndpointWithoutAuthorizationHeader() throws Exception {
        // Given
        setupResponseWriter();
        when(request.getRequestURI()).thenReturn("/api/admin/customers");
        when(request.getHeader("Authorization")).thenReturn(null);

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectAdminEndpointWithInvalidAuthorizationFormat() throws Exception {
        // Given
        setupResponseWriter();
        when(request.getRequestURI()).thenReturn("/api/admin/customers");
        when(request.getHeader("Authorization")).thenReturn("Basic " + VALID_TOKEN);

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectAdminEndpointWithInvalidToken() throws Exception {
        // Given
        setupResponseWriter();
        when(request.getRequestURI()).thenReturn("/api/admin/customers");
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldAllowAdminEndpointWithValidToken() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/admin/customers");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void shouldAllowNestedAdminEndpointWithValidToken() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/admin/customers/tokens/stats");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRejectWhenAdminApiIsDisabled() throws Exception {
        // Given
        setupResponseWriter();
        AdminProperties disabledProperties = new AdminProperties("token", false);
        AdminAuthenticationFilter disabledFilter = new AdminAuthenticationFilter(disabledProperties);
        
        when(request.getRequestURI()).thenReturn("/api/admin/customers");

        // When
        disabledFilter.doFilter(request, response, filterChain);

        // Then
        verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        verify(filterChain, never()).doFilter(request, response);
    }
}
