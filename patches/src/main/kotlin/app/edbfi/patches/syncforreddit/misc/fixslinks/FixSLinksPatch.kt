// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.patches.syncforreddit.misc.fixslinks

import app.edbfi.patches.syncforreddit.SYNC_PACKAGE
import app.edbfi.patches.syncforreddit.SYNC_VERSION
import app.edbfi.patches.syncforreddit.api.spoofclient.userAgentMethod
import app.edbfi.patches.syncforreddit.misc.extension.EXTENSION
import app.edbfi.patches.syncforreddit.misc.extension.extensionPatch
import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val fixSLinksPatch =
    bytecodePatch(
        name = "Fix /s/ links",
        description = "Opens Reddit share links (/s/) as the post they point to, from the app and from other apps.",
    ) {
        compatibleWith(SYNC_PACKAGE(SYNC_VERSION))
        dependsOn(extensionPatch)

        apply {
            // The redirect is resolved with the User-Agent the app sends to Reddit, Spoof client's if applied.
            val userAgent = userAgentMethod()
            val router = linkRouterMethod()
            router.addInstructionsWithLabels(
                0,
                """
                    invoke-static { }, ${userAgent.definingClass}->${userAgent.name}()Ljava/lang/String;
                    move-result-object v0
                    invoke-static { p0, p3, v0 }, $EXTENSION/ShareLinks;->open(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Z
                    move-result v0
                    if-eqz v0, :original
                    return v0
                """,
                ExternalLabel("original", router.getInstruction(0)),
            )
        }
    }
