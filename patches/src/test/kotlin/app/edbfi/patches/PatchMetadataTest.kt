// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.patches

import app.revanced.patcher.patch.Patch
import app.revanced.patcher.patch.loadPatches
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PatchMetadataTest {
    // loadPatches swallows load failures by default, which would pass an unloadable bundle as empty.
    private val patches: Set<Patch> =
        loadPatches(File(System.getProperty("patches.bundle"))) { file, error ->
            throw AssertionError("Couldn't load patches from $file", error)
        }

    @Test
    fun `every patch has a description`() {
        val missing = patches.filter { it.description.isNullOrBlank() }.map { it.name }
        assertTrue(missing.isEmpty(), "Patches without a description: $missing")
    }

    @Test
    fun `every patch declares the apps it is compatible with`() {
        val missing = patches.filter { it.compatiblePackages.isNullOrEmpty() }.map { it.name }
        assertTrue(missing.isEmpty(), "Patches without compatibleWith: $missing")
    }

    @Test
    fun `patch names are unique within each app`() {
        val duplicates =
            patches
                .flatMap { patch -> patch.compatiblePackages.orEmpty().map { (pkg, _) -> pkg to patch.name } }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        assertTrue(duplicates.isEmpty(), "Patch names used twice for the same app: $duplicates")
    }
}
