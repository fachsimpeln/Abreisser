package io.github.fachsimpeln.abreisser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fachsimpeln.abreisser.R
import io.github.fachsimpeln.abreisser.model.Align
import io.github.fachsimpeln.abreisser.model.Direction
import io.github.fachsimpeln.abreisser.model.FontChoice
import io.github.fachsimpeln.abreisser.model.ImagePos
import io.github.fachsimpeln.abreisser.model.Limits
import io.github.fachsimpeln.abreisser.model.StripMode
import io.github.fachsimpeln.abreisser.render.Analysis
import io.github.fachsimpeln.abreisser.render.Issue
import io.github.fachsimpeln.abreisser.render.stripMarkup
import java.util.Locale

private val panelSpacing = Arrangement.spacedBy(16.dp)

/** Locale-aware number for help texts: 1,5 vs 1.5. */
fun fmt(n: Float, digits: Int? = null): String {
    val nf = java.text.NumberFormat.getNumberInstance(Locale.getDefault())
    if (digits == null) {
        nf.maximumFractionDigits = 1
    } else {
        nf.minimumFractionDigits = digits
        nf.maximumFractionDigits = digits
    }
    return nf.format(n)
}

@Composable
private fun alignIcon(a: Align) = when (a) {
    Align.LEFT -> R.drawable.ic_align_left to stringResource(R.string.align_left)
    Align.CENTER -> R.drawable.ic_align_center to stringResource(R.string.align_center)
    Align.RIGHT -> R.drawable.ic_align_right to stringResource(R.string.align_right)
    Align.JUSTIFY -> R.drawable.ic_align_justify to stringResource(R.string.align_justify)
}

/* ---------- step 1: main part ---------- */

