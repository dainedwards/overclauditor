plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val type = providers.gradleProperty("platformType")
        val version = providers.gradleProperty("platformVersion")
        create(type, version)

        bundledPlugin("org.jetbrains.plugins.terminal")

        pluginVerifier()
    }

    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)

    testImplementation(kotlin("test"))
}

intellijPlatform {
    pluginConfiguration {
        id = "com.overclauditor"
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        // Change notes from CHANGELOG env var (set by CI), or fallback to plugin.xml static content
        val changelogEnv = providers.environmentVariable("CHANGELOG")
        if (changelogEnv.isPresent && changelogEnv.get().isNotBlank()) {
            changeNotes = changelogEnv
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    buildSearchableOptions = false

    // ./gradlew verifyPlugin checks the built plugin against real IDE builds for missing,
    // removed or internal APIs. -PverifyIde=<path to an installed IDE> verifies against that
    // install (no download); otherwise the oldest supported build, downloaded.
    pluginVerification {
        ides {
            val localIde = providers.gradleProperty("verifyIde").orNull
            if (localIde != null) local(file(localIde))
            else ide(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, providers.gradleProperty("platformVersion").get())
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "8.10.2"
    }
}
