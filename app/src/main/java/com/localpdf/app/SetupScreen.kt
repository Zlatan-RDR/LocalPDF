@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.localpdf.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// First launch (and component installation from the settings): a friendly screen that
// downloads the components and shows the progress.
@Composable
fun SetupScreen(onRequestNotifications: () -> Unit, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val installing = AppModel.installing

    DisposableEffect(installing) {
        view.keepScreenOn = installing
        onDispose { view.keepScreenOn = false }
    }

    val phase = AppModel.setupPhase
    val savedMb = AppModel.savedBytes / MB
    val canSkip = AppModel.coreInstalled && !installing

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            BrandMark(size = 96.dp)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = Lang.t("مرحبًا بك في مختبر PDF", "Welcome to PDF Lab"),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = Lang.t(
                    "كل أدوات PDF في جيبك، وتعمل على جهازك دون رفع ملفاتك إلى أي مكان.",
                    "All the PDF tools in your pocket, running on your device without uploading your files anywhere."
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureLine(Lang.t("أكثر من 80 أداة: دمج وتقسيم وضغط وتحويل وOCR وحماية", "80+ tools: merge, split, compress, convert, OCR and protect"))
                FeatureLine(Lang.t("خصوصية كاملة: ملفاتك تبقى في هاتفك", "Full privacy: your files stay on your phone"))
                FeatureLine(Lang.t("تعمل بدون إنترنت بعد هذا التجهيز", "Works offline after this one-time setup"))
            }
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = Lang.t("المكونات المطلوبة", "Required components"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    StepRow(
                        label = Lang.t("النظام الأساسي (نحو 280 ميغابايت)", "Core system (about 280 MB)"),
                        done = AppModel.coreInstalled,
                        active = installing && AppModel.setupStep == "core"
                    )
                    StepRow(
                        label = Lang.t("حزمة الأدوات (OCR وتحويل المستندات)", "Tools pack (OCR and document conversion)"),
                        done = AppModel.toolsPackInstalled,
                        active = installing && AppModel.setupStep == "tools"
                    )
                    StepRow(
                        label = Lang.t("حزمة Office (Word وExcel)", "Office pack (Word and Excel)"),
                        done = AppModel.officePackInstalled,
                        active = installing && AppModel.setupStep == "office"
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (installing) {
                if (AppModel.setupFraction >= 0f) {
                    LinearProgressIndicator(
                        progress = { AppModel.setupFraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = AppModel.setupStatus.ifEmpty { Lang.t("جارٍ التحضير…", "Preparing…") },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    text = Lang.t(
                        "يمكنك ترك التطبيق، وسيستمر التنزيل في الخلفية.",
                        "You can leave the app; the download continues in the background."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                OutlinedButton(
                    onClick = { AppModel.pauseInstall() },
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text(Lang.t("إيقاف مؤقت", "Pause"))
                }
            } else {
                when (phase) {
                    SetupPhase.DONE -> {
                        Text(
                            text = Lang.t("اكتمل التجهيز. كل شيء جاهز!", "Setup finished. Everything is ready!"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = onFinish,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(Lang.t("ابدأ الاستخدام", "Start using the app"))
                        }
                    }

                    SetupPhase.FAILED -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = Lang.t("تعذّر إكمال التنزيل", "The download could not finish"),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = AppModel.setupError,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                        StartButton(
                            label = Lang.t("إعادة المحاولة", "Try again"),
                            onClick = {
                                onRequestNotifications()
                                AppModel.runInstall(ctx, AppModel.missingSteps())
                            }
                        )
                    }

                    else -> {
                        if (phase == SetupPhase.PAUSED || savedMb > 0L) {
                            Text(
                                text = Lang.t(
                                    "التقدم محفوظ ($savedMb ميغابايت). سيُكمل التنزيل من حيث توقف.",
                                    "Progress is saved ($savedMb MB). The download will continue where it stopped."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = Lang.t(
                                    "يُنصح بالاتصال بشبكة Wi-Fi، فالتنزيل كبير الحجم.",
                                    "A Wi-Fi connection is recommended because the download is large."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        StartButton(
                            label = if (phase == SetupPhase.PAUSED || savedMb > 0L) {
                                Lang.t("متابعة التنزيل", "Resume the download")
                            } else {
                                Lang.t("ابدأ التجهيز", "Start setup")
                            },
                            onClick = {
                                onRequestNotifications()
                                AppModel.runInstall(ctx, AppModel.missingSteps())
                            }
                        )
                    }
                }
                if (canSkip && phase != SetupPhase.DONE) {
                    TextButton(onClick = onFinish) {
                        Text(Lang.t("لاحقًا", "Not now"))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StartButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                active -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp
                )
                else -> Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
