// SPDX-License-Identifier: AGPL-3.0-only
package app.edbfi.patches.shared

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.extensions.instructions
import app.revanced.patcher.extensions.replaceInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * Rewrites the method's string literals: [transform] returns the new value, or null to keep a literal.
 * Returns how many literals changed.
 */
internal fun MutableMethod.rewriteStrings(transform: (String) -> String?): Int {
    val edits =
        instructions.withIndex().mapNotNull { (index, instruction) ->
            val mnemonic =
                when (instruction.opcode) {
                    Opcode.CONST_STRING -> "const-string"
                    Opcode.CONST_STRING_JUMBO -> "const-string/jumbo"
                    else -> return@mapNotNull null
                }
            val old = instruction.stringLiteral ?: return@mapNotNull null
            val new = transform(old)?.takeIf { it != old } ?: return@mapNotNull null
            Triple(index, mnemonic, new)
        }
    for ((index, mnemonic, new) in edits) {
        val register = getInstruction<OneRegisterInstruction>(index).registerA
        replaceInstruction(index, "$mnemonic v$register, ${smaliString(new)}")
    }
    return edits.size
}

/** [value] as a quoted smali string literal. */
internal fun smaliString(value: String): String = buildString {
    append('"')
    for (c in value) {
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ' || c > '~') append("\\u%04x".format(c.code)) else append(c)
        }
    }
    append('"')
}

/** The string an instruction loads, or null when it loads none. */
internal val Instruction.stringLiteral: String?
    get() = ((this as? ReferenceInstruction)?.reference as? StringReference)?.string
