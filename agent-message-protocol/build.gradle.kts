plugins {
    id("java-library")
    id("maven-publish")
    id("jacoco")
}

group = project.findProperty("group") as String? ?: "com.davidparry.agent"
version = project.findProperty("version") as String? ?: "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // Jackson for JSON serialization
    api("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
    
    // Testing
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Publishing configuration
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            
            groupId = "com.davidparry.agent"
            artifactId = "message-protocol"
            version = project.version.toString()
            
            pom {
                name.set("Agent Message Protocol")
                description.set("Shared message protocol library for agent WebSocket communication")
                url.set("https://github.com/David-Parry/agent-message-protocol")
                
                licenses {
                    license {
                        name.set("GNU Affero General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/agpl-3.0.html")
                    }
                }
                
                developers {
                    developer {
                        id.set("davidparry")
                        name.set("David Parry")
                        email.set("d.dry.parry@gmail.com")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/David-Parry/agent-message-protocol.git")
                    developerConnection.set("scm:git:ssh://github.com/David-Parry/agent-message-protocol.git")
                    url.set("https://github.com/David-Parry/agent-message-protocol")
                }
            }
        }
    }
    
    repositories {
        mavenLocal()
    }
}
