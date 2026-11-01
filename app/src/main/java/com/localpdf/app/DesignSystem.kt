@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.localpdf.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ---------------------------------------------------------------------------
// Preferences (theme)
// ---------------------------------------------------------------------------

object AppPrefs {
    private const val FILE = "pdflab_prefs"
    private var prefs: SharedPreferences? = null

    // 0 = follow the system, 1 = light, 2 = dark
    var themeMode by mutableStateOf(0)

    // Material You colors taken from the wallpaper (Android 12 and later)
    var dynamicColor by mutableStateOf(false)

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        themeMode = p.getInt("theme_mode", 0)
        dynamicColor = p.getBoolean("dynamic_color", false)
    }

    fun setThemeMode(v: Int) {
        themeMode = v
        prefs?.edit()?.putInt("theme_mode", v)?.apply()
    }

    fun setDynamicColor(v: Boolean) {
        dynamicColor = v
        prefs?.edit()?.putBoolean("dynamic_color", v)?.apply()
    }
}

// ---------------------------------------------------------------------------
// Theme
// ---------------------------------------------------------------------------

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5445D8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4DFFF),
    onPrimaryContainer = Color(0xFF160066),
    secondary = Color(0xFF5E5C71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E0F9),
    onSecondaryContainer = Color(0xFF1B192C),
    tertiary = Color(0xFF8B5000),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF2B1700),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFCF8FF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE5E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFF78767F),
    outlineVariant = Color(0xFFC8C5D0),
    surfaceBright = Color(0xFFFCF8FF),
    surfaceDim = Color(0xFFDCD8E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F2FA),
    surfaceContainer = Color(0xFFF0ECF4),
    surfaceContainerHigh = Color(0xFFEAE6EE),
    surfaceContainerHighest = Color(0xFFE4E1E9)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFC5C0FF),
    onPrimary = Color(0xFF2B1E9C),
    primaryContainer = Color(0xFF3F30B4),
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFFC7C4DD),
    onSecondary = Color(0xFF302E42),
    secondaryContainer = Color(0xFF464559),
    onSecondaryContainer = Color(0xFFE4E0F9),
    tertiary = Color(0xFFFFB867),
    onTertiary = Color(0xFF492900),
    tertiaryContainer = Color(0xFF6A3C00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    outline = Color(0xFF928F99),
    outlineVariant = Color(0xFF47464F),
    surfaceBright = Color(0xFF39383E),
    surfaceDim = Color(0xFF131318),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1B1B20),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A292F),
    surfaceContainerHighest = Color(0xFF35343A)
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

