package com.qrzzzz.lyricscard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qrzzzz.lyricscard.DiagnosticsSnapshot
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.ui.theme.LyricsCardSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onDarkMode: (Boolean) -> Unit,
    onDefaultExportScale: (Int) -> Unit,
    onShowSafeArea: (Boolean) -> Unit,
    onClearExportCache: () -> Unit,
    onMessageShown: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val screenTitle = stringResource(R.string.settings_title)
    val message = state.errorMessage ?: state.cacheStatus
    val messageText = message?.asString()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = Modifier.semantics { paneTitle = screenTitle },
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val horizontalPadding = if (maxWidth < 600.dp) {
                LyricsCardSpacing.large
            } else {
                LyricsCardSpacing.section
            }
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = SETTINGS_CONTENT_MAX_WIDTH),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    top = LyricsCardSpacing.large,
                    end = horizontalPadding,
                    bottom = LyricsCardSpacing.section,
                ),
                verticalArrangement = Arrangement.spacedBy(LyricsCardSpacing.extraLarge),
            ) {
                item {
                    SettingsSection(stringResource(R.string.settings_section_appearance)) {
                        SwitchSettingRow(
                            title = stringResource(R.string.settings_dark_mode),
                            subtitle = stringResource(R.string.settings_dark_mode_description),
                            checked = state.preferences.darkMode,
                            enabled = !state.isLoading && !state.isSavingPreference,
                            tag = SETTINGS_DARK_MODE_TAG,
                            onChecked = onDarkMode,
                        )
                    }
                }
                item {
                    SettingsSection(stringResource(R.string.settings_section_export)) {
                        QualitySettingRow(
                            value = state.preferences.defaultExportScale,
                            enabled = !state.isLoading && !state.isSavingPreference,
                            onValue = onDefaultExportScale,
                        )
                    }
                }
                item {
                    SettingsSection(stringResource(R.string.settings_section_editing)) {
                        SwitchSettingRow(
                            title = stringResource(R.string.settings_safe_area),
                            subtitle = stringResource(R.string.settings_safe_area_description),
                            checked = state.preferences.showSafeArea,
                            enabled = !state.isLoading && !state.isSavingPreference,
                            tag = SETTINGS_SAFE_AREA_TAG,
                            onChecked = onShowSafeArea,
                        )
                    }
                }
                item {
                    SettingsSection(stringResource(R.string.settings_section_storage)) {
                        CacheActionRow(
                            isClearing = state.isClearingCache,
                            onClick = onClearExportCache,
                        )
                    }
                }
                item {
                    SettingsSection(
                        title = stringResource(R.string.settings_section_about_diagnostics),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ) {
                        if (state.isLoadingDiagnostics) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics {
                                        liveRegion = LiveRegionMode.Polite
                                    }
                                    .testTag(SETTINGS_DIAGNOSTICS_LOADING_TAG),
                            )
                        }
                        DiagnosticsRows(state.diagnostics)
                        state.diagnosticsError?.let { error ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                error.asString(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(LyricsCardSpacing.large)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            stringResource(R.string.settings_privacy_note),
                            modifier = Modifier.padding(LyricsCardSpacing.large),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LyricsCardSpacing.small)) {
        Text(
            title,
            modifier = Modifier
                .padding(horizontal = LyricsCardSpacing.small)
                .semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = containerColor,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    tag: String,
    onChecked: (Boolean) -> Unit,
) {
    val stateLabel = stringResource(
        if (checked) R.string.settings_state_on else R.string.settings_state_off,
    )
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                subtitle,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.clearAndSetSemantics { },
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChecked,
            )
            .semantics(mergeDescendants = true) {
                stateDescription = stateLabel
            }
            .testTag(tag),
        colors = settingsListItemColors(),
    )
}

@Composable
private fun QualitySettingRow(
    value: Int,
    enabled: Boolean,
    onValue: (Int) -> Unit,
) {
    val normalized = value.coerceIn(1, 2)
    val valueLabel = stringResource(
        if (normalized == 1) R.string.settings_scale_standard else R.string.settings_scale_high,
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_default_export_quality)) },
        supportingContent = { Text(stringResource(R.string.settings_default_export_quality_description)) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LyricsCardSpacing.extraSmall),
            ) {
                Text(valueLabel, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = enabled, role = Role.Button) {
                onValue(if (normalized == 1) 2 else 1)
            }
            .semantics(mergeDescendants = true) {
                stateDescription = valueLabel
            }
            .testTag(SETTINGS_EXPORT_QUALITY_TAG),
        colors = settingsListItemColors(),
    )
}

