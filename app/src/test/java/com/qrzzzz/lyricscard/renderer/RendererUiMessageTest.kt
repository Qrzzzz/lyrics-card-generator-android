package com.qrzzzz.lyricscard.renderer

import org.junit.Assert.assertEquals
import org.junit.Test

class RendererUiMessageTest {
    @Test
    fun `known actionable failures map to typed UI keys`() {
        assertEquals(
            RendererUiMessageKey.WEBVIEW_UNSUPPORTED,
            error("当前 Android System WebView 不支持安全消息通道").uiMessageKey(),
        )
        assertEquals(
            RendererUiMessageKey.PROTOCOL_INCOMPATIBLE,
            error("渲染协议版本不兼容").uiMessageKey(),
        )
        assertEquals(
            RendererUiMessageKey.PROCESS_UNSTABLE,
            error("WebView 渲染进程反复崩溃").uiMessageKey(),
        )
        assertEquals(
            RendererUiMessageKey.INVALID_RESPONSE,
            error("导出分块缺少数据").uiMessageKey(),
        )
        assertEquals(RendererUiMessageKey.LOAD_FAILED, error("本地渲染器加载失败").uiMessageKey())
    }

    @Test
    fun `untrusted renderer payload remains a generic error key`() {
        assertEquals(
            RendererUiMessageKey.GENERIC_ERROR,
            error("private payload details must not be displayed").uiMessageKey(),
        )
    }

    @Test
    fun `recovery and normal phases retain distinct status keys`() {
        assertEquals(
            RendererUiMessageKey.RECOVERING,
            RendererStatus(RendererStatus.Phase.STARTING, "WebView 正在自动恢复").uiMessageKey(),
        )
        assertEquals(
            RendererUiMessageKey.EXPORTING,
            RendererStatus(RendererStatus.Phase.EXPORTING, "payload").uiMessageKey(),
        )
    }

    private fun error(message: String) = RendererStatus(RendererStatus.Phase.ERROR, message)
}
