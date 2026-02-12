plugins {
    id("org.springframework.boot") version "3.5.8"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.spotbugs") version "6.4.8"
    java
    jacoco
    checkstyle
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
    finalizedBy(tasks.jacocoTestReport)
}

// JaCoCo Configuration
jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    
    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/html"))
    }
    
    // Exclude generated classes and configuration from coverage
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/dto/**",
                    "**/pojo/**",
                    "**/entity/**",
                    "**/config/**Config.class",
                    "**/Application.class",
                    "**/Application\$*.class"
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.55".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.35".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.55".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "METHOD"
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "CLASS"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
    
    // Exclude generated classes and configuration from coverage verification
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/dto/**",
                    "**/pojo/**",
                    "**/entity/**",
                    "**/config/**Config.class",
                    "**/Application.class",
                    "**/Application\$*.class"
                )
            }
        })
    )
}

// Make check task depend on coverage verification
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Checkstyle Configuration
checkstyle {
    toolVersion = "10.21.4"
    configFile = file("${rootDir}/config/checkstyle/checkstyle.xml")
    isIgnoreFailures = false  // Report warnings but don't fail build
    maxWarnings = Int.MAX_VALUE
    maxErrors = 0
}

tasks.withType<Checkstyle> {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// SpotBugs Configuration
spotbugs {
    ignoreFailures.set(false)  // Report issues but don't fail build
    showStackTraces.set(true)
    showProgress.set(true)
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.LOW)
    excludeFilter.set(file("${rootDir}/config/spotbugs/exclude.xml"))
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
    val taskName = name
    reports.create("html") {
        required.set(true)
        outputLocation.set(layout.buildDirectory.file("reports/spotbugs/${taskName}.html"))
    }
    reports.create("xml") {
        required.set(true)
        outputLocation.set(layout.buildDirectory.file("reports/spotbugs/${taskName}.xml"))
    }
}

// Token Hash Generator task for http-header-generator.sh
tasks.register<JavaExec>("runTokenHashGenerator") {
    group = "application"
    description = "Runs the TokenHashGenerator utility to create token hashes"
    mainClass.set("com.davidparry.agent.security.TokenHashGenerator")
    classpath = sourceSets["main"].runtimeClasspath

    args = listOf(
        project.findProperty("tokenArg")?.toString() ?: "",
        project.findProperty("customerIdArg")?.toString() ?: "",
        project.findProperty("secretArg")?.toString() ?: "",
        project.findProperty("secretVersionArg")?.toString() ?: "V1"
    )
}
