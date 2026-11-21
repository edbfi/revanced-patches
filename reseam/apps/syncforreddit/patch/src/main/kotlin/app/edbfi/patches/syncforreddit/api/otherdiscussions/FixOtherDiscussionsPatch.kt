// SPDX-License-Identifier: AGPL-3.0-or-later
package app.edbfi.patches.syncforreddit.api.otherdiscussions

import app.edbfi.patches.syncforreddit.SyncPatchStrings
import app.edbfi.patches.syncforreddit.syncForReddit
import app.reseam.patch.dex.AccessFlags
import app.reseam.patch.method
import app.reseam.patch.patch

private const val STRING = "Ljava/lang/String;"
private const val SEEN_PREFIX = "seen###"

private val discussionRoute = method("Other discussions route builder") {
    strings(SEEN_PREFIX)
    params(STRING)
    returns(STRING)
    flags(AccessFlags.STATIC)
    calls {
        owner("Ljava/net/URLEncoder;")
        name("encode")
    }
}

val fixOtherDiscussions = patch(SyncPatchStrings.fixOtherDiscussions) {
    description(SyncPatchStrings.fixOtherDiscussionsDescription)
    compatibleWith(syncForReddit)

    execute {
        // Keep the stock encoder and send both Other discussions entry points through global search.
        check(discussionRoute.method.replaceAllStrings(SEEN_PREFIX, "search######url%3A") == 1) {
            "Expected one Other discussions route prefix"
        }
    }
}
