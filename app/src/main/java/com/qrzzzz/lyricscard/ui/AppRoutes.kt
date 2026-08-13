package com.qrzzzz.lyricscard.ui

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class EditorRoute(val projectId: String)

@Serializable
data class ExportRoute(val projectId: String)

@Serializable
data object SettingsRoute
