@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.localpdf.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Short names for the most used tools: endpoint key, Arabic name, English name.
private val QUICK_TOOLS = listOf(
    Triple("merge-pdfs", "دمج", "Merge"),
    Triple("split-pages", "تقسيم", "Split"),
    Triple("compress-pdf", "ضغط", "Compress"),
    Triple("ocr-pdf", "قراءة النص", "OCR"),
    Triple("file-to-pdf", "مستند إلى PDF", "Doc to PDF"),
    Triple("pdf-to-word", "PDF إلى Word", "PDF to Word"),
    Triple("img-to-pdf", "صور إلى PDF", "Images to PDF"),
    Triple("pdf-to-img", "PDF إلى صور", "PDF to images"),
    Triple("rotate-pdf", "تدوير", "Rotate"),
    Triple("add-password", "حماية بكلمة مرور", "Protect"),
    Triple("remove-password", "فك الحماية", "Unlock"),
    Triple("add-watermark", "علامة مائية", "Watermark"),
    Triple("url-to-pdf", "رابط إلى PDF", "Link to PDF"),
    Triple("remove-pages", "حذف صفحات", "Remove pages")
)

@Composable
fun HomeScreen(
    padding: PaddingValues,
    onOpenTool: (Tool) -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenSearch: () -> Unit
) {
    val ctx = LocalContext.current
    val tools = AppModel.tools
    val byKey = remember(tools) { tools.associateBy { Catalog.endpointKey(it.path) } }
    val quick = remember(tools, Lang.arabic) {
        QUICK_TOOLS.mapNotNull { q ->
            val t = byKey[q.first]
            if (t == null) null else Pair(t, if (Lang.arabic) q.second else q.third)
        }.take(8)
    }
    val groups = remember(tools) {
        tools.groupBy { it.group }.entries.sortedBy { uiGroupRank(it.key) }
    }
    val results = remember(AppModel.resultsVersion) { AppModel.listResults(ctx).take(5) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(size = 48.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = Lang.t("مختبر PDF", "PDF Lab"),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = Lang.t(
                            "أدوات PDF تعمل على جهازك. ملفاتك لا تغادره.",
                            "PDF tools that run on your device. Your files never leave it."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { EngineBanner() }

        if (AppModel.coreInstalled && !AppModel.installing &&
            (!AppModel.toolsPackInstalled || !AppModel.officePackInstalled)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = Lang.t("أكمل تجهيز المكونات", "Finish setting up the components"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = Lang.t(
                                "بعض الأدوات (مثل OCR وتحويل Word) تحتاج مكونات إضافية لم تُنزَّل بعد.",
                                "Some tools (such as OCR and Word conversion) need extra components that are not downloaded yet."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Button(
                            onClick = { AppModel.runInstall(ctx, AppModel.missingSteps()) },
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(Lang.t("تنزيل الآن", "Download now"))
                        }
                    }
                }
            }
        }

        item {
            Surface(
                onClick = onOpenSearch,
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = Lang.t("ابحث عن أداة…", "Search for a tool…"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (quick.isNotEmpty()) {
            item {
                Column {
                    SectionTitle(Lang.t("الأكثر استخدامًا", "Most used"))
                    SimpleGrid(items = quick, columns = 4, spacing = 8.dp) { entry ->
                        QuickTile(tool = entry.first, label = entry.second, onClick = { onOpenTool(entry.first) })
                    }
                }
            }
        }

        if (groups.isNotEmpty()) {
            item {
                Column {
                    SectionTitle(Lang.t("الفئات", "Categories"))
                    SimpleGrid(items = groups, columns = 2, spacing = 12.dp) { g ->
                        CategoryCard(
                            group = g.key,
                            count = g.value.size,
                            onClick = { onOpenCategory(g.key) }
                        )
                    }
                }
            }
        }

        if (results.isNotEmpty()) {
            item {
                SectionTitle(Lang.t("آخر الملفات", "Recent files"))
            }
            items(results.size) { i ->
                ResultRow(result = results[i])
            }
        }
    }
}

@Composable
private fun QuickTile(tool: Tool, label: String, onClick: () -> Unit) {
    val style = categoryStyle(tool.group)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconBadge(
                icon = style.icon,
                container = style.container,
                content = style.content,
                size = 48.dp
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .heightIn(min = 32.dp)
            )
        }
    }
}

@Composable
private fun CategoryCard(group: String, count: Int, onClick: () -> Unit) {
    val style = categoryStyle(group)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = style.icon,
                container = style.container,
                content = style.content,
                size = 44.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Lang.groupTitle(group),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = count.toString() + " " + Lang.t("أداة", "tools"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
