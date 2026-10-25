package com.localpdf.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// On-device English -> Arabic translation of the long tool/field descriptions.
// - Technical tokens (quoted values, acronyms, camelCase names, numbers, {placeholders})
//   are masked before translation and restored afterwards.
// - Text is translated sentence by sentence.
// - Results are cached in memory and in a file, so each text is translated only once.
object AutoTranslate {

    private const val PREFS = "localpdf_ui"
    private const val KEY_ENABLED = "auto_translate"
    private const val CACHE_FILE = "translations_ar.json"
    private const val MARK_PREFIX = "ZQ"
    private const val MARK_SUFFIX = "Z"

    // Arabic phrases to correct after translation: "wrong rendering" to "better rendering".
    // Filled later from real output examples.
    private val FIXES: Map<String, String> = mapOf()

    private val PROTECT = Regex(
        "'[^']{1,40}'" +
            "|\"[^\"]{1,40}\"" +
            "|\\{[^}]{1,30}\\}" +
            "|#[0-9A-Fa-f]{3,8}\\b" +
            "|\\b[A-Z]{2,}[A-Za-z0-9/._-]*" +
            "|\\b[A-Za-z]*[0-9][A-Za-z0-9/._-]*" +
            "|\\b[a-z]+[A-Z][A-Za-z0-9]*\\b" +
            "|\\b[A-Za-z0-9]+_[A-Za-z0-9_]+\\b" +
            "|\\b[A-Za-z]+/[A-Za-z]+\\b"
    )

    private val cache = mutableStateMapOf<String, String>()
    private val failed = HashSet<String>()
    private val pending = HashSet<String>()
    private val executor = Executors.newSingleThreadExecutor()
    private val inFlight = AtomicInteger(0)

    @Volatile
    private var cancelRequested = false
    private var initialized = false
    private lateinit var appCtx: Context

    var enabled by mutableStateOf(false)
        private set
    var modelReady by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    var done by mutableStateOf(0)
        private set
    var total by mutableStateOf(0)
        private set

