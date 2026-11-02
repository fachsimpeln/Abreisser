package io.github.fachsimpeln.abreisser.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import io.github.fachsimpeln.abreisser.R
import io.github.fachsimpeln.abreisser.print.exportPdf
import io.github.fachsimpeln.abreisser.print.printFlyer
import io.github.fachsimpeln.abreisser.render.Analysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Tab(val label: Int, val icon: Int) {
    MAIN(R.string.step1, R.drawable.ic_header),
    STRIPS(R.string.step2, R.drawable.ic_strips),
    PAGE(R.string.step3, R.drawable.ic_page),
    PREVIEW(R.string.preview, R.drawable.ic_eye),
}

private val EDIT_TABS = listOf(Tab.MAIN, Tab.STRIPS, Tab.PAGE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbreisserApp(vm: FlyerViewModel) {
    val state = vm.state
    val analysis = remember(state, vm.imageVersion) { vm.renderer.analyze(state) }
    var tab by rememberSaveable { mutableStateOf(Tab.MAIN) }
    var confirmReset by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileName = stringResource(R.string.file_name)
    val pdfFailed = stringResource(R.string.pdf_failed)
    val shareTitle = stringResource(R.string.share_pdf)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val shown = if (wide && tab == Tab.PREVIEW) Tab.MAIN else tab

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.app_name))
                            Text(
                                pluralStringResource(
                                    R.plurals.summary, state.page.pages,
                                    state.page.rows, state.strips.count, state.page.rows * state.strips.count, state.page.pages,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            scope.launch {
                                val file = try {
                                    withContext(Dispatchers.IO) { exportPdf(context, vm.renderer, state, fileName) }
                                } catch (e: Exception) {
                                    Toast.makeText(context, pdfFailed, Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                sharePdf(context, file, shareTitle)
                            }
                        }) {
                            Icon(painterResource(R.drawable.ic_share), stringResource(R.string.share_pdf))
                        }
                        IconButton(onClick = { printFlyer(context, vm.renderer, state, fileName) }) {
                            Icon(painterResource(R.drawable.ic_print), stringResource(R.string.print))
                        }
                        OverflowMenu(onReset = { confirmReset = true })
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = shown == t,
                                onClick = { tab = t },
                                icon = { Icon(painterResource(t.icon), null) },
                                label = { Text(stringResource(t.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            val body = Modifier
                .padding(padding)
                .fillMaxSize()
            if (wide) {
                Row(body) {
                    Column(Modifier.width(440.dp).fillMaxHeight()) {
                        PrimaryTabRow(selectedTabIndex = EDIT_TABS.indexOf(shown)) {
                            EDIT_TABS.forEach { t ->
                                Tab(
                                    selected = shown == t,
                                    onClick = { tab = t },
                                    text = { Text(stringResource(t.label), maxLines = 1) },
                                )
                            }
                        }
                        Editor(vm, shown, analysis, Modifier.weight(1f))
                    }
                    VerticalDivider()
                    PreviewPane(state, vm.renderer, vm.imageVersion, Modifier.weight(1f))
                }
            } else if (shown == Tab.PREVIEW) {
                Column(body) {
                    IssueList(analysis, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
                    PreviewPane(state, vm.renderer, vm.imageVersion, Modifier.weight(1f))
                }
            } else {
                Editor(vm, shown, analysis, body)
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            text = { Text(stringResource(R.string.reset_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.reset(); confirmReset = false }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun Editor(vm: FlyerViewModel, tab: Tab, analysis: Analysis, modifier: Modifier) {
    // A separate scroll position per step
    val scroll = when (tab) {
        Tab.MAIN -> rememberScrollState()
        Tab.STRIPS -> rememberScrollState()
        else -> rememberScrollState()
    }
    Column(
        modifier
            .imePadding()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (tab) {
            Tab.MAIN -> MainPartPanel(vm)
            Tab.STRIPS -> StripsPanel(vm, analysis)
            else -> PagePanel(vm, analysis)
        }
        IssueList(analysis)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OverflowMenu(onReset: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more), stringResource(R.string.more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                stringResource(R.string.language),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            listOf("" to null, "de" to "Deutsch", "en" to "English").forEach { (tag, name) ->
                DropdownMenuItem(
                    text = { Text(name ?: stringResource(R.string.language_system)) },
                    leadingIcon = { RadioButton(selected = current.startsWith(tag) && (tag.isNotEmpty() || current.isEmpty()), onClick = null) },
                    onClick = {
                        open = false
                        val locales = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                        AppCompatDelegate.setApplicationLocales(locales)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reset)) },
                onClick = { open = false; onReset() },
            )
        }
    }
}

private fun sharePdf(context: Context, file: java.io.File, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, title))
}
