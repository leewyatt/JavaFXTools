plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

val platformType: String by project
val platformVersion: String by project
val pluginGroup: String by project
val pluginVersion: String by project
val javaVersion: String by project

group = pluginGroup
version = pluginVersion

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(platformType, platformVersion)

        bundledPlugins(
            "com.intellij.java",
            "com.intellij.modules.xml",
            "com.intellij.properties"
        )
    }

    // MigLayout Swing
    implementation("com.miglayout:miglayout-swing:5.3")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

java {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
    }

    test {
        useJUnitPlatform()
    }

    patchPluginXml {
        sinceBuild.set("233")
    }

    buildSearchableOptions {
        enabled = false
    }
}