fun Context.activityOrNull(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = when (AppPrefs.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = if (AppPrefs.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) DarkScheme else LightScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = ctx.activityOrNull()?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

// ---------------------------------------------------------------------------
// Icons (drawn from simple stroke paths, so no icon library is needed)
// ---------------------------------------------------------------------------

object AppIcons {
    private fun vec(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        for (p in paths) {
            b.addPath(
                pathData = PathParser().parsePathString(p).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return b.build()
    }

    val Convert: ImageVector by lazy {
        vec("convert", "M5 8h12", "M14 4l4 4-4 4", "M19 16H7", "M10 12l-4 4 4 4")
    }
    val Organize: ImageVector by lazy {
        vec(
            "organize",
            "M8 3h8a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M3 8v11a2 2 0 0 0 2 2h9"
        )
    }
    val Security: ImageVector by lazy {
        vec(
            "security",
            "M12 3l7 3v5.5c0 4.4-2.9 7.6-7 9.5-4.1-1.9-7-5.1-7-9.5V6z",
            "M9 12l2 2 4-4"
        )
    }
    val Magic: ImageVector by lazy {
        vec(
            "magic",
            "M11 4l1.7 4.3L17 10l-4.3 1.7L11 16l-1.7-4.3L5 10l4.3-1.7z",
            "M18 15l.9 2.1L21 18l-2.1.9L18 21l-.9-2.1L15 18l2.1-.9z"
        )
    }
    val Analysis: ImageVector by lazy {
        vec(
            "analysis",
            "M10.5 4a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13z",
            "M15.2 15.2L20 20",
            "M8.5 12.5v-2",
            "M10.5 12.5v-4",
            "M12.5 12.5v-3"
        )
    }
    val Filter: ImageVector by lazy { vec("filter", "M4 5h16l-6 7.5V19l-4 1.5v-8z") }
    val Form: ImageVector by lazy {
        vec(
            "form",
            "M4.5 6.5l1.5 1.5 3-3", "M12 6.5h8",
            "M4.5 12.5l1.5 1.5 3-3", "M12 12.5h8",
            "M4.5 18.5l1.5 1.5 3-3", "M12 18.5h8"
        )
    }
    val Grid: ImageVector by lazy {
        vec("grid", "M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 14h6v6h-6z")
    }
    val Document: ImageVector by lazy {
        vec(
            "document",
            "M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M14 3v5h5", "M8.5 13h7", "M8.5 17h5"
        )
    }
    val Upload: ImageVector by lazy {
        vec("upload", "M12 16V5", "M7.5 9.5L12 5l4.5 4.5", "M5 19h14")
    }
}

data class CategoryStyle(val icon: ImageVector, val container: Color, val content: Color)

@Composable
fun categoryStyle(group: String): CategoryStyle {
    val cs = MaterialTheme.colorScheme
    return when (group) {
        "convert" -> CategoryStyle(AppIcons.Convert, cs.primaryContainer, cs.onPrimaryContainer)
        "general" -> CategoryStyle(AppIcons.Organize, cs.secondaryContainer, cs.onSecondaryContainer)
        "security" -> CategoryStyle(AppIcons.Security, cs.tertiaryContainer, cs.onTertiaryContainer)
        "misc" -> CategoryStyle(AppIcons.Magic, cs.primaryContainer, cs.onPrimaryContainer)
        "analysis" -> CategoryStyle(AppIcons.Analysis, cs.secondaryContainer, cs.onSecondaryContainer)
        "filter" -> CategoryStyle(AppIcons.Filter, cs.tertiaryContainer, cs.onTertiaryContainer)
        "form" -> CategoryStyle(AppIcons.Form, cs.primaryContainer, cs.onPrimaryContainer)
        else -> CategoryStyle(AppIcons.Grid, cs.secondaryContainer, cs.onSecondaryContainer)
    }
}

val UI_GROUP_ORDER = listOf("convert", "general", "misc", "security", "form", "filter", "analysis")

fun uiGroupRank(group: String): Int {
    val i = UI_GROUP_ORDER.indexOf(group)
    return if (i >= 0) i else UI_GROUP_ORDER.size
}

// ---------------------------------------------------------------------------
// Shared components
// ---------------------------------------------------------------------------

@Composable
fun BrandMark(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.linearGradient(listOf(Color(0xFF6A4BE8), Color(0xFF2A177A)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppIcons.Document,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.58f)
        )
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    container: Color,
    content: Color,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.34f))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(size * 0.54f)
        )
    }
}

