plugins {
    id("org.springframework.boot") version "3.5.8"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.spotbugs") version "6.4.8"
    id("org.unbroken-dome.test-sets") version "4.1.0"
    java
    jacoco
    checkstyle
}

group = "com.davidparry.agent"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
}

extra["springAiVersion"] = "1.1.2"

dependencies {
    // Shared message protocol library
    implementation(project(":agent-message-protocol"))

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
    val coveragePhase = (findProperty("coveragePhase") as String?) ?: "baseline"
    val lineThresholdByPhase = mapOf(
        "baseline" to "0.20".toBigDecimal(),
        "intermediate" to "0.60".toBigDecimal(),
        "target" to "0.85".toBigDecimal()
    )
    val lineCoverageMinimum = (findProperty("coverageMinimum") as String?)?.toBigDecimal()
        ?: lineThresholdByPhase[coveragePhase]
        ?: lineThresholdByPhase.getValue("baseline")
    
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = lineCoverageMinimum
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
    configFile = file("${projectDir}/config/checkstyle/checkstyle.xml")
}

tasks.withType<Checkstyle> {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// Fail build on any checkstyle violations in main source code
tasks.named<Checkstyle>("checkstyleMain") {
    isIgnoreFailures = false
    maxWarnings = 0
    maxErrors = 0
}

// Don't fail build for test code checkstyle violations (just report them)
tasks.named<Checkstyle>("checkstyleTest") {
    isIgnoreFailures = true
}

// SpotBugs Configuration
spotbugs {
    showStackTraces.set(true)
    showProgress.set(true)
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.LOW)
    excludeFilter.set(file("${projectDir}/config/spotbugs/exclude.xml"))
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

// Fail build on any SpotBugs violations in main source code
tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsMain").configure {
    ignoreFailures = false
}

// Don't fail build for test code SpotBugs violations (just report them)
tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsTest").configure {
    ignoreFailures = true
}

// Token Hash Generator task for http-header-generator.sh
// Uses environment variables to avoid exposing secrets in process listings and build logs
tasks.register<JavaExec>("runTokenHashGenerator") {
    group = "application"
    description = "Runs the TokenHashGenerator utility to create token hashes"
    mainClass.set("com.davidparry.agent.security.TokenHashGenerator")
    classpath = sourceSets["main"].runtimeClasspath

    args = listOf(
        System.getenv("TOKEN_HASH_TOKEN") ?: "",
        System.getenv("TOKEN_HASH_CUSTOMER_ID") ?: "",
        System.getenv("TOKEN_HASH_SECRET") ?: "",
        System.getenv("TOKEN_HASH_SECRET_VERSION") ?: "V1"
    )
}

testSets {
    create("integrationTest")
}

tasks.named<Test>("integrationTest") {
    useJUnitPlatform()
}