package com.localpdf.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.io.File

data class ExportResult(val text: String, val tools: Int, val fields: Int)

// Builds a compact plain-text list of the English texts that still have no Arabic
// translation (tool names and unique field names), so they can be pasted in a chat.
object TranslationExport {

    private fun clean(s: String?, max: Int): String {
        if (s.isNullOrBlank()) return ""
        val one = s.replace(Regex("\\s+"), " ").replace("|", "/").trim()
        return if (one.length > max) one.substring(0, max) + "..." else one
    }

    fun build(tools: List<Tool>): ExportResult {
        val sb = StringBuilder()
        var toolCount = 0
        var fieldCount = 0

        sb.append("# TOOLS WITHOUT ARABIC NAME (key | English title | description)\n")
        for (t in tools) {
            if (ArabicNames.toolName(t) != t.title) continue
            sb.append(Catalog.endpointKey(t.path))
                .append(" | ").append(clean(t.title, 80))
                .append(" | ").append(clean(t.description, 100))
                .append('\n')
            toolCount++
        }
        if (toolCount == 0) sb.append("(none)\n")

        sb.append("\n# FIELDS WITHOUT ARABIC LABEL (name | English label | type | description)\n")
        val seen = HashSet<String>()
        for (t in tools) {
            for (f in t.fields) {
                if (f.name == "fileId") continue
                if (!seen.add(f.name)) continue
                if (ArabicNames.fieldLabel(f) != f.label) continue
                sb.append(f.name)
                    .append(" | ").append(clean(f.label, 60))
                    .append(" | ").append(f.type.name)
                    .append(" | ").append(clean(f.description, 90))
                    .append('\n')
                fieldCount++
            }
        }
        if (fieldCount == 0) sb.append("(none)\n")

        return ExportResult(sb.toString(), toolCount, fieldCount)
    }

    fun copyToClipboard(ctx: Context, tools: List<Tool>): String {
        return try {
            val r = build(tools)
            File(ctx.filesDir, "translation_export.txt").writeText(r.text)
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("translation", r.text))
            Lang.t(
                "تم نسخ ${r.fields} حقل و${r.tools} أداة غير مترجمة (${r.text.length / 1024} KB). الصقها في المحادثة.",
                "Copied ${r.fields} fields and ${r.tools} tools without translation (${r.text.length / 1024} KB). Paste them in the chat."
            )
        } catch (e: Throwable) {
            Lang.t(
                "فشل النسخ: ${e.javaClass.simpleName}: ${e.message}",
                "Copy failed: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }
}
