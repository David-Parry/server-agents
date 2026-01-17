plugins {
    id("org.springframework.boot") version "3.5.8"
    id("io.spring.dependency-management") version "1.1.7"
    java
}

group = "com.davidparry.agent"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenLocal()
    mavenCentral()
}

extra["springAiVersion"] = "1.1.2"

dependencies {
    // Shared message protocol library
    implementation("com.davidparry.agent:message-protocol:1.0.0")

    // Core Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Database
    runtimeOnly("com.h2database:h2")

    // Flyway for migrations
    implementation("org.flywaydb:flyway-core")

    // Spring AI - LLM providers only (no MCP server - we're a proxy)
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")

    // Metrics
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Reliability
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.2.0")

    // Rate limiting
    implementation("com.bucket4j:bucket4j-core:8.10.1")

    // JWT support
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // OpenAPI / Swagger documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.4")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
