// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.patches.syncforreddit.misc.extension

import app.edbfi.patches.shared.uniqueMethod
import app.revanced.patcher.definingClass
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.name
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.returnType

/** The package of the Java the extension adds to the app, as a type descriptor prefix. */
internal const val EXTENSION = "Lapp/edbfi/extension/syncforreddit"

/** Adds the extension's classes to the app. Patches that call into it depend on this one. */
internal val extensionPatch =
    bytecodePatch {
        extendWith("extensions/syncforreddit.rve")
    }

/** Makes the extension class's `isEnabled()`, false as built, return true. */
internal fun BytecodePatchContext.enableInExtension(className: String) {
    uniqueMethod("$className.isEnabled in the extension") {
        definingClass("$EXTENSION/$className;")
        name("isEnabled")
        returnType("Z")
    }.addInstructions(
        0,
        """
            const/4 v0, 0x1
            return v0
        """,
    )
}
