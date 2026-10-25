plugins {
    java
    id("org.springframework.boot") version "4.0.8"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.spotbugs") version "6.5.12"
    jacoco
    id("com.diffplug.spotless") version "8.10.3"
}

group = "io.shortly"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

extra["resilience4jVersion"] = "2.4.0"
extra["findsecbugsVersion"] = "1.14.0"
extra["spotbugsVersion"] = "4.10.4"
extra["jacocoVersion"] = "0.8.15"
extra["googleJavaFormatVersion"] = "1.36.1"

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()
            targets.configureEach {
                testTask.configure {
                    shouldRunAfter(tasks.named("test"))
                    finalizedBy("jacocoTestReport")
                }
            }
        }
    }
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.testImplementation.get())
}
configurations.named("integrationTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("io.github.resilience4j:resilience4j-spring-boot4:${property("resilience4jVersion")}")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    "integrationTestImplementation"(project())
    "integrationTestImplementation"("org.testcontainers:testcontainers-postgresql")
    "integrationTestImplementation"("org.testcontainers:testcontainers-kafka")

    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:${property("findsecbugsVersion")}")
}

val analysisFailsBuild = providers.gradleProperty("analysisReportOnly").isPresent.not()

spotbugs {
    toolVersion = property("spotbugsVersion") as String
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
    excludeFilter = file("config/spotbugs/exclude.xml")
    ignoreFailures = !analysisFailsBuild
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    enabled = name == "spotbugsMain"
    reports.create("html") { required = true }
}

jacoco {
    toolVersion = property("jacocoVersion") as String
}

tasks.named<JacocoReport>("jacocoTestReport") {
    executionData.setFrom(layout.buildDirectory.files("jacoco/test.exec", "jacoco/integrationTest.exec"))
    mustRunAfter("test", "integrationTest")
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}

tasks.named("test") {
    finalizedBy("jacocoTestReport")
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat(property("googleJavaFormatVersion") as String).aosp()
        removeUnusedImports()
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-Xshare:off")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") { archiveFileName.set("app.jar") }
