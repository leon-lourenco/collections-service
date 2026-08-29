import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    alias(libs.plugins.spring.boot)
}

group = "com.cardbilling"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Spring Boot's BOM applied directly instead of via the io.spring.dependency-management
    // plugin: one less plugin, and platform() is what Gradle's own dependency locking and
    // configuration cache understand natively.
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // Boot 4.1 no longer auto-configures RestClient just because spring-web is present; the
    // outbound HTTP client is its own module now. This service is a client of two other services,
    // so it needs it explicitly.
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Resilience4j's annotations (@CircuitBreaker, @Retry) are AspectJ-style pointcuts applied
    // through Spring AOP proxies, so the aspectj starter is not optional here.
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.resilience4j.spring.boot)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Boot 4.1 split MockMvc's test support out of spring-boot-test-autoconfigure into its own
    // module; the plain test starter no longer carries @AutoConfigureMockMvc.
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(libs.wiremock.standalone)
    testImplementation(libs.testcontainers.redis)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        // ./gradlew test -PtestOutput surfaces the application's own logs from inside the test
        // JVM - the fastest way to see what a resilience fallback actually decided.
        showStandardStreams = providers.gradleProperty("testOutput").isPresent
    }
}
