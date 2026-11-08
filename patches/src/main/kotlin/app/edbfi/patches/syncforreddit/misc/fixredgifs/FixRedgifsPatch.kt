// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.patches.syncforreddit.misc.fixredgifs

import app.edbfi.patches.syncforreddit.SYNC_PACKAGE
import app.edbfi.patches.syncforreddit.SYNC_VERSION
import app.edbfi.patches.syncforreddit.misc.extension.enableInExtension
import app.edbfi.patches.syncforreddit.misc.volley.volleyHookPatch
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val fixRedgifsPatch =
    bytecodePatch(
        name = "Fix Redgifs",
        description = "Fixes Redgifs videos not loading.",
    ) {
        compatibleWith(SYNC_PACKAGE(SYNC_VERSION))
        dependsOn(volleyHookPatch)

        apply {
            checkRedgifsRequests()
            enableInExtension("Redgifs")
        }
    }
