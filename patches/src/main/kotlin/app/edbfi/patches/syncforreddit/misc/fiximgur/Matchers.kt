// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.patches.syncforreddit.misc.fiximgur

import app.edbfi.patches.shared.isDeclaredIn
import app.edbfi.patches.shared.uniqueMethod
import app.edbfi.patches.syncforreddit.misc.extension.EXTENSION
import app.revanced.patcher.accessFlags
import app.revanced.patcher.custom
import app.revanced.patcher.definingClass
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import com.android.tools.smali.dexlib2.AccessFlags

/** The app's Imgur requests and the URL each holds: album and single image through the mirror, and the upload. */
private val IMGUR_REQUESTS =
    mapOf(
        "ExternalGallery.java" to "https://images.syncforreddit.com/a/",
        "SingleImage.java" to "https://images.syncforreddit.com/image/",
        "UploadImgurRequest.java" to "https://imgur-apiv3.p.rapidapi.com/3/image",
    )

/** Fails unless each of the app's Imgur requests still holds the URL the extension answers. */
internal fun BytecodePatchContext.checkImgurRequests() {
    IMGUR_REQUESTS.forEach { (sourceFile, url) ->
        uniqueMethod("the Imgur URL in $sourceFile", url) {
            custom { isDeclaredIn(this, sourceFile) }
        }
    }
}

/**
 * `ImgurImageHelper` turns one path segment of an Imgur link into the ID: it cuts at `#`, `?`, `&`, `.` and `-`, and
 * logs "Error: " if that fails.
 */
internal fun BytecodePatchContext.imgurIdMethod() = uniqueMethod("the Imgur ID parser", "Error: ") {
    accessFlags(AccessFlags.PRIVATE, AccessFlags.STATIC)
    returnType("Ljava/lang/String;")
    parameterTypes("Ljava/lang/String;")
    custom { isDeclaredIn(this, "ImgurImageHelper.java") }
}

/** The extension method the "Imgur client ID" option goes in. */
internal fun BytecodePatchContext.clientIdMethod() = uniqueMethod("Imgur.clientId in the extension") {
    definingClass("$EXTENSION/Imgur;")
    name("clientId")
    returnType("Ljava/lang/String;")
}
