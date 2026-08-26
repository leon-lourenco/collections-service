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
    // The initiative-wide plan is for this catalog to come from the `card-billing-shared`
    // submodule (`from(files("card-billing-shared/versions/libs.versions.toml"))`). That repo
    // does not exist yet, and a submodule pointing at a missing remote makes this repo
    // unclonable, so the catalog lives here for now with the exact versions ARCHITECTURE.md
    // pins. Swapping to the submodule is a two-line change once the shared repo is published.
}
