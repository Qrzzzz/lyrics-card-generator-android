package com.qrzzzz.lyricscard.renderer

/** Resource-key categories for native renderer chrome; raw renderer payloads are never displayed. */
internal enum class RendererUiMessageKey {
    STARTING,
    RECOVERING,
    READY,
    RENDERING,
    EXPORTING,
    WEBVIEW_UNSUPPORTED,
    PROTOCOL_INCOMPATIBLE,
    PROCESS_UNSTABLE,
    INVALID_RESPONSE,
    LOAD_FAILED,
    GENERIC_ERROR,
}

internal fun RendererStatus.uiMessageKey(): RendererUiMessageKey = when (phase) {
    RendererStatus.Phase.STARTING -> if (message.contains("自动恢复")) {
        RendererUiMessageKey.RECOVERING
    } else {
        RendererUiMessageKey.STARTING
    }
    RendererStatus.Phase.READY -> RendererUiMessageKey.READY
    RendererStatus.Phase.RENDERING -> RendererUiMessageKey.RENDERING
    RendererStatus.Phase.EXPORTING -> RendererUiMessageKey.EXPORTING
    RendererStatus.Phase.ERROR -> when {
        message.contains("Android System WebView") -> RendererUiMessageKey.WEBVIEW_UNSUPPORTED
        message.contains("协议") -> RendererUiMessageKey.PROTOCOL_INCOMPATIBLE
        message.contains("WebView") && (
            message.contains("进程") || message.contains("崩溃") || message.contains("回收")
        ) -> RendererUiMessageKey.PROCESS_UNSTABLE
        message.contains("加载失败") -> RendererUiMessageKey.LOAD_FAILED
        message.contains("格式") || message.contains("分块") || message.contains("MIME") ||
            message.contains("尺寸") || message.contains("PNG") -> RendererUiMessageKey.INVALID_RESPONSE
        else -> RendererUiMessageKey.GENERIC_ERROR
    }
}
