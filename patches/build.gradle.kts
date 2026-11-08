// SPDX-License-Identifier: AGPL-3.0-only
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.ktlint)
}

group = "app.edbfi"

patches {
    about {
        name = "edbfi patches"
        description = "ReVanced patches by edbfi"
        source = "https://github.com/edbfi/revanced-patches"
        author = "edbfi"
        contact = "https://github.com/edbfi/revanced-patches/issues"
        website = "https://github.com/edbfi/revanced-patches"
        license = "GNU Affero General Public License v3.0"
    }
}

java {
    // The plugin sets only the target; without this, Java sources would compile as the daemon's JDK 25.
    sourceCompatibility = JavaVersion.VERSION_17
}

kotlin {
    // The plugin turns ABI validation on. Nothing consumes this bundle's Kotlin API, so there is
    // no reference dump to check against.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation { enabled.set(false) }
}

ktlint {
    version = libs.versions.ktlint
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // Load the bundle the way ReVanced Manager and the CLI do: from the built jar.
    val bundle = tasks.jar.flatMap { it.archiveFile }
    inputs.file(bundle)
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dpatches.bundle=${bundle.get().asFile}") })
}
