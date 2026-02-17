plugins {
    idea
}

// Shared configuration for all subprojects
subprojects {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

// IntelliJ IDEA configuration
idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}
