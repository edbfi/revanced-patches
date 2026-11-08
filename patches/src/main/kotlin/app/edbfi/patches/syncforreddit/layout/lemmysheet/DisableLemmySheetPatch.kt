// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.patches.syncforreddit.layout.lemmysheet

import app.edbfi.patches.syncforreddit.SYNC_PACKAGE
import app.edbfi.patches.syncforreddit.SYNC_VERSION
import app.revanced.patcher.extensions.instructions
import app.revanced.patcher.extensions.removeInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

// The helper that shows a bottom sheet given its class.
private val SHOW_SHEET_PARAMETERS = listOf("Ljava/lang/Class;", "Landroidx/fragment/app/FragmentManager;")

@Suppress("unused")
val disableLemmySheetPatch =
    bytecodePatch(
        name = "Disable Sync for Lemmy bottom sheet",
        description = "Disables the bottom sheet that promotes Sync for Lemmy at every start.",
    ) {
        compatibleWith(SYNC_PACKAGE(SYNC_VERSION))

        apply {
            val sheet = lemmySheetTitleMethod().definingClass
            val onCreate = mainActivityOnCreateMethod(sheet)
            val code = onCreate.instructions.toList()

            val sheetClass =
                code.indices.singleOrNull { i ->
                    val type = (code[i] as? ReferenceInstruction)?.reference as? TypeReference
                    code[i].opcode == Opcode.CONST_CLASS && type?.type == sheet
                } ?: throw PatchException("Expected one const-class $sheet in MainActivity.onCreate")

            val classRegister = (code[sheetClass] as OneRegisterInstruction).registerA

            // The class goes to the show-sheet helper; removing that call leaves nothing else to undo.
            val show =
                (sheetClass + 1 until code.size).firstOrNull { i ->
                    val call = code[i] as? FiveRegisterInstruction
                    val reference = (call as? ReferenceInstruction)?.reference as? MethodReference
                    code[i].opcode == Opcode.INVOKE_STATIC &&
                        call?.registerC == classRegister &&
                        reference?.returnType == "V" &&
                        reference.parameterTypes.map { it.toString() } == SHOW_SHEET_PARAMETERS
                } ?: throw PatchException("Found no call that shows $sheet")
            onCreate.removeInstruction(show)
        }
    }
