// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.patches.syncforreddit.misc.fixredgifs

import app.edbfi.patches.shared.isDeclaredIn
import app.edbfi.patches.shared.uniqueMethod
import app.revanced.patcher.custom
import app.revanced.patcher.patch.BytecodePatchContext

/** The three Volley requests the app makes to Redgifs, in order, and the URL each holds. */
private val REDGIFS_REQUESTS =
    mapOf(
        "OAuthRedgifTokenRequest.java" to "https://api.redgifs.com/v2/oauth/client",
        "GrabIpRequest.java" to "https://api.redgifs.com/info",
        "GrabRedgifRequest.java" to "https://api.redgifs.com/v2/gifs/",
    )

/** Fails unless each of the app's Redgifs requests still holds the URL the extension answers. */
internal fun BytecodePatchContext.checkRedgifsRequests() {
    REDGIFS_REQUESTS.forEach { (sourceFile, url) ->
        uniqueMethod("the Redgifs request in $sourceFile", url) {
            custom { isDeclaredIn(this, sourceFile) }
        }
    }
}
