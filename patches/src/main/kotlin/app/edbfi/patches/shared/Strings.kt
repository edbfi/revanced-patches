// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.patches.shared

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.extensions.instructions
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.extensions.string
import app.revanced.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

/**
 * Rewrites the method's string literals: [transform] returns the new value, or null to keep a literal.
 * Returns how many literals changed.
 */
internal fun MutableMethod.rewriteStrings(transform: (String) -> String?): Int {
    val edits =
        instructions.withIndex().mapNotNull { (index, instruction) ->
            val jumbo =
                when (instruction.opcode) {
                    Opcode.CONST_STRING -> false
                    Opcode.CONST_STRING_JUMBO -> true
                    else -> return@mapNotNull null
                }
            val old = instruction.string ?: return@mapNotNull null
            val new = transform(old)?.takeIf { it != old } ?: return@mapNotNull null
            Triple(index, jumbo, new)
        }
    for ((index, jumbo, new) in edits) {
        val register = getInstruction<OneRegisterInstruction>(index).registerA
        replaceInstruction(index, constString(register, new, jumbo))
    }
    return edits.size
}

/** Makes the method return [value]. The original body stays behind the early return. */
internal fun MutableMethod.returnString(value: String) {
    if ((implementation?.registerCount ?: 0) < 1) throw PatchException("$name has no register for a string")
    addInstructions(0, listOf<BuilderInstruction>(constString(0, value, jumbo = false), BuilderInstruction11x(Opcode.RETURN_OBJECT, 0)))
}

private fun constString(
    register: Int,
    value: String,
    jumbo: Boolean,
): BuilderInstruction = if (jumbo) {
    BuilderInstruction31c(Opcode.CONST_STRING_JUMBO, register, ImmutableStringReference(value))
} else {
    BuilderInstruction21c(Opcode.CONST_STRING, register, ImmutableStringReference(value))
}
