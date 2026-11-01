@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.localpdf.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Tools list
// ---------------------------------------------------------------------------

@Composable
fun ToolsScreen(
    padding: PaddingValues,
    group: String?,
    onGroupChange: (String?) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    focusSearch: Boolean,
    onSearchFocused: () -> Unit,
    listState: LazyListState,
    onOpenTool: (Tool) -> Unit
) {
    val tools = AppModel.tools
    val groups = remember(tools) { tools.map { it.group }.distinct().sortedBy { uiGroupRank(it) } }
    val filtered = remember(tools, query, group, Lang.arabic) {
        tools.filter { (group == null || it.group == group) && ArabicNames.matches(it, query) }
    }
    val nameCounts = remember(tools, Lang.arabic) { tools.groupingBy { Lang.toolName(it) }.eachCount() }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            try {
                focusRequester.requestFocus()
            } catch (e: Throwable) {
                // the field is not on screen yet
            }
            onSearchFocused()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Text(
                text = Lang.t("كل الأدوات", "All tools"),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text(Lang.t("ابحث بالعربية أو الإنجليزية", "Search in Arabic or English")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = Lang.t("مسح", "Clear"))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = group == null,
                    onClick = { onGroupChange(null) },
                    label = { Text(Lang.t("الكل", "All")) }
                )
            }
            items(groups) { g ->
                FilterChip(
                    selected = group == g,
                    onClick = { onGroupChange(if (group == g) null else g) },
                    label = { Text(Lang.groupTitle(g)) }
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            if (tools.isEmpty()) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        EngineBanner()
                        if (AppModel.engine == EngineState.READY) {
                            Text(
                                text = Lang.t("لا توجد أدوات متاحة.", "No tools available."),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    Text(
                        text = Lang.t("لا توجد أداة مطابقة للبحث.", "No tool matches your search."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(filtered, key = { it.path }) { t ->
                    ToolRow(
                        tool = t,
                        ambiguous = (nameCounts[Lang.toolName(t)] ?: 0) > 1,
                        onClick = { onOpenTool(t) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolRow(tool: Tool, ambiguous: Boolean, onClick: () -> Unit) {
    val style = categoryStyle(tool.group)
    val name = Lang.toolName(tool)
    val sub = shownDescription(tool.description, 90)
        ?: if (ambiguous && name != tool.title) tool.title else null
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = style.icon,
                container = style.container,
                content = style.content,
                size = 44.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// One tool
// ---------------------------------------------------------------------------

private fun shouldShowField(f: ToolField): Boolean {
    if (f.name == "fileId" || f.name == "clientFileIds") return false
    if (f.type == FieldType.TEXT && !f.required &&
        (f.description ?: "").contains("JSON", ignoreCase = true)
    ) {
        return false
    }
    return true
}

@Composable
fun ToolScreen(tool: Tool, onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)

    val values = remember(tool.path) {
        mutableStateMapOf<String, String>().apply {
            tool.fields.forEach { f ->
                val d = f.default
                if (d != null) {
                    put(f.name, d)
                } else if (f.type == FieldType.CHOICE && f.required) {
                    val preferred = f.options.firstOrNull { it == "orderProvided" }
                    val first = preferred ?: f.options.firstOrNull { it.isNotEmpty() }
                    if (first != null) put(f.name, first)
                }
            }
        }
    }
    val files = remember(tool.path) { mutableStateMapOf<String, List<Uri>>() }
    var pendingField by remember(tool.path) { mutableStateOf<ToolField?>(null) }
    var running by remember(tool.path) { mutableStateOf(false) }
    var result by remember(tool.path) { mutableStateOf<RunResult?>(null) }
    var elapsed by remember(tool.path) { mutableStateOf(0) }
    var saving by remember(tool.path) { mutableStateOf(false) }
    var saveMsg by remember(tool.path) { mutableStateOf("") }
    var showAdvanced by remember(tool.path) { mutableStateOf(false) }

    LaunchedEffect(running) {
        elapsed = 0
        while (running) {
            delay(1000)
            elapsed += 1
        }
    }

    val pickSingle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val f = pendingField
        if (uri != null && f != null) files[f.name] = listOf(uri)
    }
    val pickMulti = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val f = pendingField
        if (f != null && uris.isNotEmpty()) {
            files[f.name] = (files[f.name].orEmpty() + uris).distinct()
        }
    }
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            result?.let { ResultActions.mimeOf(it.fileName) } ?: "application/pdf"
        )
    ) { uri ->
        val src = result?.file
        if (uri != null && src != null) {
            saving = true
            saveMsg = ""
            Thread {
                val msg = try {
                    ctx.contentResolver.openOutputStream(uri)?.use { o ->
                        src.inputStream().use { i -> i.copyTo(o, 256 * 1024) }
                    }
                    Lang.t("تم الحفظ في الجهاز", "Saved to the device")
                } catch (e: Throwable) {
                    Lang.t("فشل الحفظ", "Save failed") + ": " + e.message
                }
                saveMsg = msg
                saving = false
            }.start()
        }
    }

    val visibleFields = tool.fields.filter { shouldShowField(it) }
    val fileFields = visibleFields.filter { it.type == FieldType.FILE || it.type == FieldType.FILES }
    val mainFiles = if (fileFields.any { it.required }) {
        fileFields.filter { it.required }
    } else {
        fileFields.take(1)
    }
    val extraFiles = fileFields.filter { f -> mainFiles.none { it.name == f.name } }
    val otherFields = visibleFields.filter { it.type != FieldType.FILE && it.type != FieldType.FILES }
    val basicFields = otherFields.filter { it.required }
    val advancedFields = otherFields.filter { !it.required }

    val hasMainFile = mainFiles.isEmpty() || mainFiles.any { !files[it.name].isNullOrEmpty() }
    val missingFile = mainFiles.any { it.required && files[it.name].isNullOrEmpty() }
    val missingText = otherFields.filter {
        it.required &&
            it.type != FieldType.BOOL &&
            values[it.name].isNullOrBlank()
    }
    val engineReady = AppModel.engine == EngineState.READY
    val engineFailed = AppModel.engine == EngineState.FAILED
    val inputsOk = hasMainFile && !missingFile && missingText.isEmpty()
    val canRun = !running && engineReady && inputsOk

    fun startRun() {
        if (running) return
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
                    false, -1, "ERROR: " + e.javaClass.simpleName + ": " + e.message,
                    null, "", "", 0, 0
                )
            }
            result = r
            running = false
            if (r.ok) AppModel.resultsVersion++
            if (!r.ok && (r.message.startsWith("Connection error") || !StirlingServer.isRunning())) {
                AppModel.engine = EngineState.STOPPED
                AppModel.ensureEngine(ctx)
            }
        }.start()
    }

    Scaffold(
        topBar = { AppTopBar(title = Lang.toolName(tool), onBack = onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    if (!inputsOk && !running && !engineFailed) {
                        Text(
                            text = if (!hasMainFile || missingFile) {
                                Lang.t("اختر الملف أولًا.", "Choose the file first.")
                            } else {
                                Lang.t("أكمل الحقول المطلوبة (*).", "Fill in the required fields (*).")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Button(
                        onClick = {
                            if (engineFailed) AppModel.restartEngine(ctx) else startRun()
                        },
                        enabled = canRun || engineFailed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (running || (!engineReady && !engineFailed)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = when {
                                running -> Lang.t("جارٍ المعالجة… ", "Processing… ") + prettyDuration(elapsed)
                                engineFailed -> Lang.t("إعادة تشغيل المحرك", "Restart the engine")
                                !engineReady -> Lang.t("جارٍ تجهيز المحرك…", "Getting the engine ready…")
                                else -> Lang.t("تشغيل", "Run")
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val style = categoryStyle(tool.group)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(style.icon, style.container, style.content, size = 36.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = Lang.groupTitle(tool.group),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val desc = shownDescription(tool.description, 400)
            if (desc != null) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            for (f in mainFiles) {
                FileSection(
                    field = f,
                    uris = files[f.name].orEmpty(),
                    onPick = {
                        pendingField = f
                        if (f.type == FieldType.FILES) pickMulti.launch(arrayOf("*/*")) else pickSingle.launch(arrayOf("*/*"))
                    },
                    onRemove = { u -> files[f.name] = files[f.name].orEmpty().filter { it != u } },
                    onMove = { from, to ->
                        val list = files[f.name].orEmpty().toMutableList()
                        if (from in list.indices && to in list.indices) {
                            val item = list.removeAt(from)
                            list.add(to, item)
                            files[f.name] = list
                        }
                    }
                )
            }

            if (basicFields.isNotEmpty()) {
                SectionTitle(Lang.t("الخيارات", "Options"))
                for (f in basicFields) {
                    FieldInput(field = f, value = values[f.name].orEmpty(), onChange = { values[f.name] = it })
                }
            }

            if (advancedFields.isNotEmpty() || extraFiles.isNotEmpty()) {
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(
                        text = if (showAdvanced) {
                            Lang.t("إخفاء الخيارات المتقدمة", "Hide advanced options")
                        } else {
                            Lang.t("خيارات متقدمة", "Advanced options")
                        }
                    )
                    Icon(
                        imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
                AnimatedVisibility(visible = showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (f in extraFiles) {
                            FileSection(
                                field = f,
                                uris = files[f.name].orEmpty(),
                                onPick = {
                                    pendingField = f
                                    if (f.type == FieldType.FILES) pickMulti.launch(arrayOf("*/*")) else pickSingle.launch(arrayOf("*/*"))
                                },
                                onRemove = { u -> files[f.name] = files[f.name].orEmpty().filter { it != u } },
                                onMove = { _, _ -> }
                            )
                        }
                        for (f in advancedFields) {
                            FieldInput(field = f, value = values[f.name].orEmpty(), onChange = { values[f.name] = it })
                        }
                    }
                }
            }

            val r = result
            if (running) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = Lang.t(
                                "المعالجة تجري على جهازك. يمكنك مغادرة التطبيق وسيصلك إشعار عند الانتهاء.",
                                "Processing happens on your device. You can leave the app and you will get a notification when it is done."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
            if (r != null) {
                if (r.ok && r.file != null) {
                    ResultPanel(
                        result = r,
                        saving = saving,
                        saveMsg = saveMsg,
                        onSave = { saver.launch(r.fileName) }
                    )
                } else {
                    FailurePanel(r)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FileSection(
    field: ToolField,
    uris: List<Uri>,
    onPick: () -> Unit,
    onRemove: (Uri) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val ctx = LocalContext.current
    val multi = field.type == FieldType.FILES
    val label = shownLabel(Lang.fieldLabel(field))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label + if (field.required) " *" else "",
            style = MaterialTheme.typography.titleSmall
        )
        Surface(
            onClick = onPick,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(
                    icon = AppIcons.Upload,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 48.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            uris.isEmpty() && multi -> Lang.t("اختر ملفات", "Choose files")
                            uris.isEmpty() -> Lang.t("اختر ملفًا", "Choose a file")
                            multi -> Lang.t("إضافة ملفات أخرى", "Add more files")
                            else -> Lang.t("تغيير الملف", "Change the file")
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = Lang.t("من ذاكرة الهاتف أو أي مكان آخر", "From the phone or any other location"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        uris.forEachIndexed { index, uri ->
            val info = remember(uri) { pickedFileInfo(ctx, uri) }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (multi && uris.size > 1) {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info.first,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (info.second >= 0) {
                            Text(
                                text = prettySize(info.second),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (multi && uris.size > 1) {
                        IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = Lang.t("أعلى", "Up"))
                        }
                        IconButton(onClick = { onMove(index, index + 1) }, enabled = index < uris.size - 1) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = Lang.t("أسفل", "Down"))
                        }
                    }
                    IconButton(onClick = { onRemove(uri) }) {
                        Icon(Icons.Default.Close, contentDescription = Lang.t("إزالة", "Remove"))
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldInput(field: ToolField, value: String, onChange: (String) -> Unit) {
    val label = shownLabel(Lang.fieldLabel(field)) + if (field.required) " *" else ""
    val help = shownDescription(field.description, 160)
    when (field.type) {
        FieldType.BOOL -> {
            Surface(
                onClick = { onChange(if (value == "true") "false" else "true") },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        if (help != null) {
                            Text(
                                text = help,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(checked = value == "true", onCheckedChange = { onChange(it.toString()) })
                }
            }
        }

        FieldType.CHOICE -> ChoiceField(label, help, field.options, value, onChange)

        else -> {
            val numeric = field.type == FieldType.INT || field.type == FieldType.NUMBER
            val hint = if (field.type == FieldType.LIST) {
                Lang.t("افصل بين القيم بفاصلة", "Separate values with commas")
            } else {
                null
            }
            val supporting = listOfNotNull(hint, help).joinToString("\n")
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label) },
                supportingText = if (supporting.isNotEmpty()) {
                    { Text(supporting) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ChoiceField(
    label: String,
    help: String?,
    options: List<String>,
    value: String,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val defaultText = Lang.t("(افتراضي)", "(default)")
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = if (value.isEmpty()) defaultText else value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            supportingText = if (help != null) {
                { Text(help) }
            } else {
                null
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (opt in options) {
                DropdownMenuItem(
                    text = { Text(if (opt.isEmpty()) defaultText else opt) },
                    onClick = {
                        onChange(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ResultPanel(result: RunResult, saving: Boolean, saveMsg: String, onSave: () -> Unit) {
    val ctx = LocalContext.current
    val file = result.file ?: return
    var showPreview by remember(result) { mutableStateOf(false) }
    val isText = result.mime.contains("json") || result.mime.startsWith("text")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = Lang.t("تمت العملية بنجاح", "Done"),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = result.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = prettySize(result.bytes) + "  •  " + "%.1f".format(result.millis / 1000.0) + " " +
                    Lang.t("ثانية", "s"),
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (saving) {
                        Lang.t("جارٍ الحفظ…", "Saving…")
                    } else {
                        Lang.t("حفظ في الجهاز", "Save to the device")
                    }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { ResultActions.share(ctx, file) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(Lang.t("مشاركة", "Share"))
                }
                OutlinedButton(
                    onClick = { ResultActions.open(ctx, file) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(Lang.t("فتح", "Open"))
                }
            }
            if (saveMsg.isNotEmpty()) {
                Text(text = saveMsg, style = MaterialTheme.typography.bodySmall)
            }
            if (isText && result.bytes < 200_000) {
                TextButton(onClick = { showPreview = !showPreview }) {
                    Text(
                        if (showPreview) Lang.t("إخفاء المعاينة", "Hide preview")
                        else Lang.t("معاينة النص", "Preview")
                    )
                }
                if (showPreview) {
                    val preview = try {
                        file.readText().take(4000)
                    } catch (e: Throwable) {
                        ""
                    }
                    Text(text = preview, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun FailurePanel(result: RunResult) {
    var details by remember(result) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = Lang.t("تعذّر إكمال العملية", "The operation failed"),
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = userErrorText(result.message), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { details = !details }) {
                Text(
                    if (details) Lang.t("إخفاء التفاصيل التقنية", "Hide technical details")
                    else Lang.t("عرض التفاصيل التقنية", "Show technical details")
                )
            }
            if (details) {
                Text(
                    text = result.message.take(1500),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
