package com.localpdf.app

import android.content.Context
import org.json.JSONObject
import java.io.File

enum class FieldType { FILE, FILES, BOOL, INT, NUMBER, TEXT, CHOICE, LIST }

data class ToolField(
    val name: String,
    val label: String,
    val type: FieldType,
    val required: Boolean,
    val default: String?,
    val options: List<String>,
    val description: String?
)

data class Tool(
    val path: String,
    val group: String,
    val title: String,
    val description: String?,
    val fields: List<ToolField>,
    val unsupportedFields: List<String>,
    val disabled: Boolean,
    val formEncoded: Boolean = false
)

object Catalog {

    private const val SPEC_NAME = "openapi.json"

    // Tools that take no file (only text fields such as a URL). Every other
    // tool without a file input is still hidden (info, job and file-store endpoints).
    private val FILELESS_TOOLS = setOf("url-to-pdf")

    fun specFile(ctx: Context): File = File(ctx.filesDir, SPEC_NAME)

    fun saveSpec(ctx: Context, text: String) {
        specFile(ctx).writeText(text)
    }

    fun load(ctx: Context): List<Tool> {
        val f = specFile(ctx)
        if (!f.isFile) return emptyList()
        return try {
            parse(f.readText(), disabledEndpoints(ctx))
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun humanize(s: String): String {
        val spaced = s.replace('-', ' ')
            .replace('_', ' ')
            .replace(Regex("([a-z0-9])([A-Z])"), "\$1 \$2")
            .trim()
        return spaced.replaceFirstChar { it.uppercase() }
    }

    fun endpointKey(path: String): String {
        val segs = path.trim('/').split('/')
        val i = segs.indexOf("convert")
        if (i >= 0 && segs.size == i + 3) {
            return "${segs[i + 1]}-to-${segs[i + 2]}"
        }
        return segs.last()
    }

    fun disabledEndpoints(ctx: Context): Set<String> {
        val f = StirlingServer.logFile(ctx)
        if (!f.isFile) return emptySet()
        return try {
            f.useLines { lines ->
                var found: Set<String> = emptySet()
                for (line in lines) {
                    val marker = "Disabled endpoints:"
                    val idx = line.indexOf(marker)
                    if (idx >= 0) {
                        found = line.substring(idx + marker.length)
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .toSet()
                        break
                    }
                }
                found
            }
        } catch (e: Throwable) {
            emptySet()
        }
    }

    private fun resolveRef(node: JSONObject, schemas: JSONObject): JSONObject {
        val ref = node.optString("\$ref", "")
        if (ref.isEmpty()) return node
        val name = ref.substringAfterLast('/')
        return schemas.optJSONObject(name) ?: node
    }

    private fun collect(
        node: JSONObject,
        schemas: JSONObject,
        out: MutableMap<String, JSONObject>,
        required: MutableSet<String>,
        depth: Int
    ) {
        if (depth > 6) return
        val n = resolveRef(node, schemas)
        val all = n.optJSONArray("allOf")
        if (all != null) {
            for (i in 0 until all.length()) {
                val part = all.optJSONObject(i) ?: continue
                collect(part, schemas, out, required, depth + 1)
            }
        }
        val props = n.optJSONObject("properties")
        if (props != null) {
            val iter = props.keys()
            while (iter.hasNext()) {
                val k = iter.next()
                val p = props.optJSONObject(k) ?: continue
                out[k] = p
            }
        }
        val req = n.optJSONArray("required")
        if (req != null) {
            for (i in 0 until req.length()) required.add(req.optString(i))
        }
    }

    private fun toField(
        name: String,
        raw: JSONObject,
        schemas: JSONObject,
        required: Boolean
    ): ToolField? {
        val p = resolveRef(raw, schemas)
        val type = p.optString("type", "")
        val format = p.optString("format", "")
        val desc = p.optString("description", "")
            .ifBlank { raw.optString("description", "") }
            .ifBlank { null }
        val scalarDefault: String? =
            if (p.has("default") && !p.isNull("default") && type != "array") {
                p.get("default").toString()
            } else {
                null
            }
        val label = humanize(name)
        val options = ArrayList<String>()
        val enumArr = p.optJSONArray("enum")
        if (enumArr != null) {
            for (i in 0 until enumArr.length()) options.add(enumArr.optString(i))
        }
        return when {
            type == "string" && format == "binary" ->
                ToolField(name, label, FieldType.FILE, required, null, emptyList(), desc)

            type == "array" -> {
                val items = p.optJSONObject("items")?.let { resolveRef(it, schemas) }
                val itemType = items?.optString("type", "") ?: ""
                val itemFormat = items?.optString("format", "") ?: ""
                if (itemType == "string" && itemFormat == "binary") {
                    ToolField(name, label, FieldType.FILES, required, null, emptyList(), desc)
                } else if (itemType == "string" || itemType == "integer" || itemType == "number") {
                    ToolField(name, label, FieldType.LIST, required, null, emptyList(), desc)
                } else {
                    null
                }
            }

            type == "boolean" ->
                ToolField(name, label, FieldType.BOOL, required, scalarDefault ?: "false", emptyList(), desc)

            type == "integer" ->
                ToolField(name, label, FieldType.INT, required, scalarDefault, emptyList(), desc)

            type == "number" ->
                ToolField(name, label, FieldType.NUMBER, required, scalarDefault, emptyList(), desc)

            type == "string" && options.isNotEmpty() -> {
                val opts = ArrayList<String>()
                if (!required && scalarDefault == null) opts.add("")
                opts.addAll(options)
                ToolField(name, label, FieldType.CHOICE, required, scalarDefault, opts, desc)
            }

            type == "string" ->
                ToolField(name, label, FieldType.TEXT, required, scalarDefault, emptyList(), desc)

            else -> null
        }
    }

    fun parse(text: String, disabled: Set<String>): List<Tool> {
        val root = JSONObject(text)
        val paths = root.optJSONObject("paths") ?: return emptyList()
        val schemas = root.optJSONObject("components")?.optJSONObject("schemas") ?: JSONObject()
        val tools = ArrayList<Tool>()
        val keys = paths.keys()
        while (keys.hasNext()) {
            val path = keys.next()
            if (path.contains("{")) continue
            val post = paths.optJSONObject(path)?.optJSONObject("post") ?: continue
            val key = endpointKey(path)
            val content = post.optJSONObject("requestBody")?.optJSONObject("content")
            val multipartBody = content?.optJSONObject("multipart/form-data")
            val body = multipartBody
                ?: (if (key in FILELESS_TOOLS) content?.optJSONObject("application/x-www-form-urlencoded") else null)
                ?: continue
            val formEncoded = multipartBody == null
            val schema = body.optJSONObject("schema") ?: continue

            val props = LinkedHashMap<String, JSONObject>()
            val required = HashSet<String>()
            collect(schema, schemas, props, required, 0)

            val fields = ArrayList<ToolField>()
            val unsupported = ArrayList<String>()
            for ((name, p) in props) {
                val f = toField(name, p, schemas, required.contains(name))
                if (f == null) unsupported.add(name) else fields.add(f)
            }
            val hasFile = fields.any { it.type == FieldType.FILE || it.type == FieldType.FILES }
            if (!hasFile && !(key in FILELESS_TOOLS && fields.isNotEmpty())) continue

            val segs = path.trim('/').split('/')
            val group = if (segs.size > 2) segs[2] else "other"
            val title = post.optString("summary", "").ifBlank { humanize(segs.last()) }
            val desc = post.optString("description", "").ifBlank { null }
            tools.add(
                Tool(
                    path = path,
                    group = group,
                    title = title,
                    description = desc,
                    fields = fields,
                    unsupportedFields = unsupported,
                    disabled = disabled.contains(key),
                    formEncoded = formEncoded
                )
            )
        }
        return tools.sortedWith(compareBy<Tool>({ it.group }, { it.title.lowercase() }))
    }
}