    private val translator: Translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.ARABIC)
                .build()
        )
    }

    private fun remoteModel(): TranslateRemoteModel =
        TranslateRemoteModel.Builder(TranslateLanguage.ARABIC).build()

    private fun <T> await(task: Task<T>, seconds: Long): T =
        Tasks.await(task, seconds, TimeUnit.SECONDS)

    fun init(ctx: Context) {
        if (initialized) return
        initialized = true
        appCtx = ctx.applicationContext
        enabled = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
        loadCache()
        refreshModelState()
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
    }

    private fun refreshModelState() {
        executor.execute {
            try {
                modelReady = await(
                    RemoteModelManager.getInstance().isModelDownloaded(remoteModel()),
                    20
                )
            } catch (e: Throwable) {
                modelReady = false
            }
        }
    }

    fun downloadModel(wifiOnly: Boolean) {
        if (busy) return
        busy = true
        status = Lang.t("جارٍ تنزيل نموذج الترجمة...", "Downloading the translation model...")
        executor.execute {
            try {
                val conditions = DownloadConditions.Builder().apply {
                    if (wifiOnly) requireWifi()
                }.build()
                await(translator.downloadModelIfNeeded(conditions), 15 * 60L)
                modelReady = true
                setEnabled(true)
                status = Lang.t("تم تنزيل نموذج الترجمة.", "Translation model downloaded.")
            } catch (e: Throwable) {
                status = Lang.t(
                    "فشل تنزيل النموذج: ${e.javaClass.simpleName}: ${e.message}",
                    "Model download failed: ${e.javaClass.simpleName}: ${e.message}"
                )
            }
            busy = false
        }
    }

    fun deleteModel() {
        if (busy) return
        busy = true
        executor.execute {
            try {
                await(RemoteModelManager.getInstance().deleteDownloadedModel(remoteModel()), 60)
                modelReady = false
                setEnabled(false)
                status = Lang.t("تم حذف نموذج الترجمة.", "Translation model deleted.")
            } catch (e: Throwable) {
                status = Lang.t(
                    "فشل الحذف: ${e.javaClass.simpleName}: ${e.message}",
                    "Delete failed: ${e.javaClass.simpleName}: ${e.message}"
                )
            }
            busy = false
        }
    }

    // Returns the Arabic translation when available, otherwise the original text
    // (and schedules the translation in the background).
    fun display(text: String): String {
        if (!Lang.arabic || !enabled) return text
        val hit = cache[text]
        if (hit != null) return hit
        request(text)
        return text
    }

    private fun request(text: String) {
        if (!modelReady || text.isBlank()) return
        synchronized(pending) {
            if (failed.contains(text)) return
            if (!pending.add(text)) return
        }
        inFlight.incrementAndGet()
        executor.execute {
            try {
                val r = translateOne(text)
                if (r != null) {
                    cache[text] = r
                } else {
                    synchronized(pending) { failed.add(text) }
                }
            } catch (e: Throwable) {
                synchronized(pending) { failed.add(text) }
            } finally {
                synchronized(pending) { pending.remove(text) }
                if (inFlight.decrementAndGet() == 0) saveCache()
            }
        }
    }

    fun cancelBatch() {
        cancelRequested = true
    }

    fun translateAll(tools: List<Tool>) {
        if (busy || !modelReady) return
        val texts = LinkedHashSet<String>()
        for (t in tools) {
            t.description?.take(300)?.let { if (it.isNotBlank()) texts.add(it) }
            for (f in t.fields) {
                f.description?.take(160)?.let { if (it.isNotBlank()) texts.add(it) }
            }
        }
        val todo = texts.filter { !cache.containsKey(it) }
        if (todo.isEmpty()) {
            status = Lang.t("كل الأوصاف مترجمة بالفعل.", "All descriptions are already translated.")
            return
        }
        cancelRequested = false
        busy = true
        total = todo.size
        done = 0
        status = Lang.t("جارٍ ترجمة الأوصاف...", "Translating descriptions...")
        executor.execute {
            var okCount = 0
            for ((i, text) in todo.withIndex()) {
                if (cancelRequested) break
                try {
                    val r = translateOne(text)
                    if (r != null) {
                        cache[text] = r
                        okCount++
                    }
                } catch (e: Throwable) {
                    // keep going with the next text
                }
                done = i + 1
            }
            saveCache()
            status = if (cancelRequested) {
                Lang.t("تم الإيقاف ($okCount مترجم).", "Stopped ($okCount translated).")
            } else {
                Lang.t("اكتملت الترجمة: $okCount من ${todo.size}.", "Finished: $okCount of ${todo.size}.")
            }
            busy = false
        }
    }

    private fun splitSentences(src: String): List<String> {
        val normalized = src
            .replace("e.g.,", "for example,")
            .replace("e.g.", "for example")
            .replace("i.e.", "that is")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isEmpty()) return emptyList()
        return normalized.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
    }

    private fun translateOne(src: String): String? {
        val out = StringBuilder()
        for (sentence in splitSentences(src)) {
            val tr = translateSentence(sentence) ?: return null
            if (out.isNotEmpty()) out.append(' ')
            out.append(tr)
        }
        var result = out.toString().trim()
        for ((wrong, right) in FIXES) {
            result = result.replace(wrong, right)
        }
        return result.ifEmpty { null }
    }

    private fun translateSentence(s: String): String? {
        if (!s.any { it in 'A'..'Z' || it in 'a'..'z' }) return s

        val tokens = ArrayList<String>()
        val masked = PROTECT.replace(s) { m ->
            val id = tokens.size
            tokens.add(m.value)
            "$MARK_PREFIX$id$MARK_SUFFIX"
        }
        if (tokens.isNotEmpty()) {
            val first = await(translator.translate(masked), 60)
            val restored = unmask(first, tokens)
            if (restored != null) return restored
        }
        val plain = await(translator.translate(s), 60)
        return plain.ifBlank { null }
    }

    private fun unmask(text: String, tokens: List<String>): String? {
        var result = text
        for (i in tokens.indices) {
            val mark = "$MARK_PREFIX$i$MARK_SUFFIX"
            if (!result.contains(mark)) return null
            result = result.replace(mark, tokens[i])
        }
        return result.ifBlank { null }
    }

    private fun loadCache() {
        try {
            val f = File(appCtx.filesDir, CACHE_FILE)
            if (!f.isFile) return
            val o = JSONObject(f.readText())
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                cache[k] = o.getString(k)
            }
        } catch (e: Throwable) {
            // an unreadable cache is simply rebuilt
        }
    }

    @Synchronized
    private fun saveCache() {
        try {
            val o = JSONObject()
            for ((k, v) in cache.entries.toList()) o.put(k, v)
            File(appCtx.filesDir, CACHE_FILE).writeText(o.toString())
        } catch (e: Throwable) {
            // ignore: the cache is only an optimization
        }
    }
}
