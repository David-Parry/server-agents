package com.davidparry.agent;

import com.davidparry.agent.config.DatabaseAgentConfigurationProvider;
import com.davidparry.agent.repository.AgentConfigRepository;
import com.davidparry.agent.repository.CustomerRepository;
import com.davidparry.agent.repository.CustomerTokenRepository;
import com.davidparry.agent.security.DatabaseApiKeyValidator;
import com.davidparry.agent.security.TokenHashingService;
import com.davidparry.agent.service.CustomerTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test to verify the application context loads correctly
 * with all the new database-backed components.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApplicationContextTest {

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerTokenRepository customerTokenRepository;

    @Autowired
    private DatabaseAgentConfigurationProvider agentConfigurationProvider;

    @Autowired
    private TokenHashingService tokenHashingService;

    @Autowired
    private DatabaseApiKeyValidator apiKeyValidator;

    @Autowired
    private CustomerTokenService customerTokenService;

    @Test
    void contextLoads() {
        // Verify all beans are created
        assertNotNull(agentConfigRepository);
        assertNotNull(customerRepository);
        assertNotNull(customerTokenRepository);
        assertNotNull(agentConfigurationProvider);
        assertNotNull(tokenHashingService);
        assertNotNull(apiKeyValidator);
        assertNotNull(customerTokenService);
    }

    @Test
    void flywayMigrationsRun() {
        // Verify Flyway migrations ran and seeded data exists
        long agentConfigCount = agentConfigRepository.count();
        // Should have 4 agent configs from V3 migration
        assert agentConfigCount >= 4 : "Expected at least 4 agent configs from seed data";
    }
}
