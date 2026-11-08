// SPDX-License-Identifier: AGPL-3.0-only
rootProject.name = "revanced-patches"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven {
            // ReVanced publishes its Gradle plugin and patcher here; reading needs a token with read:packages.
            name = "githubPackages"
            url = uri("https://maven.pkg.github.com/revanced/registry")
            credentials(PasswordCredentials::class)
        }
    }
}

plugins {
    id("app.revanced.patches") version "1.0.0-dev.11"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

settings {
    extensions {
        defaultNamespace = "app.edbfi.extension"
    }
}
