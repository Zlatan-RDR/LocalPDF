package com.localpdf.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun groupTitle(g: String): String = when (g) {
    "analysis" -> "تحليل"
    "convert" -> "تحويل"
    "filter" -> "فلترة"
    "form" -> "نماذج"
    "general" -> "عام"
    "misc" -> "متفرقات"
    "security" -> "أمان"
    "pipeline" -> "أتمتة"
    else -> Catalog.humanize(g)
}

@Composable
fun ToolsScreen() {
    val ctx = LocalContext.current
    var tools by remember { mutableStateOf(Catalog.load(ctx)) }
    var selected by remember { mutableStateOf<Tool?>(null) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("") }
    val serverUp = StirlingServer.isRunning()

    fun startAndRefresh() {
        busy = true
        Thread {
            try {
                if (!StirlingServer.isRunning()) {
                    info = StirlingServer.start(ctx)
                }
                val ready = StirlingServer.waitReady(300) { s ->
                    info = "بدء التشغيل... $s ثانية"
                }
                if (ready) {
                    val r = StirlingServer.httpGet("/v1/api-docs", 30000, 12 * 1024 * 1024)
                    if (r.code == 200) {
                        Catalog.saveSpec(ctx, r.body)
                        tools = Catalog.load(ctx)
                        info = "تم تحميل ${tools.size} أداة"
                    } else {
                        info = "فشل تحميل الأدوات: HTTP ${r.code}"
                    }
                } else {
                    info = "لم يصبح الخادم جاهزاً (${StirlingServer.exitInfo()})"
                }
            } catch (e: Throwable) {
                info = "خطأ: ${e.javaClass.simpleName}: ${e.message}"
            }
            busy = false
        }.start()
    }

    val current = selected
    if (current != null) {
        ToolDetail(tool = current, onBack = { selected = null })
    } else {
        val filtered = remember(tools, query) {
            tools.filter {
                query.isBlank() ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.path.contains(query, ignoreCase = true)
            }
        }
        val grouped = remember(filtered) { filtered.groupBy { it.group } }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(text = "الأدوات", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = if (serverUp) "الخادم: يعمل" else "الخادم: متوقف",
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = { startAndRefresh() }
                ) {
                    Text(if (serverUp) "تحديث قائمة الأدوات" else "تشغيل الخادم وتحميل الأدوات")
                }
                if (busy) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (info.isNotEmpty()) {
                    Text(text = info, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("بحث") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (tools.isEmpty()) {
                item {
                    Text(
                        text = "لا توجد قائمة أدوات بعد. اضغط الزر أعلاه لتشغيل الخادم وتحميلها.",
                        fontSize = 13.sp
                    )
                }
            }
            for ((group, list) in grouped) {
                item(key = "header_$group") {
                    Text(
                        text = groupTitle(group) + " (" + list.size + ")",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }
                items(list, key = { it.path }) { t ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = t }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = t.title + if (t.disabled) "  (غير متاح)" else "",
                            fontSize = 15.sp
                        )
                        Text(
                            text = t.path,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceField(field: ToolField, value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = field.label + if (field.required) " *" else "",
            fontSize = 12.sp
        )
        Box {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = true }
            ) {
                Text(if (value.isEmpty()) "(افتراضي)" else value)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                field.options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(if (opt.isEmpty()) "(افتراضي)" else opt) },
                        onClick = {
                            onChange(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ToolDetail(tool: Tool, onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)

    val values = remember(tool.path) {
        mutableStateMapOf<String, String>().apply {
            tool.fields.forEach { f ->
                val d = f.default
                if (d != null) put(f.name, d)
            }
        }
    }
    val files = remember(tool.path) { mutableStateMapOf<String, List<Uri>>() }
    var pending by remember(tool.path) { mutableStateOf<String?>(null) }
    var running by remember(tool.path) { mutableStateOf(false) }
    var result by remember(tool.path) { mutableStateOf<RunResult?>(null) }
    var saveMsg by remember(tool.path) { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val name = pending
        if (name != null && uris.isNotEmpty()) {
            files[name] = uris
        }
    }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            result?.mime?.ifBlank { null } ?: "application/pdf"
        )
    ) { uri ->
        val src = result?.file
        if (uri != null && src != null) {
            saveMsg = try {
                ctx.contentResolver.openOutputStream(uri)?.use { o ->
                    src.inputStream().use { i -> i.copyTo(o, 64 * 1024) }
                }
                "تم الحفظ"
            } catch (e: Throwable) {
                "فشل الحفظ: ${e.message}"
            }
        }
    }

    val fileFields = tool.fields.filter { it.type == FieldType.FILE || it.type == FieldType.FILES }
    val anyFile = fileFields.any { !files[it.name].isNullOrEmpty() }
    val missingRequired = fileFields.any { it.required && files[it.name].isNullOrEmpty() }
    val canRun = !running && !tool.disabled && anyFile && !missingRequired

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        OutlinedButton(onClick = onBack) { Text("رجوع إلى القائمة") }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = tool.title, style = MaterialTheme.typography.titleLarge)
        Text(text = tool.path, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        val desc = tool.description
        if (desc != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = desc.take(300), fontSize = 12.sp)
        }
        if (tool.disabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "هذه الأداة تحتاج برامج غير مثبتة بعد، لذلك لا يمكن تشغيلها الآن.",
                fontSize = 13.sp
            )
        }
        if (tool.unsupportedFields.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "حقول غير مدعومة في الواجهة العامة: " + tool.unsupportedFields.joinToString(),
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        for (f in tool.fields) {
            when (f.type) {
                FieldType.FILE, FieldType.FILES -> {
                    val chosen = files[f.name].orEmpty()
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            pending = f.name
                            picker.launch(arrayOf("*/*"))
                        }
                    ) {
                        val verb = if (f.type == FieldType.FILE) "اختر ملفاً" else "اختر ملفات"
                        Text(verb + ": " + f.label + if (f.required) " *" else "")
                    }
                    if (chosen.isNotEmpty()) {
                        val names = chosen.take(3).joinToString { ApiClient.displayName(ctx, it) }
                        val more = if (chosen.size > 3) " ..." else ""
                        Text(text = "المحدد (${chosen.size}): $names$more", fontSize = 12.sp)
                    }
                }

                FieldType.BOOL -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = values[f.name] == "true",
                            onCheckedChange = { values[f.name] = it.toString() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = f.label, fontSize = 14.sp)
                    }
                }

                FieldType.CHOICE -> {
                    ChoiceField(
                        field = f,
                        value = values[f.name].orEmpty(),
                        onChange = { values[f.name] = it }
                    )
                }

                else -> {
                    val numeric = f.type == FieldType.INT || f.type == FieldType.NUMBER
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = values[f.name].orEmpty(),
                        onValueChange = { values[f.name] = it },
                        label = { Text(f.label + if (f.required) " *" else "") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text
                        )
                    )
                    if (f.type == FieldType.LIST) {
                        Text(text = "افصل بين القيم بفاصلة", fontSize = 11.sp)
                    }
                }
            }
            val d = f.description
            if (d != null && f.type != FieldType.FILE && f.type != FieldType.FILES) {
                Text(text = d.take(160), fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = canRun,
            onClick = {
                running = true
                result = null
                saveMsg = ""
                val v = values.toMap()
                val fl = files.toMap()
                Thread {
                    val r = try {
                        ApiClient.run(ctx, tool, v, fl)
                    } catch (e: Throwable) {
                        RunResult(
                            false, -1, "ERROR: ${e.javaClass.simpleName}: ${e.message}",
                            null, "", "", 0, 0
                        )
                    }
                    result = r
                    running = false
                }.start()
            }
        ) {
            Text("تشغيل")
        }

        if (running) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(text = "جارٍ التنفيذ...", fontSize = 13.sp)
        }

        val r = result
        if (r != null) {
            Spacer(modifier = Modifier.height(12.dp))
            if (r.ok && r.file != null) {
                Text(
                    text = "تم بنجاح في " + "%.1f".format(r.millis / 1000.0) + " ثانية",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = r.fileName + " (" + (r.bytes / 1024) + " KB, " + r.mime + ")",
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { saver.launch(r.fileName) }
                ) {
                    Text("حفظ النتيجة في الجهاز")
                }
                if (saveMsg.isNotEmpty()) {
                    Text(text = saveMsg, fontSize = 13.sp)
                }
                val isText = r.mime.contains("json") || r.mime.startsWith("text")
                if (isText && r.bytes < 200_000) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val preview = try {
                        r.file.readText().take(4000)
                    } catch (e: Throwable) {
                        ""
                    }
                    Text(text = preview, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            } else {
                Text(text = "فشل التنفيذ", style = MaterialTheme.typography.titleMedium)
                Text(text = r.message, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
