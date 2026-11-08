// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.patches.syncforreddit.ads.disableads

import app.edbfi.patches.syncforreddit.SYNC_PACKAGE
import app.edbfi.patches.syncforreddit.SYNC_VERSION
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val ADS_INIT_PROVIDER = "com.google.android.gms.ads.MobileAdsInitProvider"

// The Google Mobile Ads SDK starts itself from this provider at process start, before any check Sync makes.
private val removeAdsInitProviderPatch =
    resourcePatch {
        apply {
            document("AndroidManifest.xml").use { manifest ->
                val providers = manifest.getElementsByTagName("provider")
                val matches =
                    (0 until providers.length)
                        .map { providers.item(it) as Element }
                        .filter { it.getAttribute("android:name") == ADS_INIT_PROVIDER }
                if (matches.size != 1) throw PatchException("Expected one $ADS_INIT_PROVIDER, found ${matches.size}")
                matches.single().let { it.parentNode.removeChild(it) }
            }
        }
    }

@Suppress("unused")
val disableAdsPatch =
    bytecodePatch(
        name = "Disable ads",
        description = "Disables ads and the ad consent form.",
    ) {
        compatibleWith(SYNC_PACKAGE(SYNC_VERSION))
        dependsOn(removeAdsInitProviderPatch)

        apply {
            // Every ad path checks this method: SDK setup, banners, feed and gallery ads, and the consent form.
            adsEnabledMethod().addInstructions(
                0,
                """
                    const/4 v0, 0x0
                    return v0
                """,
            )
        }
    }
