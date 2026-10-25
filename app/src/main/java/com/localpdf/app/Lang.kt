package com.localpdf.app

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

// App language switch (Arabic / English). The choice is stored on the device.
object Lang {
    private const val PREFS = "localpdf_ui"
    private const val KEY_ARABIC = "ui_arabic"

    private var state: MutableState<Boolean> = mutableStateOf(true)
    private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ARABIC, true)
        state = mutableStateOf(saved)
    }

    val arabic: Boolean
        get() = state.value

    fun set(ctx: Context, value: Boolean) {
        state.value = value
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ARABIC, value)
            .apply()
    }

    fun t(ar: String, en: String): String = if (arabic) ar else en

    fun toolName(tool: Tool): String =
        if (arabic) ArabicNames.toolName(tool) else tool.title

    fun fieldLabel(field: ToolField): String =
        if (arabic) ArabicNames.fieldLabel(field) else field.label

    fun groupTitle(group: String): String =
        if (arabic) ArabicNames.groupTitle(group) else Catalog.humanize(group)
}
