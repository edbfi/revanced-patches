// SPDX-License-Identifier: AGPL-3.0-only
extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    // Sync's own minSdk.
    defaultConfig.minSdk = 23
}

dependencies {
    compileOnly(project(":extensions:syncforreddit:stub"))
    testImplementation(libs.junit)
}
