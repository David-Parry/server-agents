package com.davidparry.agent.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "agent.client.api-key=test-key",
    "agent.client.server-url=ws://localhost:9999/test"
})
class AgentClientApplicationTests {

    @Test
    void contextLoads() {
        // Verify Spring context loads successfully
    }
}
