@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.localpdf.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Lang.init(applicationContext)
        AppPrefs.init(applicationContext)
        AppModel.init(applicationContext)
        setContent {
            val dir = if (Lang.arabic) LayoutDirection.Rtl else LayoutDirection.Ltr
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides dir) {
                    AppRoot(onRequestNotifications = { askForNotificationPermission() })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        BackgroundWork.appVisible = true
        AppModel.onForeground(applicationContext)
    }

    override fun onStop() {
        BackgroundWork.appVisible = false
        AppModel.onBackground(applicationContext)
        super.onStop()
    }

    // Android 13+ asks the user before an app may show notifications.
    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun AppRoot(onRequestNotifications: () -> Unit) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var selectedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var toolsGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var toolsQuery by rememberSaveable { mutableStateOf("") }
    var focusSearch by remember { mutableStateOf(false) }
    val toolsListState = rememberLazyListState()

    // The engine starts by itself as soon as the components are installed.
    LaunchedEffect(AppModel.coreInstalled, AppModel.installing) {
        if (AppModel.coreInstalled && !AppModel.installing) AppModel.ensureEngine(ctx)
    }

    val needsSetup = !AppModel.coreInstalled || AppModel.showSetup
    if (needsSetup) {
        SetupScreen(
            onRequestNotifications = onRequestNotifications,
            onFinish = { AppModel.showSetup = false }
        )
    } else {
        val selectedTool = AppModel.tools.firstOrNull { it.path == selectedPath }
        AnimatedContent(
            targetState = selectedTool,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220))) togetherWith
                    fadeOut(tween(140))
            },
            label = "screen"
        ) { tool ->
            if (tool != null) {
                key(tool.path) {
                    ToolScreen(tool = tool, onBack = { selectedPath = null })
                }
            } else {
                MainScaffold(
                    tab = tab,
                    onTab = { tab = it },
                    toolsGroup = toolsGroup,
                    onToolsGroup = { toolsGroup = it },
                    toolsQuery = toolsQuery,
                    onToolsQuery = { toolsQuery = it },
                    focusSearch = focusSearch,
                    onSearchFocused = { focusSearch = false },
                    onOpenSearch = {
                        tab = 1
                        focusSearch = true
                    },
                    onOpenCategory = { g ->
                        toolsGroup = g
                        toolsQuery = ""
                        tab = 1
                    },
                    onOpenTool = { selectedPath = it.path },
                    toolsListState = toolsListState
                )
            }
        }
    }
}

@Composable
private fun MainScaffold(
    tab: Int,
    onTab: (Int) -> Unit,
    toolsGroup: String?,
    onToolsGroup: (String?) -> Unit,
    toolsQuery: String,
    onToolsQuery: (String) -> Unit,
    focusSearch: Boolean,
    onSearchFocused: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenTool: (Tool) -> Unit,
    toolsListState: androidx.compose.foundation.lazy.LazyListState
) {
    BackHandler(enabled = tab != 0) { onTab(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { onTab(0) },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(Lang.t("الرئيسية", "Home")) }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { onTab(1) },
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    label = { Text(Lang.t("الأدوات", "Tools")) }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { onTab(2) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(Lang.t("الإعدادات", "Settings")) }
                )
            }
        }
    ) { padding ->
        Crossfade(targetState = tab, label = "tab") { t ->
            when (t) {
                0 -> HomeScreen(
                    padding = padding,
                    onOpenTool = onOpenTool,
                    onOpenCategory = onOpenCategory,
                    onOpenSearch = onOpenSearch
                )
                1 -> ToolsScreen(
                    padding = padding,
                    group = toolsGroup,
                    onGroupChange = onToolsGroup,
                    query = toolsQuery,
                    onQueryChange = onToolsQuery,
                    focusSearch = focusSearch,
                    onSearchFocused = onSearchFocused,
                    listState = toolsListState,
                    onOpenTool = onOpenTool
                )
                else -> SettingsScreen(padding = padding)
            }
        }
    }
}