@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = 56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = Lang.t("رجوع", "Back")
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun <T> SimpleGrid(
    items: List<T>,
    columns: Int,
    spacing: Dp,
    modifier: Modifier = Modifier,
    cell: @Composable (T) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        for (row in items.chunked(columns)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                for (item in row) {
                    Box(modifier = Modifier.weight(1f)) { cell(item) }
                }
                for (i in row.size until columns) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Engine status banner (hidden when everything is ready)
// ---------------------------------------------------------------------------

@Composable
fun EngineBanner(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    if (!AppModel.coreInstalled || AppModel.installing) return
    when (AppModel.engine) {
        EngineState.READY -> Unit
        EngineState.FAILED -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = Lang.t("تعذّر تشغيل المحرك", "The engine could not start"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = Lang.t(
                            "جرّب إعادة المحاولة. إن تكرر الأمر فأعد تشغيل الهاتف.",
                            "Try again. If it keeps happening, restart the phone."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Button(
                        onClick = { AppModel.restartEngine(ctx) },
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Text(Lang.t("إعادة المحاولة", "Try again"))
                    }
                }
            }
        }
        else -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = Lang.t("جارٍ تجهيز المحرك…", "Getting the engine ready…") +
                                if (AppModel.engineSeconds > 0) "  " + AppModel.engineSeconds + " " +
                                    Lang.t("ث", "s") else "",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = Lang.t(
                            "يستغرق هذا نحو نصف دقيقة عند فتح التطبيق. يمكنك تصفح الأدوات أثناء ذلك.",
                            "This takes about half a minute when the app opens. You can browse the tools meanwhile."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Result row (recent files)
// ---------------------------------------------------------------------------

@Composable
fun ResultRow(result: SavedResult, onDeleted: () -> Unit = {}) {
    val ctx = LocalContext.current
    Surface(
        onClick = { ResultActions.open(ctx, result.file) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = AppIcons.Document,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 40.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = prettySize(result.bytes) + "  •  " + prettyAgo(result.modified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { ResultActions.share(ctx, result.file) }) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = Lang.t("مشاركة", "Share")
                )
            }
            IconButton(onClick = {
                AppModel.deleteResult(ctx, result)
                onDeleted()
            }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = Lang.t("حذف", "Delete")
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

fun prettySize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

fun prettyDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

fun prettyAgo(millis: Long): String {
    return android.text.format.DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        android.text.format.DateUtils.MINUTE_IN_MILLIS
    ).toString()
}

// Name and size of a file chosen with the system picker.
fun pickedFileInfo(ctx: Context, uri: Uri): Pair<String, Long> {
    var name = uri.lastPathSegment ?: "file"
    var size = -1L
    try {
        ctx.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getString(0)
                if (!n.isNullOrBlank()) name = n
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
    } catch (e: Throwable) {
        // keep the defaults
    }
    return Pair(name, size)
}

// Drops the technical tail of the API description ("Input:PDF Output:PDF Type:SISO").
fun stripApiTail(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    var s: String = raw
    val idx = s.indexOf(" Input:")
    if (idx > 0) s = s.substring(0, idx)
    s = s.trim()
    return if (s.isEmpty()) null else s
}

// Descriptions come from the API in English. In the Arabic interface they are shown only
// when the on-device translation is switched on, so the screens never mix languages.
fun shownDescription(raw: String?, max: Int): String? {
    val s = stripApiTail(raw) ?: return null
    if (Lang.arabic && !(AutoTranslate.enabled && AutoTranslate.modelReady)) return null
    return AutoTranslate.display(s.take(max))
}

// Removes a trailing English name in parentheses from an Arabic label, e.g. "الدقة (Dpi)".
fun shownLabel(label: String): String {
    if (!Lang.arabic) return label
    return label.replace(Regex("\\s*\\([A-Za-z0-9 _/.-]+\\)\\s*$"), "").trim()
}

fun userErrorText(raw: String): String {
    return when {
        raw.contains("OutOfMemoryError") -> Lang.t(
            "نفدت الذاكرة أثناء المعالجة. جرّب ملفًا أصغر، أو دقة أقل، أو عددًا أقل من الصفحات.",
            "The device ran out of memory. Try a smaller file, a lower resolution or fewer pages."
        )
        raw.startsWith("Connection error") || raw.contains("Connection refused") -> Lang.t(
            "المحرك غير جاهز أو توقف. جارٍ إعادة تشغيله، أعد المحاولة بعد لحظات.",
            "The engine is not ready or stopped. It is restarting, try again in a moment."
        )
        raw.contains("password", ignoreCase = true) -> Lang.t(
            "الملف محمي بكلمة مرور أو كلمة المرور غير صحيحة.",
            "The file is password protected or the password is wrong."
        )
        raw.contains("Could not fetch the page") -> Lang.t(
            "تعذّر تحميل الصفحة. تحقق من الرابط ومن اتصال الإنترنت.",
            "The page could not be loaded. Check the link and your internet connection."
        )
        else -> Lang.t("تعذّر تنفيذ العملية.", "The operation could not be completed.")
    }
}