@Composable
private fun CacheActionRow(isClearing: Boolean, onClick: () -> Unit) {
    val stateLabel = stringResource(
        if (isClearing) R.string.settings_cache_clearing else R.string.settings_cache_ready,
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_clear_export_cache)) },
        supportingContent = {
            Text(
                stringResource(
                    if (isClearing) {
                        R.string.settings_cache_clearing
                    } else {
                        R.string.settings_clear_export_cache_description
                    },
                ),
            )
        },
        trailingContent = {
            if (isClearing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .clearAndSetSemantics { },
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = !isClearing, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                stateDescription = stateLabel
            }
            .testTag(SETTINGS_CLEAR_CACHE_TAG),
        colors = settingsListItemColors(),
    )
}

@Composable
private fun DiagnosticsRows(snapshot: DiagnosticsSnapshot?) {
    val unavailable = stringResource(R.string.common_unavailable)
    val appVersion = snapshot?.let {
        stringResource(R.string.settings_app_version_value, it.appVersionName, it.appVersionCode)
    } ?: unavailable
    val rendererVersion = snapshot?.rendererVersion ?: unavailable
    val schema = snapshot?.let {
        stringResource(R.string.settings_schema_value, it.rendererSchemaVersion)
    } ?: unavailable
    val protocol = snapshot?.rendererProtocolVersion?.let {
        stringResource(R.string.settings_protocol_value, it)
    } ?: unavailable
    val source = snapshot?.let { value ->
        val packageVersion = value.rendererSourcePackageVersion
        val commit = value.rendererSourceCommit?.take(DIAGNOSTIC_HASH_LENGTH)
        when {
            packageVersion != null && commit != null -> stringResource(
                R.string.settings_renderer_source_value,
                packageVersion,
                commit,
            )
            packageVersion != null -> packageVersion
            commit != null -> commit
            else -> unavailable
        }
    } ?: unavailable
    val webView = snapshot?.let { value ->
        val packageName = value.systemWebViewPackage
        val version = value.systemWebViewVersion
        if (packageName != null && version != null) {
            stringResource(R.string.settings_package_version_value, packageName, version)
        } else {
            unavailable
        }
    } ?: unavailable

    DiagnosticRow(
        stringResource(R.string.settings_app_version),
        appVersion,
        SETTINGS_APP_VERSION_TAG,
    )
    DiagnosticDivider()
    DiagnosticRow(
        stringResource(R.string.settings_renderer),
        rendererVersion,
        SETTINGS_RENDERER_VERSION_TAG,
    )
    DiagnosticDivider()
    DiagnosticRow(
        stringResource(R.string.settings_render_spec_schema),
        schema,
        SETTINGS_SCHEMA_TAG,
    )
    DiagnosticDivider()
    DiagnosticRow(
        stringResource(R.string.settings_renderer_protocol),
        protocol,
        SETTINGS_PROTOCOL_TAG,
    )
    DiagnosticDivider()
    DiagnosticRow(
        stringResource(R.string.settings_renderer_source),
        source,
        SETTINGS_RENDERER_SOURCE_TAG,
    )
    DiagnosticDivider()
    DiagnosticRow(
        stringResource(R.string.settings_system_webview),
        webView,
        SETTINGS_WEBVIEW_TAG,
    )
}

@Composable
private fun DiagnosticRow(label: String, value: String, tag: String) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                value,
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        colors = settingsListItemColors(),
    )
}

@Composable
private fun DiagnosticDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = LyricsCardSpacing.large),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun settingsListItemColors() = ListItemDefaults.colors(
    containerColor = Color.Transparent,
)

internal const val SETTINGS_DARK_MODE_TAG = "settings-dark-mode"
internal const val SETTINGS_EXPORT_QUALITY_TAG = "settings-export-quality"
internal const val SETTINGS_SAFE_AREA_TAG = "settings-safe-area"
internal const val SETTINGS_CLEAR_CACHE_TAG = "settings-clear-cache"
internal const val SETTINGS_DIAGNOSTICS_LOADING_TAG = "settings-diagnostics-loading"
internal const val SETTINGS_APP_VERSION_TAG = "settings-app-version"
internal const val SETTINGS_RENDERER_VERSION_TAG = "settings-renderer-version"
internal const val SETTINGS_SCHEMA_TAG = "settings-schema"
internal const val SETTINGS_PROTOCOL_TAG = "settings-protocol"
internal const val SETTINGS_RENDERER_SOURCE_TAG = "settings-renderer-source"
internal const val SETTINGS_WEBVIEW_TAG = "settings-webview"

private val SETTINGS_CONTENT_MAX_WIDTH = 760.dp
private const val DIAGNOSTIC_HASH_LENGTH = 12
