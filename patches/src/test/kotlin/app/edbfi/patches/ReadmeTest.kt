// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.patches

import app.revanced.patcher.patch.Patch
import app.revanced.patcher.patch.loadPatches
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** The README's app and patch tables state facts the bundle holds; this keeps them equal. */
class ReadmeTest {
    private val patches: Set<Patch> =
        loadPatches(File(System.getProperty("patches.bundle"))) { file, error ->
            throw AssertionError("Couldn't load patches from $file", error)
        }
    private val readme = File(System.getProperty("readme.file")).readLines()

    private data class App(val name: String, val pkg: String, val versions: Set<String>)

    private data class Row(val name: String, val description: String, val default: Boolean)

    /** Rows of the first table after [heading], cells trimmed, header and separator dropped. */
    private fun table(heading: String, from: Int = 0): List<List<String>> {
        val start = readme.drop(from).indexOf(heading).takeIf { it >= 0 }?.plus(from) ?: fail("README has no \"$heading\"")
        return readme.drop(start + 1)
            .dropWhile { !it.startsWith("|") }
            .takeWhile { it.startsWith("|") }
            .drop(2)
            .map { line -> line.trim().removePrefix("|").removeSuffix("|").split(" | ").map { it.trim() } }
    }

    private fun code(cell: String) = Regex("`([^`]+)`").findAll(cell).map { it.groupValues[1] }.toSet()

    private val apps =
        table("## Supported apps").map { (app, pkg, versions) ->
            val name = Regex("""^\[(.+)]\(#[a-z0-9-]+\)$""").find(app)?.groupValues?.get(1) ?: fail("Not an app link: $app")
            App(name, code(pkg).single(), code(versions))
        }

    @Test
    fun `the apps table lists every app with the versions its patches were tested on`() {
        val bundle =
            patches
                .flatMap { it.compatiblePackages.orEmpty() }
                .groupBy({ it.first }, { it.second.orEmpty() })
                .mapValues { (_, versions) -> versions.flatten().toSet() }
        assertEquals(bundle, apps.associate { it.pkg to it.versions })
    }

    @Test
    fun `each app's patch table matches its patches`() {
        for (app in apps) {
            val section = readme.indexOf("### ${app.name}").takeIf { it >= 0 } ?: fail("README has no \"### ${app.name}\"")
            val rows =
                table("#### Patches", section).map { (name, description, default) ->
                    Row(
                        name,
                        description,
                        when (default) {
                            "On" -> true
                            "Off" -> false
                            else -> fail("Default must be On or Off: $default")
                        },
                    )
                }
            val bundle =
                patches
                    .filter { patch -> patch.name != null && patch.compatiblePackages.orEmpty().any { it.first == app.pkg } }
                    .map { Row(it.name!!, it.description.orEmpty(), it.use) }
            assertEquals(bundle.size, rows.size, "${app.name}: one README row per patch")
            assertEquals(bundle.toSet(), rows.toSet(), "${app.name}: README patch table")
        }
    }

    @Test
    fun `each app's options are named in its section`() {
        for (app in apps) {
            val section = readme.indexOf("### ${app.name}")
            val end = readme.drop(section + 1).indexOfFirst { it.startsWith("## ") || it.startsWith("### ") }
            val text = readme.subList(section, if (end < 0) readme.size else section + 1 + end).joinToString("\n")
            val options =
                patches
                    .filter { patch -> patch.compatiblePackages.orEmpty().any { it.first == app.pkg } }
                    .flatMap { it.options.values.map { option -> option.name } }
            val missing = options.filter { "`$it`" !in text }
            assertTrue(missing.isEmpty(), "${app.name}: options not named in the README: $missing")
        }
    }
}
