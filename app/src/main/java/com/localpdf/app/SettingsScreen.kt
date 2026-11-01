@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.localpdf.app

import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(padding: PaddingValues) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf<String?>(null) }
    var resultsBytes by remember { mutableStateOf(0L) }

    LaunchedEffect(AppModel.resultsVersion) {
        resultsBytes = withContext(Dispatchers.IO) { AppModel.resultsBytes(ctx) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = Lang.t("الإعدادات", "Settings"),
            style = MaterialTheme.typography.headlineMedium
        )

        SettingsCard(Lang.t("اللغة", "Language")) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = Lang.arabic,
                    onClick = { Lang.set(ctx, true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("العربية")
                }
                SegmentedButton(
                    selected = !Lang.arabic,
                    onClick = { Lang.set(ctx, false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("English")
                }
            }
        }

        SettingsCard(Lang.t("المظهر", "Appearance")) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val labels = listOf(
                    Lang.t("النظام", "System"),
                    Lang.t("فاتح", "Light"),
                    Lang.t("داكن", "Dark")
                )
                for (i in labels.indices) {
                    SegmentedButton(
                        selected = AppPrefs.themeMode == i,
                        onClick = { AppPrefs.setThemeMode(i) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 3)
                    ) {
                        Text(labels[i])
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Lang.t("ألوان النظام (Material You)", "System colors (Material You)"),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = Lang.t(
                                "تتناغم ألوان التطبيق مع خلفية هاتفك.",
                                "The app colors follow your wallpaper."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = AppPrefs.dynamicColor,
                        onCheckedChange = { AppPrefs.setDynamicColor(it) }
                    )
                }
            }
        }

        if (Lang.arabic) {
            TranslationCard()
        }

        SettingsCard(Lang.t("المكونات", "Components")) {
            ComponentRow(
                name = Lang.t("النظام الأساسي", "Core system"),
                installed = AppModel.coreInstalled,
                busy = AppModel.installing,
                onAction = { confirm = "core" }
            )
            ComponentRow(
                name = Lang.t("حزمة الأدوات (OCR وغيرها)", "Tools pack (OCR and more)"),
                installed = AppModel.toolsPackInstalled,
                busy = AppModel.installing || !AppModel.coreInstalled,
                onAction = { confirm = "tools" }
            )
            ComponentRow(
                name = Lang.t("حزمة Office (Word وExcel)", "Office pack (Word and Excel)"),
                installed = AppModel.officePackInstalled,
                busy = AppModel.installing || !AppModel.coreInstalled,
                onAction = { confirm = "office" }
            )
            Text(
                text = Lang.t(
                    "إعادة تثبيت النظام الأساسي تعيد تنزيل كل المكونات.",
                    "Reinstalling the core system downloads all the components again."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsCard(Lang.t("المحرك", "Engine")) {
            Text(
                text = when (AppModel.engine) {
                    EngineState.READY -> Lang.t("جاهز", "Ready")
                    EngineState.STARTING -> Lang.t("جارٍ التجهيز…", "Getting ready…")
                    EngineState.FAILED -> Lang.t("تعذّر التشغيل", "Could not start")
                    EngineState.STOPPED -> Lang.t("متوقف، يبدأ تلقائيًا عند الحاجة", "Stopped, it starts by itself when needed")
                },
                style = MaterialTheme.typography.bodyLarge
            )
            OutlinedButton(
                onClick = { AppModel.restartEngine(ctx) },
                enabled = AppModel.coreInstalled && !AppModel.installing
            ) {
                Text(Lang.t("إعادة تشغيل المحرك", "Restart the engine"))
            }
        }

        SettingsCard(Lang.t("التخزين", "Storage")) {
            Text(
                text = Lang.t("الملفات الناتجة المحفوظة مؤقتًا: ", "Temporarily kept results: ") +
                    prettySize(resultsBytes),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = Lang.t(
                    "تُحذف الملفات الأقدم من 7 أيام تلقائيًا. احفظ ما تحتاجه في هاتفك.",
                    "Files older than 7 days are removed automatically. Save what you need on your phone."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { confirm = "results" },
                enabled = resultsBytes > 0L
            ) {
                Text(Lang.t("حذف الملفات الناتجة", "Delete the results"))
            }
        }

        SettingsCard(Lang.t("حول التطبيق", "About")) {
            Text(
                text = Lang.t("مختبر PDF", "PDF Lab") + "  " + AppModel.versionName(ctx),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = Lang.t(
                    "كل معالجة تتم على جهازك. لا تُرفع ملفاتك إلى أي خادم، ويُستخدم الإنترنت فقط لتنزيل المكونات ونموذج الترجمة.",
                    "All processing happens on your device. Your files are never uploaded; the internet is only used to download the components and the translation model."
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = Lang.t(
                    "يعمل بمحرك Stirling-PDF مفتوح المصدر.",
                    "Powered by the open source Stirling-PDF engine."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = {
                Thread {
                    val text = AppModel.buildReport(ctx)
                    Handler(Looper.getMainLooper()).post {
                        ResultActions.shareText(ctx, "PDF Lab report", text)
                    }
                }.start()
            }) {
                Text(Lang.t("الإبلاغ عن مشكلة", "Report a problem"))
            }
        }
    }

    val c = confirm
    if (c != null) {
        val title: String
        val body: String
        when (c) {
            "core" -> {
                title = Lang.t("إعادة تثبيت النظام الأساسي؟", "Reinstall the core system?")
                body = Lang.t(
                    "سيُعاد تنزيل النظام الأساسي والحزم كلها. استخدم شبكة Wi-Fi إن أمكن.",
                    "The core system and all packs will be downloaded again. Use Wi-Fi if you can."
                )
            }
            "tools" -> {
                title = Lang.t("تثبيت حزمة الأدوات؟", "Install the tools pack?")
                body = Lang.t("ستُنزَّل الحزمة وتُثبَّت فوق النظام الأساسي.", "The pack will be downloaded and installed on top of the core system.")
            }
            "office" -> {
                title = Lang.t("تثبيت حزمة Office؟", "Install the Office pack?")
                body = Lang.t("ستُنزَّل الحزمة وتُثبَّت فوق النظام الأساسي.", "The pack will be downloaded and installed on top of the core system.")
            }
            else -> {
                title = Lang.t("حذف الملفات الناتجة؟", "Delete the results?")
                body = Lang.t(
                    "ستُحذف كل الملفات الناتجة المحفوظة مؤقتًا في التطبيق. الملفات التي حفظتها في هاتفك لن تتأثر.",
                    "All results kept temporarily inside the app will be deleted. Files you saved on your phone are not affected."
                )
            }
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (c) {
                        "core" -> AppModel.runInstall(ctx, listOf("core", "tools", "office"))
                        "tools" -> {
                            val steps = ArrayList<String>()
                            steps.add("tools")
                            if (AppModel.officePackInstalled) steps.add("office")
                            AppModel.runInstall(ctx, steps)
                        }
                        "office" -> {
                            val steps = ArrayList<String>()
                            if (!AppModel.toolsPackInstalled) steps.add("tools")
                            steps.add("office")
                            AppModel.runInstall(ctx, steps)
                        }
                        else -> AppModel.clearResults(ctx)
                    }
                }) {
                    Text(Lang.t("متابعة", "Continue"))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) {
                    Text(Lang.t("إلغاء", "Cancel"))
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ComponentRow(name: String, installed: Boolean, busy: Boolean, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (installed) Lang.t("مثبّت", "Installed") else Lang.t("غير مثبّت", "Not installed"),
                style = MaterialTheme.typography.bodySmall,
                color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        OutlinedButton(onClick = onAction, enabled = !busy) {
            Text(if (installed) Lang.t("إعادة تثبيت", "Reinstall") else Lang.t("تثبيت", "Install"))
        }
    }
}

@Composable
private fun TranslationCard() {
    SettingsCard(Lang.t("ترجمة الأوصاف على الجهاز", "On-device description translation")) {
        if (!AutoTranslate.modelReady) {
            Text(
                text = Lang.t(
                    "لعرض أوصاف الأدوات بالعربية بدون إنترنت، نزّل نموذج الترجمة مرة واحدة (نحو 30 ميغابايت).",
                    "To show the tool descriptions in Arabic offline, download the translation model once (about 30 MB)."
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = { AutoTranslate.downloadModel(false) },
                enabled = !AutoTranslate.busy
            ) {
                Text(Lang.t("تنزيل نموذج الترجمة", "Download the translation model"))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Lang.t("عرض الأوصاف مترجمة", "Show translated descriptions"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = AutoTranslate.enabled,
                    onCheckedChange = { AutoTranslate.setEnabled(it) }
                )
            }
            if (AutoTranslate.busy && AutoTranslate.total > 0) {
                LinearProgressIndicator(
                    progress = { AutoTranslate.done.toFloat() / AutoTranslate.total.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = AutoTranslate.done.toString() + " / " + AutoTranslate.total,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = { AutoTranslate.cancelBatch() }) {
                    Text(Lang.t("إيقاف الترجمة", "Stop translating"))
                }
            } else {
                if (AppModel.tools.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            AutoTranslate.setEnabled(true)
                            AutoTranslate.translateAll(AppModel.tools)
                        },
                        enabled = !AutoTranslate.busy
                    ) {
                        Text(Lang.t("ترجمة كل الأوصاف الآن", "Translate all descriptions now"))
                    }
                }
                OutlinedButton(
                    onClick = { AutoTranslate.deleteModel() },
                    enabled = !AutoTranslate.busy
                ) {
                    Text(Lang.t("حذف نموذج الترجمة", "Delete the translation model"))
                }
            }
        }
        if (AutoTranslate.status.isNotEmpty()) {
            Text(
                text = AutoTranslate.status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
