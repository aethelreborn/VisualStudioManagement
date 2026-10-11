plugins {
    java
    `maven-publish`
}

group = property("group") as String
version = property("version") as String

val javaVersion = (property("javaVersion") as String).toInt()
val paperApiVersion = property("paperApiVersion") as String

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    withSourcesJar()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    // Runtime libraries, resolved by VsmLoader (Paper PluginLoader) at server start.
    // They are compile-time only here so nothing is shaded into the jar.
    compileOnly("com.zaxxer:HikariCP:6.3.0")
    compileOnly("org.xerial:sqlite-jdbc:3.50.3.0")
    compileOnly("com.mysql:mysql-connector-j:9.4.0")

    // Soft dependency: only needed to compile the PlaceholderAPI expansion.
    compileOnly("me.clip:placeholderapi:2.12.3")

    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
    options.compilerArgs.add("-Xlint:-options")
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand(
            "version" to project.version,
            "name" to "VisualStudioManagement",
            "description" to "Self-contained staff, moderation, IP-ban and movement-tools plugin.",
            "main" to "io.github.aethelreborn.vsm.VisualStudioManagement",
            "bootstrapper" to "io.github.aethelreborn.vsm.VsmBootstrap",
            "loader" to "io.github.aethelreborn.vsm.VsmLoader",
            "apiVersion" to paperApiVersion.substringBefore(".build"),
        )
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "VisualStudioManagement",
            "Implementation-Version" to project.version,
            "Specification-Version" to project.version,
        )
    }
}

tasks.build {
    dependsOn(tasks.jar)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}