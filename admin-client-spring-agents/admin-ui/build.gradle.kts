plugins {
    base
    id("com.github.node-gradle.node") version "7.1.0"
}

node {
    // Node.js version to use
    version.set("20.18.0")
    // Download Node.js automatically
    download.set(true)
    // Directory where node_modules will be located
    nodeProjectDir.set(file(projectDir))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
    dependsOn("npmInstall")
    args.set(listOf("run", "build"))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmDev") {
    dependsOn("npmInstall")
    args.set(listOf("run", "dev"))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmLint") {
    dependsOn("npmInstall")
    args.set(listOf("run", "lint"))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmTest") {
    dependsOn("npmInstall")
    args.set(listOf("run", "test"))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmCoverage") {
    dependsOn("npmInstall")
    args.set(listOf("run", "coverage"))
    environment.set(mapOf("COVERAGE_PHASE" to ((findProperty("coveragePhase") as String?) ?: "baseline")))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmStart") {
    dependsOn("npmBuild")
    args.set(listOf("run", "start"))
}

// Wire up standard Gradle lifecycle tasks
tasks.named("build") {
    dependsOn("npmBuild")
}

tasks.named("check") {
    dependsOn("npmTest")
}
