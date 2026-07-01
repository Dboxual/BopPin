plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.0.0-rc1"
}

group = "dev.dkocaj.boppin"
version = "0.1.4"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.47-alpha")
    // sqlite-jdbc is NOT bundled. Paper resolves it at runtime via the
    // `libraries:` block in plugin.yml — relocating sqlite-jdbc breaks JNI.
    compileOnly("org.xerial:sqlite-jdbc:3.50.3.0")
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    // No relocations: sqlite-jdbc must NOT be relocated (JNI symbols are tied
    // to org.sqlite.core.NativeDB) and we're not bundling it anyway.
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
