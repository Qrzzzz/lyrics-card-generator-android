@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.qrzzzz.lyricscard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qrzzzz.lyricscard.R

internal val HEX_COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?$")

@Composable
internal fun PanelColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
internal fun SectionTitle(value: String) {
    Text(
        value,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
internal fun SettingSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    supportingText: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                },
            )
            supportingText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
internal fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(displayValue, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription = label
                    stateDescription = displayValue
                },
        )
    }
}

@Composable
internal fun <T> ChoiceChips(
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label(value)) },
                leadingIcon = if (selected == value) {
                    {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
internal fun NumberField(
    label: String,
    value: Int,
    validRange: IntRange,
    modifier: Modifier = Modifier,
    onValidValue: (Int) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value.toString()) }
    val parsed = draft.toIntOrNull()
    val valid = parsed != null && parsed in validRange
    OutlinedTextField(
        value = draft,
        onValueChange = { next ->
            draft = next.take(6)
            draft.toIntOrNull()?.takeIf { it in validRange }?.let(onValidValue)
        },
        modifier = modifier,
        label = { Text(label) },
        supportingText = {
            Text(
                if (valid) {
                    stringResource(R.string.editor_value_range, validRange.first, validRange.last)
                } else {
                    stringResource(R.string.editor_invalid_number, validRange.first, validRange.last)
                },
            )
        },
        isError = !valid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
    )
}

@Composable
internal fun ColorField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValidValue: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    val valid = HEX_COLOR_PATTERN.matches(draft)
    OutlinedTextField(
        value = draft,
        onValueChange = { next ->
            draft = next.take(9)
            if (HEX_COLOR_PATTERN.matches(draft)) onValidValue(draft.uppercase())
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = {
            Text(
                stringResource(
                    if (valid) R.string.editor_color_format_hint else R.string.editor_invalid_color,
                ),
            )
        },
        isError = !valid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        trailingIcon = {
            androidx.compose.material3.Surface(
                modifier = Modifier.size(24.dp),
                shape = MaterialTheme.shapes.small,
                color = runCatching { cssHexColor(value) }
                    .getOrDefault(MaterialTheme.colorScheme.surfaceVariant),
            ) {}
        },
    )
}

@Composable
internal fun LimitedSingleLineField(
    label: String,
    value: String,
    maxLength: Int,
    required: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    onValueChange: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    val tooLong = draft.length > maxLength
    val missing = required && draft.isBlank()
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = draft,
        onValueChange = { next ->
            draft = next.take(maxLength + 1)
            if (next.length <= maxLength) onValueChange(next)
        },
        modifier = modifier,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        supportingText = {
            Text(
                when {
                    missing -> stringResource(R.string.common_required_field)
                    tooLong -> stringResource(R.string.editor_text_too_long, maxLength)
                    else -> stringResource(R.string.editor_character_count, draft.length, maxLength)
                },
            )
        },
        isError = tooLong || missing,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            onDone = { focusManager.clearFocus() },
        ),
    )
}

internal fun cssHexColor(value: String): androidx.compose.ui.graphics.Color {
    val hex = value.removePrefix("#")
    require(hex.length == 6 || hex.length == 8)
    val red = hex.substring(0, 2).toInt(16)
    val green = hex.substring(2, 4).toInt(16)
    val blue = hex.substring(4, 6).toInt(16)
    val alpha = if (hex.length == 8) hex.substring(6, 8).toInt(16) else 255
    return androidx.compose.ui.graphics.Color(red, green, blue, alpha)
}
