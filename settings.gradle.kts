pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "server-agents"

// Include all Gradle subprojects
include("agent-message-protocol")
include("spring-agents")
include("agent-sdk")
include("agent-app")
include("admin-client-spring-agents")
include("admin-client-spring-agents:admin-ui")
