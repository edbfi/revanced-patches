// SPDX-License-Identifier: AGPL-3.0-or-later

package app.edbfi.patches.syncforreddit.layout.gfycat

import app.edbfi.patches.syncforreddit.SyncPatchStrings
import app.edbfi.patches.syncforreddit.syncForReddit
import app.reseam.patch.Type
import app.reseam.patch.before
import app.reseam.patch.dex.Opcode
import app.reseam.patch.dex.typeRef
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point

private val isGfycatLink = method("Gfycat link classifier") {
    params(Type.String)
    returns(Type.Boolean)
    strings("gfycat.com", "http://gfycat.com/", "https://gfycat.com/")
}

private val postMedia = method("Post media parser") {
    strings("https://thumbs.gfycat.com/", "-poster.jpg", "USING YOUTUBE")
    calls(isGfycatLink)
}

private val commentLink = method("Comment link renderer") {
    strings("tenor.com", ".jpeg", "Is user link: ")
    calls(isGfycatLink)
}

val hideGfycatLinkPreviews = patch(SyncPatchStrings.hideGfycatLinkPreviews) {
    description(SyncPatchStrings.hideGfycatLinkPreviewsDescription)
    compatibleWith(syncForReddit)

    execute {
        val postCheck = postMedia.point { calls(isGfycatLink) }.captureArgumentAs("url", 0)
        // The app has no stable names for its media model. Resolve the plain-link model and
        // URL field from the parser's existing plain-link path.
        val plainLink = postMedia.point { string("mediadownloads.mlb.com") }
            .next { opcode(Opcode.NEW_INSTANCE) }
        val plainLinkType = checkNotNull(plainLink.instruction.typeRef)
        val mediaUrl = plainLink.next { opcode(Opcode.IPUT_OBJECT) }.field()

        val commentPreviewBranch = commentLink.point {
            invokeVirtual { owner(Type.String); name("contains") }
            argument(1) { string("tenor.com") }
        }.captureArgumentAs("url", 0)
            .next {
                resultOf(Type.Boolean)
                then { opcode(Opcode.IF_EQZ) }
                then { opcode(Opcode.MOVE) }
                then { opcode(Opcode.IF_EQZ) }
            }
            .captureAs("showPreview", Type.Boolean)

        // Resolve both hooks before editing either method.
        postCheck.index
        commentPreviewBranch.index
        check(mediaUrl.type == Type.String)

        postCheck.before {
            val url = capture("url")
            whenTrue(call(isGfycatLink, url)) {
                val link = newInstance(plainLinkType)
                link.set(mediaUrl, url)
                returnValue(link)
            }
        }
        commentPreviewBranch.before {
            whenTrue(call(isGfycatLink, capture("url"))) {
                capture("showPreview").assign(bool(false))
            }
        }
    }
}
