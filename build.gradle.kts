plugins {
    idea
}

// Shared configuration for all subprojects
subprojects {
    repositories {
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
