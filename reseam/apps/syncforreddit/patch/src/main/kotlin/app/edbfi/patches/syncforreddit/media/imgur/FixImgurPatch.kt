// SPDX-License-Identifier: AGPL-3.0-or-later

package app.edbfi.patches.syncforreddit.media.imgur

import app.edbfi.extension.shared.imgur.bindings.Imgur
import app.edbfi.patches.syncforreddit.SyncPatchStrings
import app.edbfi.patches.syncforreddit.layout.glideImageRequestUrl
import app.edbfi.patches.syncforreddit.layout.thumbnails.postThumbnail
import app.edbfi.patches.syncforreddit.syncForReddit
import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.dex.AccessFlags
import app.reseam.patch.dex.stringValue
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.replace

private const val UPLOAD_PROXY = "https://imgur-apiv3.p.rapidapi.com/3/image"
private const val COVER_PROXY = "https://ap.syncforreddit.com/image?url=imgur-album-"

private val album = klass("Imgur album request") {
    extends("com.android.volley.Request")
    sourceFile("ImgurGalleryRequest.java")
}
private val image = klass("Imgur image request") {
    extends("com.android.volley.Request")
    sourceFile("ImgurSingleImageRequest.java")
}
private val upload = klass("Imgur upload request") {
    extends("com.android.volley.Request")
    sourceFile("UploadImgurRequest.java")
}

private val albumUrl = method("Imgur album URL builder") {
    inClass(album)
    flags(AccessFlags.STATIC)
    params(Type.String)
    returns(Type.String)
}

private val imageUrl = method("Imgur single image URL builder") {
    inClass(image)
    flags(AccessFlags.STATIC)
    params(Type.String)
    returns(Type.String)
}

private val uploadConstructor = method("Imgur upload proxy request constructor") {
    inClass(upload)
    name("<init>")
    strings(UPLOAD_PROXY)
}

private val commentPreview = method("Imgur comment cover model") {
    strings(COVER_PROXY, "EfficientImageSpan")
    returns(Type.Void)
}

val fixImgur = patch(SyncPatchStrings.fixImgur) {
    description(SyncPatchStrings.fixImgurDescription)
    compatibleWith(syncForReddit)
    enabledByDefault(false)
    val clientId = stringOption(
        "imgurClientId",
        SyncPatchStrings.imgurClientId,
        SyncPatchStrings.imgurClientIdDescription,
        required = true,
    )

    execute {
        val id = options[clientId].trim()
        require(id.matches(Regex("[A-Za-z0-9]{1,64}"))) { "Enter an authorized Imgur client ID." }

        val headers = listOf(album, image, upload).map { owner ->
            owner.method("getHeaders") {
                params()
                returns("java.util.Map")
            }.also { it.method }
        }
        val uploadMethod = uploadConstructor.method
        val commentMethod = commentPreview.method
        check(uploadMethod.instructions.count { it.stringValue == UPLOAD_PROXY } == 1)
        check(commentMethod.instructions.count { it.stringValue == COVER_PROXY } == 1)
        albumUrl.method
        imageUrl.method
        postThumbnail.method
        glideImageRequestUrl.index

        albumUrl.replace { returnValue(Imgur.albumUrl(param(0))) }
        imageUrl.replace { returnValue(Imgur.imageUrl(param(0))) }
        headers.forEach { target ->
            target.after {
                val url = thisObject.callVirtual("com.android.volley.Request", "getUrl", "()Ljava/lang/String;")
                result.assign(Imgur.headers(url, result, string(id)))
            }
        }
        uploadMethod.replaceAllStrings(UPLOAD_PROXY, "https://api.imgur.com/3/image")
        commentMethod.replaceAllStrings(COVER_PROXY, "https://imgur.com/a/")
        postThumbnail.after { result.assign(Imgur.postCoverModel(param(0), result)) }
        glideImageRequestUrl.before {
            val url = capture("url")
            url.assign(Imgur.resolveCoverModel(url, string(id)))
        }
    }
}
