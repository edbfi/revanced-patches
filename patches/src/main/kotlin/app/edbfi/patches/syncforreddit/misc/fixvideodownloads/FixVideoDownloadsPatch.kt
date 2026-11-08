// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.patches.syncforreddit.misc.fixvideodownloads

import app.edbfi.patches.syncforreddit.SYNC_PACKAGE
import app.edbfi.patches.syncforreddit.SYNC_VERSION
import app.edbfi.patches.syncforreddit.misc.extension.EXTENSION
import app.edbfi.patches.syncforreddit.misc.extension.extensionPatch
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.instructions
import app.revanced.patcher.extensions.methodReference
import app.revanced.patcher.extensions.string
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val fixVideoDownloadsPatch =
    bytecodePatch(
        name = "Fix video downloads",
        description = "Fixes downloaded Reddit videos having only audio.",
    ) {
        compatibleWith(SYNC_PACKAGE(SYNC_VERSION))
        dependsOn(extensionPatch)

        apply {
            val method = manifestParserMethod()
            val code = method.instructions
            // For each AdaptationSet: getTextContent(); contains("DASH_audio"); move-result vX; if-nez vX, <audio>.
            // v.redd.it now names its audio files CMAF_AUDIO_*.mp4, so the audio set is read as the video.
            val literal = code.indices.singleOrNull { code[it].string == "DASH_audio" }
                ?: throw PatchException("Expected one \"DASH_audio\" literal in ${method.name}")
            val text = code.getOrNull(literal - 2)
            val contains = code.getOrNull(literal + 1)?.methodReference
            val result = code.getOrNull(literal + 2)
            val branch = literal + 3
            if (text?.methodReference?.name != "getTextContent" ||
                contains?.name != "contains" ||
                result?.opcode != Opcode.MOVE_RESULT ||
                code.getOrNull(branch)?.opcode != Opcode.IF_NEZ
            ) {
                throw PatchException("The audio check in ${method.name} has changed")
            }
            if (code[branch].location.labels.isNotEmpty()) throw PatchException("A branch enters the audio check")
            val set = (text as FiveRegisterInstruction).registerC
            val isAudio = (result as OneRegisterInstruction).registerA

            // The branch now follows the manifest's own contentType or mimeType.
            method.addInstructions(
                branch,
                """
                    invoke-static/range { v$set .. v$set }, $EXTENSION/Mpd;->isAudio(Lorg/w3c/dom/Element;)Z
                    move-result v$isAudio
                """,
            )
        }
    }
