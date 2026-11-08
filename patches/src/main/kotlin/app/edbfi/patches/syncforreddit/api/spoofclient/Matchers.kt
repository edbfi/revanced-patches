// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.patches.syncforreddit.api.spoofclient

import app.edbfi.patches.shared.isDeclaredIn
import app.edbfi.patches.shared.stringLiteral
import app.edbfi.patches.shared.uniqueMethod
import app.revanced.patcher.MutablePredicateList
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.predicate
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method

internal const val SYNC_REDIRECT_URI = "http://redditsync/auth"
internal const val SSL_HOST = "https://ssl.reddit.com/"
internal const val AUTHORIZE_PREFIX = "${SSL_HOST}api/v1/authorize.compact?"
private const val SYNC_USER_AGENT_PREFIX = "android:com.laurencedawson.reddit_sync:"

/** `OAuthLoginHelper` returns the redirect URI used by the authorize URL, the WebView catch and the token exchange. */
internal fun BytecodePatchContext.redirectUriMethod() = uniqueMethod("the OAuth redirect URI method", SYNC_REDIRECT_URI) {
    publicStaticNoArgs("Ljava/lang/String;")
    predicate { isDeclaredIn(this, "OAuthLoginHelper.java") }
}

/** `OAuthLoginHelper` builds the Basic authorization header every token request sends. */
internal fun BytecodePatchContext.basicAuthMethod() = uniqueMethod("the OAuth Basic authorization method") {
    publicStaticNoArgs("Ljava/lang/String;")
    predicate { isDeclaredIn(this, "OAuthLoginHelper.java") }
    predicate { holdsString { it.startsWith("Basic ") } }
}

/** `OAuthLoginHelper` builds the authorize URL that carries the client ID. */
internal fun BytecodePatchContext.authorizeUrlMethod() = uniqueMethod("the OAuth authorize URL method") {
    predicate { AccessFlags.STATIC.isSet(accessFlags) && returnType == "Ljava/lang/String;" }
    predicate { parameterTypes.map(CharSequence::toString) == listOf("Landroid/content/Context;") }
    predicate { isDeclaredIn(this, "OAuthLoginHelper.java") }
    predicate { holdsString { it.startsWith(AUTHORIZE_PREFIX) && "client_id=" in it } }
}

/** `AccountSingleton` returns the User-Agent every Reddit request sends. */
internal fun BytecodePatchContext.userAgentMethod() = uniqueMethod("the Reddit User-Agent method") {
    publicStaticNoArgs("Ljava/lang/String;")
    predicate { isDeclaredIn(this, "AccountSingleton.java") }
    predicate { holdsString { it.startsWith(SYNC_USER_AGENT_PREFIX) } }
}

private fun MutablePredicateList<Method>.publicStaticNoArgs(returns: String) = predicate {
    AccessFlags.PUBLIC.isSet(accessFlags) &&
        AccessFlags.STATIC.isSet(accessFlags) &&
        parameterTypes.isEmpty() &&
        returnType == returns
}

internal fun Method.holdsString(test: (String) -> Boolean) = implementation?.instructions?.any { instruction -> instruction.stringLiteral?.let(test) == true } == true
