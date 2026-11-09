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

    /**
     * Rows of the first table after the line [heading], within [lines], cells trimmed, once its header row is [header]
     * and a separator row follows (else GitHub doesn't render a table). Cells are split on " | ", so none may hold a pipe.
     */
    private fun table(heading: String, header: List<String>, lines: List<String> = readme): List<List<String>> {
        val start = lines.indexOf(heading).takeIf { it >= 0 } ?: fail("README has no \"$heading\" where expected")
        val rows =
            lines.drop(start + 1)
                .takeWhile { !it.startsWith("#") || it.startsWith("|") }
                .dropWhile { !it.startsWith("|") }
                .takeWhile { it.startsWith("|") }
                .map { line -> line to line.trim().removePrefix("|").removeSuffix("|").split(" | ").map { it.trim() } }
        if (rows.firstOrNull()?.second != header) fail("The table under \"$heading\" must start with the header $header")
        if (rows.getOrNull(1)?.second?.let { cells -> cells.size == header.size && cells.all { it.matches(Regex(":?-{3,}:?")) } } != true) {
            fail("The table under \"$heading\" needs a separator row after its header")
        }
        return rows.drop(2).map { (line, cells) ->
            cells.takeIf { it.size == header.size } ?: fail("README table row under \"$heading\" needs ${header.size} cells: $line")
        }
    }

    /** The backticked values of a cell that holds nothing else: `a` or `a`, `b`. */
    private fun code(cell: String): List<String> {
        if (!cell.matches(Regex("`[^`]+`(, `[^`]+`)*"))) fail("Cell must be backticked values separated by \", \": $cell")
        return Regex("`([^`]+)`").findAll(cell).map { it.groupValues[1] }.toList()
            .also { if (it.distinct().size != it.size) fail("Cell lists a value twice: $cell") }
    }

    /** GitHub's anchor for a heading: lowercase, punctuation other than '-' and '_' dropped, spaces as '-'. */
    private fun anchor(heading: String) = heading.lowercase().filter { it.isLetterOrDigit() || it in " -_" }.replace(' ', '-')

    // Lazy, so a malformed table fails the test that reads it with its message, not the class's construction.
    private val apps by lazy {
        table("## Supported apps", listOf("App", "Package", "Tested version")).map { (app, pkg, versions) ->
            val link = Regex("""^\[(.+)]\(#(.+)\)$""").find(app) ?: fail("Not a link to the app's section: $app")
            val (name, target) = link.destructured
            if (target != anchor(name)) fail("The link to $name goes to #$target, but its heading's anchor is #${anchor(name)}")
            App(name, code(pkg).singleOrNull() ?: fail("Package cell needs one `package`: $pkg"), code(versions).toSet())
        }
    }

    /** The lines of the app's `### <name>` section, up to the next heading of that level or higher. */
    private fun section(app: App): List<String> {
        val start = readme.indexOf("### ${app.name}").takeIf { it >= 0 } ?: fail("README has no \"### ${app.name}\"")
        val end = readme.drop(start + 1).indexOfFirst { it.startsWith("## ") || it.startsWith("### ") }
        return readme.subList(start, if (end < 0) readme.size else start + 1 + end)
    }

    private fun patchesOf(app: App) = patches.filter { patch -> patch.compatiblePackages.orEmpty().any { it.first == app.pkg } }

    @Test
    fun `the apps table lists every app with the versions its patches were tested on`() {
        val bundle =
            patches
                .flatMap { it.compatiblePackages.orEmpty() }
                .groupBy({ it.first }, { it.second.orEmpty() })
                .mapValues { (_, versions) -> versions.flatten().toSet() }
        assertEquals(apps.map { it.pkg }.distinct().size, apps.size, "Supported apps lists an app twice")
        // Same-named sections would get anchors GitHub suffixes with -1, which the link check can't follow.
        assertEquals(apps.map { it.name }.distinct().size, apps.size, "Supported apps uses a name twice")
        assertEquals(bundle, apps.associate { it.pkg to it.versions })
    }

    @Test
    fun `each app's patch table matches its patches`() {
        for (app in apps) {
            val rows =
                table("#### Patches", listOf("Patch", "What it does", "Default"), section(app)).map { (name, description, default) ->
                    Row(
                        name,
                        description,
                        when (default) {
                            "On" -> true
                            "Off" -> false
                            else -> fail("${app.name}: Default must be On or Off, not \"$default\" ($name)")
                        },
                    )
                }
            val bundle = patchesOf(app).map { Row(it.name!!, it.description.orEmpty(), it.use) }
            assertEquals(bundle.size, rows.size, "${app.name}: one README row per patch")
            assertEquals(bundle.toSet(), rows.toSet(), "${app.name}: README patch table")
        }
    }

    @Test
    fun `each app's options are named in its section`() {
        for (app in apps) {
            val text = section(app).joinToString("\n")
            val missing = patchesOf(app).flatMap { it.options.values.map { option -> option.name } }.filter { "`$it`" !in text }
            assertTrue(missing.isEmpty(), "${app.name}: options not named in backticks in the README: $missing")
        }
    }
}
