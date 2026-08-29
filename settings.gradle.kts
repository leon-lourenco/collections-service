plugins {
    // Lets Gradle download the Java 21 toolchain itself when the machine only has a different
    // JDK installed, so the build produces the same bytecode everywhere instead of silently
    // compiling against whatever JDK happens to be on PATH.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "collections-service"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("card-billing-shared/versions/libs.versions.toml"))
        }
    }
}
