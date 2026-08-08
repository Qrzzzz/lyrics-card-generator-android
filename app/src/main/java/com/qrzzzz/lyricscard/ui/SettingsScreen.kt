package com.qrzzzz.lyricscard.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qrzzzz.lyricscard.BuildConfig
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
) {
    val context = LocalContext.current
    val preferences = state.preferences
    val unavailable = stringResource(R.string.common_unavailable)
    val manifestMissing = stringResource(R.string.settings_renderer_manifest_missing)
    val webViewPackage = remember(unavailable) {
        WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } ?: unavailable
    }
    val rendererManifest = remember(manifestMissing) {
        runCatching {
            context.assets.open("renderer/renderer-manifest.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        }.getOrElse { manifestMissing }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(LyricsCardSpacing.comfortable),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_general)) }
            item {
                SettingRow(
                    stringResource(R.string.settings_dark_mode),
                    stringResource(R.string.settings_dark_mode_description),
                    preferences.darkMode,
                    onDarkMode,
                )
            }
            item {
                Card(shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.settings_default_export_quality), fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2).forEach { value ->
                                FilterChip(
                                    selected = preferences.defaultExportScale == value,
                                    onClick = { onDefaultExportScale(value) },
                                    label = {
                                        Text(
                                            stringResource(
                                                if (value == 1) {
                                                    R.string.settings_scale_standard
                                                } else {
                                                    R.string.settings_scale_high
                                                },
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                SettingRow(
                    stringResource(R.string.settings_safe_area),
                    stringResource(R.string.settings_safe_area_description),
                    preferences.showSafeArea,
                    onShowSafeArea,
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_storage)) }
            item {
                OutlinedButton(
                    onClick = onClearExportCache,
                    enabled = !state.isClearingCache,
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                    Text(
                        stringResource(R.string.settings_clear_export_cache),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            state.cacheStatus?.let { value ->
                item { Text(value.asString(), color = MaterialTheme.colorScheme.primary) }
            }
            state.errorMessage?.let { value ->
                item { Text(value.asString(), color = MaterialTheme.colorScheme.error) }
            }

            item { SectionHeader(stringResource(R.string.settings_section_diagnostics)) }
            item {
                DiagnosticCard(
                    rows = listOf(
                        stringResource(R.string.settings_app_version) to BuildConfig.VERSION_NAME,
                        stringResource(R.string.settings_renderer) to BuildConfig.RENDERER_VERSION,
                        stringResource(R.string.settings_render_spec_schema) to BuildConfig.RENDERER_SCHEMA_VERSION.toString(),
                        stringResource(R.string.settings_windows_baseline) to BuildConfig.BASELINE_COMMIT.take(12),
                        stringResource(R.string.settings_system_webview) to webViewPackage,
                    ),
                )
            }
            item {
                Text(stringResource(R.string.renderer_manifest_label), style = MaterialTheme.typography.labelLarge)
                Card(shape = MaterialTheme.shapes.medium) {
                    Text(
                        rendererManifest,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.settings_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(value: String) {
    Text(value, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Card(shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun DiagnosticCard(rows: List<Pair<String, String>>) {
    Card(shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

