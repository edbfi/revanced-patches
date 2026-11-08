// SPDX-License-Identifier: AGPL-3.0-only
extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    // Sync's own minSdk.
    defaultConfig.minSdk = 23
    // Lets unit tests reach code that logs with android.util.Log.
    testOptions.unitTests.isReturnDefaultValues = true
}

// AGP's built-in Kotlin adds kotlin-stdlib to every Android module; this one is plain Java.
configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
}

dependencies {
    compileOnly(project(":extensions:syncforreddit:stub"))
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