@Composable
fun MainPartPanel(vm: FlyerViewModel) {
    val h = vm.state.header
    Column(verticalArrangement = panelSpacing) {
        PanelHead(stringResource(R.string.step1), stringResource(R.string.main_intro))
        SwitchRow(stringResource(R.string.show_main), h.enabled, { on -> vm.header { it.copy(enabled = on) } })

        AnimatedVisibility(h.enabled) {
            Column(verticalArrangement = panelSpacing) {
                OutlinedTextField(
                    value = h.title,
                    onValueChange = { t -> vm.header { it.copy(title = t.take(200)) } },
                    label = { Text(stringResource(R.string.title)) },
                    placeholder = { Text(stringResource(R.string.title_ph)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = h.desc,
                    onValueChange = { t -> vm.header { it.copy(desc = t.take(600)) } },
                    label = { Text("${stringResource(R.string.description)} (${stringResource(R.string.optional)})") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel(stringResource(R.string.alignment))
                    Segmented(listOf(Align.LEFT, Align.CENTER, Align.RIGHT), h.align, { a -> vm.header { it.copy(align = a) } }) {
                        val (icon, label) = alignIcon(it)
                        SegIcon(icon, label)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel(stringResource(R.string.image), optional = true)
                    ImagePicker(
                        h.image, vm.images, vm.imageVersion,
                        onPick = { uri -> vm.pickImage(uri) { p -> vm.header { it.copy(image = p) } } },
                        onRemove = { vm.header { it.copy(image = null) } },
                    )
                }

                Advanced {
                    FieldGrid(
                        { m -> NumberField(stringResource(R.string.height), h.height, "mm", Limits.headerHeight, { v -> vm.header { it.copy(height = v) } }, m) },
                        { m -> NumberField(stringResource(R.string.image_size), h.imageSize, "mm", Limits.headerImage, { v -> vm.header { it.copy(imageSize = v) } }, m) },
                        { m -> NumberField(stringResource(R.string.title_size), h.titleSize, "pt", Limits.titleSize, { v -> vm.header { it.copy(titleSize = v) } }, m) },
                        { m -> NumberField(stringResource(R.string.desc_size), h.descSize, "pt", Limits.descSize, { v -> vm.header { it.copy(descSize = v) } }, m) },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FieldLabel(stringResource(R.string.image_pos))
                        Segmented(ImagePos.entries, h.imagePos, { p -> vm.header { it.copy(imagePos = p) } }) {
                            Text(
                                stringResource(
                                    when (it) {
                                        ImagePos.LEFT -> R.string.pos_left
                                        ImagePos.TOP -> R.string.pos_top
                                        ImagePos.RIGHT -> R.string.pos_right
                                    },
                                ),
                            )
                        }
                    }
                    SwitchRow(
                        stringResource(R.string.recolor), h.imageRecolor, { on -> vm.header { it.copy(imageRecolor = on) } },
                        help = stringResource(R.string.recolor_help),
                    )
                    ImageBackground(
                        h.imageBgOn, h.imageBg,
                        { on -> vm.header { it.copy(imageBgOn = on) } },
                        { c -> vm.header { it.copy(imageBg = c) } },
                    )
                }
            }
        }
    }
}

/* ---------- step 2: strips ---------- */

/** Wraps the selection in markdown-style markers, or removes them if already there. */
private fun wrapSelection(v: TextFieldValue, marker: String): TextFieldValue {
    val a = v.selection.min
    val b = v.selection.max
    val text = v.text
    val sel = text.substring(a, b)
    val before = text.substring(maxOf(0, a - marker.length), a)
    val after = text.substring(b, minOf(text.length, b + marker.length))
    return if (sel.isNotEmpty() && before == marker && after == marker) {
        TextFieldValue(
            text.substring(0, a - marker.length) + sel + text.substring(b + marker.length),
            TextRange(a - marker.length, b - marker.length),
        )
    } else {
        TextFieldValue(
            text.substring(0, a) + marker + sel + marker + text.substring(b),
            TextRange(a + marker.length, b + marker.length),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StripsPanel(vm: FlyerViewModel, analysis: Analysis) {
    val s = vm.state.strips
    val m = analysis.metrics
    var field by remember { mutableStateOf(TextFieldValue(s.text)) }
    LaunchedEffect(s.text) {
        if (field.text != s.text) field = TextFieldValue(s.text, TextRange(s.text.length))
    }
    fun setText(v: TextFieldValue) {
        val limited = if (v.text.length > 200) TextFieldValue(v.text.take(200), TextRange(200)) else v
        field = limited
        vm.strips { it.copy(text = limited.text) }
    }

    Column(verticalArrangement = panelSpacing) {
        PanelHead(stringResource(R.string.step2), stringResource(R.string.strips_intro))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(stringResource(R.string.content))
            Segmented(StripMode.entries, s.mode, { mode -> vm.strips { it.copy(mode = mode) } }) {
                Text(stringResource(if (it == StripMode.SAME) R.string.mode_same else R.string.mode_individual), maxLines = 1)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                FlowRow(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.Center) {
                    IconButton(onClick = { setText(wrapSelection(field, "**")) }) {
                        Text(stringResource(R.string.bold_short), fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { setText(wrapSelection(field, "*")) }) {
                        Text(stringResource(R.string.italic_short), fontStyle = FontStyle.Italic)
                    }
                    Spacer(Modifier.width(8.dp))
                    Align.entries.forEach { a ->
                        val (icon, label) = alignIcon(a)
                        val selected = s.align == a
                        IconButton(onClick = { vm.strips { it.copy(align = a) } }) {
                            Surface(
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Icon(painterResource(icon), label, Modifier.padding(6.dp).size(20.dp))
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = field,
                onValueChange = ::setText,
                label = { Text(stringResource(R.string.text)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Help(stringResource(R.string.format_help))
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = s.link,
                onValueChange = { t -> vm.strips { it.copy(link = t.trim()) } },
                label = { Text("${stringResource(R.string.qr_link)} (${stringResource(R.string.optional)})") },
                placeholder = { Text("https://…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            analysis.qr?.let { qr ->
                val text = buildString {
                    if (qr.dotMm != null) append(stringResource(R.string.qr_help, fmt(qr.sizeMm, 1), fmt(qr.dotMm, 2)))
                    if (qr.limitedFrom != null) append(" ").append(stringResource(R.string.qr_limited, fmt(qr.limitedFrom)))
                }
                Help(text.trim(), warn = qr.tooSmall)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(stringResource(R.string.image), optional = true)
            ImagePicker(
                s.image, vm.images, vm.imageVersion,
                onPick = { uri -> vm.pickImage(uri) { p -> vm.strips { it.copy(image = p) } } },
                onRemove = { vm.strips { it.copy(image = null) } },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(stringResource(R.string.strips_per_row))
            Stepper(
                s.count, Limits.stripCount, { c -> vm.strips { it.copy(count = c) } },
                stringResource(R.string.fewer), stringResource(R.string.more_count),
            )
            Help(stringResource(R.string.strip_help, fmt(m.stripW, 1), fmt(m.cross(s.padding), 1)))
        }

        if (s.mode == StripMode.INDIVIDUAL) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Help(stringResource(R.string.fallback_help))
                for (i in 0 until s.count) StripItemCard(vm, i)
            }
        }

        Advanced {
            FieldGrid(
                { md -> NumberField(stringResource(R.string.strip_length), s.length, "mm", Limits.stripLength, { v -> vm.strips { it.copy(length = v) } }, md) },
                { md -> NumberField(stringResource(R.string.inner_padding), s.padding, "mm", Limits.padding, { v -> vm.strips { it.copy(padding = v) } }, md) },
                { md -> NumberField(stringResource(R.string.image_length), s.imageSize, "mm", Limits.stripImage, { v -> vm.strips { it.copy(imageSize = v) } }, md) },
                { md -> NumberField(stringResource(R.string.qr_size), s.qrSize, "mm", Limits.qrSize, { v -> vm.strips { it.copy(qrSize = v) } }, md) },
                { md -> FontField(s.font, { f -> vm.strips { it.copy(font = f) } }, md) },
                { md -> NumberField(stringResource(R.string.font_size), s.fontSize, "pt", Limits.fontSize, { v -> vm.strips { it.copy(fontSize = v) } }, md) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(stringResource(R.string.reading_dir))
                Segmented(Direction.entries, s.direction, { d -> vm.strips { it.copy(direction = d) } }) {
                    Text(stringResource(if (it == Direction.UP) R.string.dir_up else R.string.dir_down), maxLines = 1)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel(stringResource(R.string.whole_text))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(s.bold, { vm.strips { it.copy(bold = !it.bold) } }, { Text(stringResource(R.string.bold), fontWeight = FontWeight.Bold) })
                    FilterChip(s.italic, { vm.strips { it.copy(italic = !it.italic) } }, { Text(stringResource(R.string.italic), fontStyle = FontStyle.Italic) })
                    FilterChip(
                        s.underline, { vm.strips { it.copy(underline = !it.underline) } },
                        { Text(stringResource(R.string.underline), textDecoration = TextDecoration.Underline) },
                    )
                }
            }
            SwitchRow(
                stringResource(R.string.recolor), s.imageRecolor, { on -> vm.strips { it.copy(imageRecolor = on) } },
                help = stringResource(R.string.recolor_help),
            )
            ImageBackground(
                s.imageBgOn, s.imageBg,
                { on -> vm.strips { it.copy(imageBgOn = on) } },
                { c -> vm.strips { it.copy(imageBg = c) } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontField(font: FontChoice, onChange: (FontChoice) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val label = @Composable { f: FontChoice ->
        stringResource(
            when (f) {
                FontChoice.SANS -> R.string.font_sans
                FontChoice.SERIF -> R.string.font_serif
                FontChoice.MONO -> R.string.font_mono
                FontChoice.HAND -> R.string.font_hand
            },
        )
    }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value = label(font),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.font), maxLines = 1) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            FontChoice.entries.forEach { f ->
                DropdownMenuItem(text = { Text(label(f)) }, onClick = { onChange(f); expanded = false })
            }
        }
    }
}

@Composable
private fun StripItemCard(vm: FlyerViewModel, i: Int) {
    val item = vm.state.strips.items.getOrNull(i) ?: return
    var open by rememberSaveable(i) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            val own = item.text.trim()
            Text(
                if (own.isNotEmpty()) stripMarkup(own).lines().first() else stringResource(R.string.item_shared),
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.isCustom) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { open = !open }) {
                Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(18.dp).rotate(if (open) 90f else 0f))
            }
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = item.text,
                    onValueChange = { t -> vm.item(i) { it.copy(text = t.take(200)) } },
                    label = { Text(stringResource(R.string.text)) },
                    placeholder = { Text(stringResource(R.string.item_text_ph)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = item.link,
                    onValueChange = { t -> vm.item(i) { it.copy(link = t.trim()) } },
                    label = { Text(stringResource(R.string.qr_link)) },
                    placeholder = { Text(stringResource(R.string.item_link_ph)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                ImagePicker(
                    item.image, vm.images, vm.imageVersion,
                    onPick = { uri -> vm.pickImage(uri) { p -> vm.item(i) { it.copy(image = p) } } },
                    onRemove = { vm.item(i) { it.copy(image = null) } },
                )
            }
        }
    }
}

/* ---------- step 3: page & print ---------- */

@Composable
fun PagePanel(vm: FlyerViewModel, analysis: Analysis) {
    val p = vm.state.page
    val m = analysis.metrics
    Column(verticalArrangement = panelSpacing) {
        PanelHead(stringResource(R.string.step3), stringResource(R.string.page_intro))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(stringResource(R.string.rows_per_page))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper(
                    p.rows, Limits.rows, { r -> vm.page { it.copy(rows = r) } },
                    stringResource(R.string.fewer), stringResource(R.string.more_count),
                )
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = vm::fillPage) { Text(stringResource(R.string.fill_page)) }
            }
            Help(pluralStringResource(R.plurals.fit_help, m.fit, m.fit, fmt(m.zettelH)))
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(stringResource(R.string.pages))
            Stepper(
                p.pages, Limits.pages, { n -> vm.page { it.copy(pages = n) } },
                stringResource(R.string.fewer), stringResource(R.string.more_count),
            )
        }

        SwitchRow(stringResource(R.string.cut_lines), p.cutLines, { on -> vm.page { it.copy(cutLines = on) } })

        Advanced {
            FieldGrid(
                { md -> NumberField(stringResource(R.string.side_margin), p.marginX, "mm", Limits.margin, { v -> vm.page { it.copy(marginX = v) } }, md) },
                { md -> NumberField(stringResource(R.string.tb_margin), p.marginY, "mm", Limits.margin, { v -> vm.page { it.copy(marginY = v) } }, md) },
                { md -> NumberField(stringResource(R.string.row_gap), p.gap, "mm", Limits.gap, { v -> vm.page { it.copy(gap = v) } }, md) },
            )
            SwitchRow(stringResource(R.string.outline), p.outline, { on -> vm.page { it.copy(outline = on) } })
        }

        Callout(
            stringResource(R.string.tips_title),
            listOf(stringResource(R.string.tip1), stringResource(R.string.tip2), stringResource(R.string.tip3)),
        )
    }
}

/* ---------- warnings ---------- */

@Composable
fun IssueList(analysis: Analysis, modifier: Modifier = Modifier) {
    if (analysis.issues.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            analysis.issues.forEach { issue ->
                Row {
                    Icon(
                        painterResource(R.drawable.ic_warning), null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp).padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        issueText(issue),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun issueText(issue: Issue): String = when (issue) {
    is Issue.Rows -> pluralStringResource(R.plurals.issue_rows, issue.fit, issue.fit)
    is Issue.Scheme -> stringResource(R.string.issue_scheme, issue.link)
    Issue.QrSmall -> stringResource(R.string.issue_qr_small)
    Issue.TextCut -> stringResource(R.string.issue_text)
    Issue.HeadCut -> stringResource(R.string.issue_head)
}
