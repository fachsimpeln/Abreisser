package io.github.fachsimpeln.abreisser.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fachsimpeln.abreisser.R
import io.github.fachsimpeln.abreisser.render.ImageStore
import java.text.DecimalFormat

@Composable
fun PanelHead(title: String, intro: String) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun FieldLabel(text: String, optional: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge)
        if (optional) {
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.optional),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun Help(text: String, warn: Boolean = false) {
    if (text.isEmpty()) return
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, help: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (help != null) Help(help)
        }
        trailing()
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A segmented choice; each option is shown as a label or an icon. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    label: @Composable (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
                label = { label(option) },
            )
        }
    }
}

@Composable
fun SegIcon(res: Int, description: String) {
    Icon(painterResource(res), contentDescription = description, modifier = Modifier.size(20.dp))
}

private val decimal = DecimalFormat("0.##")

private fun parseNumber(text: String): Float? = text.trim().replace(',', '.').toFloatOrNull()

/**
 * Number input that clamps to [range]. While the user types, valid values are
 * applied right away; the clamped value is shown once the field loses focus.
 */
@Composable
fun NumberField(
    label: String,
    value: Float,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(decimal.format(value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) {
        val typed = parseNumber(text)
        if (!focused || (typed != null && typed.coerceIn(range) != value)) text = decimal.format(value)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            parseNumber(t)?.let { onChange(it.coerceIn(range)) }
        },
        label = { Text(label, maxLines = 1) },
        suffix = { Text(unit) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
    )
}

/** Integer input with − and + buttons. */
@Composable
fun Stepper(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    fewer: String,
    more: String,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) {
        val typed = text.toIntOrNull()
        if (!focused || (typed != null && typed.coerceIn(range) != value)) text = value.toString()
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { onChange((value - 1).coerceIn(range)) },
            enabled = value > range.first,
            modifier = Modifier.semantics { contentDescription = fewer },
        ) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                text = t.filter { it.isDigit() }.take(3)
                text.toIntOrNull()?.let { onChange(it.coerceIn(range)) }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(80.dp)
                .padding(horizontal = 8.dp)
                .onFocusChanged { focused = it.isFocused },
        )
        FilledTonalIconButton(
            onClick = { onChange((value + 1).coerceIn(range)) },
            enabled = value < range.last,
            modifier = Modifier.semantics { contentDescription = more },
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Collapsible "Advanced options" section. */
@Composable
fun Advanced(content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val angle by animateFloatAsState(if (open) 90f else 0f, label = "chevron")
    Column {
        TextButton(onClick = { open = !open }) {
            Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(18.dp).rotate(angle))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.advanced))
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

/** Two number fields per row. */
@Composable
fun FieldGrid(vararg fields: @Composable (Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.toList().chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { it(Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Image chooser with a thumbnail of the current image. */
@Composable
fun ImagePicker(
    path: String?,
    images: ImageStore,
    imageVersion: Int,
    onPick: (Uri) -> Unit,
    onRemove: () -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPick(uri)
    }
    val bitmap = remember(path, imageVersion) { path?.let { images.bitmap(it, false) }?.asImageBitmap() }
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { launcher.launch("image/*") },
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(bitmap, null, contentScale = ContentScale.Fit, modifier = Modifier.size(48.dp))
                } else {
                    Icon(painterResource(R.drawable.ic_image), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(if (path != null) R.string.replace_image else R.string.add_image),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (path != null) {
                OutlinedButton(onClick = onRemove) { Text(stringResource(R.string.remove)) }
            }
        }
    }
}

private val SWATCHES = listOf(
    0xFF222222, 0xFF000000, 0xFF5D6679, 0xFF1F6FEB, 0xFF188038, 0xFFD93025, 0xFFF9AB00,
).map { it.toInt() }

/** Image backdrop toggle with a few colours to choose from. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageBackground(on: Boolean, color: Int, onToggle: (Boolean) -> Unit, onColor: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchRow(stringResource(R.string.image_bg), on, onToggle)
        if (on) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SWATCHES.forEach { c ->
                    val selected = c == color
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                CircleShape,
                            )
                            .padding(if (selected) 4.dp else 0.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .clickable { onColor(c) },
                    )
                }
            }
        }
    }
}

@Composable
fun Callout(title: String, lines: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            lines.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
