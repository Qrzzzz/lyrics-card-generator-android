package com.qrzzzz.lyricscard.renderer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.qrzzzz.lyricscard.BuildConfig
import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.model.RenderSpecJson
import com.qrzzzz.lyricscard.model.PaletteSpec
import com.qrzzzz.lyricscard.model.requireValid
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private const val EXPORT_CHUNK_BYTES = 384 * 1024
private const val MAX_BASE64_CHARS_PER_CHUNK = ((EXPORT_CHUNK_BYTES + 2) / 3) * 4
private const val MAX_PNG_BYTES = 64L * 1024L * 1024L
private val MAX_EXPORT_CHUNKS = ((MAX_PNG_BYTES + EXPORT_CHUNK_BYTES - 1) / EXPORT_CHUNK_BYTES).toInt()

@Serializable
data class RendererEnvelope(
    val protocolVersion: Int = 1,
    val requestId: String,
    val type: String,
    val payload: JsonElement = JsonObject(emptyMap()),
)

data class RendererStatus(
    val phase: Phase = Phase.STARTING,
    val message: String = "正在启动本地渲染器…",
    val lastRenderMillis: Long? = null,
) {
    enum class Phase { STARTING, READY, RENDERING, EXPORTING, ERROR }
}

data class ExportedImage(
    val file: File,
    val width: Int,
    val height: Int,
    val mimeType: String = "image/png",
)

data class CanvasMeasurement(val width: Int, val height: Int)

class RendererException(message: String, cause: Throwable? = null) : Exception(message, cause)

class RendererController(
    context: Context,
    private val assetStore: ProjectAssetStore,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<RendererEnvelope>>()
    private val exportAssemblies = ConcurrentHashMap<String, ExportAssembly>()
    private val previewRequestIds = ConcurrentHashMap.newKeySet<String>()
    private val operationMutex = Mutex()
    private val _status = MutableStateFlow(RendererStatus())
    val status: StateFlow<RendererStatus> = _status.asStateFlow()
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    private var webView: WebView? = null
    private var pendingSpec: RenderSpec? = null
    private var specJob: Job? = null
    private var recoveryAttempts = 0
    private var closed = false

    @SuppressLint("SetJavaScriptEnabled")
    fun createWebView(context: Context): WebView {
        check(!closed) { "RendererController is closed" }
        webView?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(APP_ASSET_HOST)
            .addPathHandler("/renderer/") { path -> openRendererAsset(path) }
            .addPathHandler("/media/") { path -> assetStore.openForWebView(path) }
            .build()

        return WebView(context).also { view ->
            webView = view
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            view.setBackgroundColor(Color.TRANSPARENT)
            view.isLongClickable = false
            view.setOnLongClickListener { true }
            view.setDownloadListener { _, _, _, _, _ -> Unit }
            view.webChromeClient = WebChromeClient()
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                builtInZoomControls = false
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                blockNetworkLoads = true
                loadsImagesAutomatically = true
                mediaPlaybackRequiresUserGesture = true
                safeBrowsingEnabled = true
            }
            view.webViewClient = secureClient(assetLoader)

            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(
                    view,
                    BRIDGE_OBJECT,
                    setOf(TRUSTED_ORIGIN),
                ) { _, message, sourceOrigin, isMainFrame, _ ->
                    if (isMainFrame && sourceOrigin.toString() == TRUSTED_ORIGIN) {
                        message.data?.let(::handleMessage)
                    }
                }
                view.loadUrl(RENDERER_URL)
            } else {
                _status.value = RendererStatus(
                    phase = RendererStatus.Phase.ERROR,
                    message = "当前 Android System WebView 不支持安全消息通道，请先更新 WebView。",
                )
                webView = null
                view.destroy()
            }
        }
    }

    /** Starts the renderer while the user is still on the project list. Must run on the main thread. */
    fun warmUp() {
        if (closed || webView != null) return
        check(Looper.myLooper() == Looper.getMainLooper()) { "Renderer warm-up must run on the main thread" }
        createWebView(appContext)
    }

    fun updateSpec(spec: RenderSpec) {
        pendingSpec = spec.requireValid()
        specJob?.cancel()
        specJob = scope.launchSafely {
            delay(PREVIEW_DEBOUNCE_MS)
            if (_status.value.phase in setOf(RendererStatus.Phase.READY, RendererStatus.Phase.RENDERING)) {
                val value = pendingSpec ?: return@launchSafely
                val requestId = UUID.randomUUID().toString()
                _status.value = RendererStatus(RendererStatus.Phase.RENDERING, "正在更新预览…")
                previewRequestIds += requestId
                try {
                    send(
                        RendererEnvelope(
                            requestId = requestId,
                            type = "setSpec",
                            payload = RenderSpecJson.format.encodeToJsonElement(RenderSpec.serializer(), value),
                        ),
                    )
                } catch (cause: Throwable) {
                    previewRequestIds -= requestId
                    throw cause
                }
            }
        }
    }

    suspend fun measure(spec: RenderSpec): CanvasMeasurement = operationMutex.withLock {
        measureUnlocked(spec.requireValid())
    }

    suspend fun exportPng(spec: RenderSpec, pixelRatio: Int): ExportedImage = operationMutex.withLock {
        require(pixelRatio in 1..2) { "Alpha 仅支持 1 倍或 2 倍导出" }
        var exportSpec = spec.copy(canvas = spec.canvas.copy(pixelRatio = pixelRatio)).requireValid()
        _status.value = RendererStatus(RendererStatus.Phase.EXPORTING, "正在生成 PNG…")

        if (exportSpec.canvas.autoHeight) {
            val measurement = measureUnlocked(exportSpec)
            exportSpec = exportSpec.copy(canvas = exportSpec.canvas.copy(height = measurement.height)).requireValid()
        }

        val applied = request(
            type = "setSpec",
            payload = RenderSpecJson.format.encodeToJsonElement(RenderSpec.serializer(), exportSpec),
            timeoutMillis = SPEC_TIMEOUT_MS,
        )
        if (applied.type != "specApplied") {
            throw RendererException("渲染器未确认当前设置")
        }

        val image = requestExport(
            payload = buildJsonObject {
                put("pixelRatio", pixelRatio)
                put("spec", RenderSpecJson.format.encodeToJsonElement(RenderSpec.serializer(), exportSpec))
            },
            spec = exportSpec,
        )
        _status.value = RendererStatus(RendererStatus.Phase.READY, "导出完成")
        image
    }

    private suspend fun measureUnlocked(spec: RenderSpec): CanvasMeasurement {
        val measured = request(
            type = "measure",
            payload = RenderSpecJson.format.encodeToJsonElement(RenderSpec.serializer(), spec),
            timeoutMillis = SPEC_TIMEOUT_MS,
        )
        if (measured.type != "measured") throw RendererException("渲染器未返回画布尺寸")
        val payload = measured.payload.jsonObject
        val width = payload["width"]?.jsonPrimitive?.intOrNull ?: throw RendererException("测量结果缺少宽度")
        val height = payload["height"]?.jsonPrimitive?.intOrNull ?: throw RendererException("测量结果缺少高度")
        require(width == spec.canvas.width && height in 720..3200) { "渲染器返回的自动高度无效" }
        return CanvasMeasurement(width, height)
    }

    suspend fun extractPalette(assetId: String): PaletteSpec {
        val response = request(
            type = "extractPalette",
            payload = buildJsonObject { put("assetId", assetId) },
            timeoutMillis = SPEC_TIMEOUT_MS,
        )
        if (response.type != "paletteExtracted") {
            throw RendererException("渲染器未返回调色板")
        }
        val payload = response.payload.jsonObject
        return PaletteSpec(
            dominant = payload["dominant"]?.jsonPrimitive?.contentOrNull ?: error("缺少主色"),
            secondary = payload["secondary"]?.jsonPrimitive?.contentOrNull ?: error("缺少辅色"),
            accent = payload["accent"]?.jsonPrimitive?.contentOrNull ?: error("缺少强调色"),
        )
    }

    fun retry() {
        recoveryAttempts = 0
        _status.value = RendererStatus()
        destroyWebView()
        _generation.value += 1
    }

    fun close() {
        if (closed) return
        closed = true
        specJob?.cancel()
        pending.values.forEach { it.cancel() }
        pending.clear()
        exportAssemblies.values.forEach(ExportAssembly::abort)
        exportAssemblies.clear()
        previewRequestIds.clear()
        destroyWebView()
        scope.cancel()
    }

    private suspend fun request(
        type: String,
        payload: JsonElement,
        timeoutMillis: Long,
    ): RendererEnvelope {
        awaitReady()
        val requestId = UUID.randomUUID().toString()
        val result = CompletableDeferred<RendererEnvelope>()
        pending[requestId] = result
        try {
            withContext(Dispatchers.Main.immediate) {
                send(RendererEnvelope(requestId = requestId, type = type, payload = payload))
            }
            return withTimeout(timeoutMillis) { result.await() }
        } finally {
            pending.remove(requestId)
            if (!result.isCompleted) sendCancel(requestId)
        }
    }

    private suspend fun requestExport(payload: JsonElement, spec: RenderSpec): ExportedImage {
        awaitReady()
        val requestId = UUID.randomUUID().toString()
        val result = CompletableDeferred<RendererEnvelope>()
        val assembly = withContext(Dispatchers.IO) { createExportAssembly(spec) }
        pending[requestId] = result
        exportAssemblies[requestId] = assembly
        var completedNormally = false
        try {
            withContext(Dispatchers.Main.immediate) {
                send(RendererEnvelope(requestId = requestId, type = "exportPng", payload = payload))
            }
            val completed = withTimeout(EXPORT_TIMEOUT_MS) { result.await() }
            if (completed.type != "exportCompleted") {
                throw RendererException("渲染器返回了意外结果：${completed.type}")
            }
            val image = finalizeExport(completed.payload.jsonObject, spec, assembly)
            completedNormally = true
            return image
        } finally {
            pending.remove(requestId)
            exportAssemblies.remove(requestId)
            if (!result.isCompleted) sendCancel(requestId)
            if (!completedNormally) {
                withContext(NonCancellable + Dispatchers.IO) { assembly.abort() }
            }
        }
    }

    private suspend fun sendCancel(targetRequestId: String) {
        withContext(NonCancellable + Dispatchers.Main.immediate) {
            runCatching {
                send(
                    RendererEnvelope(
                        requestId = UUID.randomUUID().toString(),
                        type = "cancel",
                        payload = buildJsonObject { put("requestId", targetRequestId) },
                    ),
                )
            }
        }
    }

    private suspend fun awaitReady() {
        withTimeout(READY_TIMEOUT_MS) {
            while (_status.value.phase == RendererStatus.Phase.STARTING) {
                delay(25)
            }
        }
        if (_status.value.phase == RendererStatus.Phase.ERROR) {
            throw RendererException(_status.value.message)
        }
    }

    private fun send(envelope: RendererEnvelope) {
        val json = RenderSpecJson.format.encodeToString(envelope)
        val quoted = JsonPrimitive(json).toString()
        val script = "window.LyricsCardRenderer && window.LyricsCardRenderer.receive(JSON.parse($quoted));"
        webView?.evaluateJavascript(script, null)
            ?: throw RendererException("本地渲染器尚未创建")
    }

    private fun handleMessage(raw: String) {
        val envelope = runCatching {
            RenderSpecJson.format.decodeFromString(RendererEnvelope.serializer(), raw)
        }.getOrElse { cause ->
            _status.value = RendererStatus(RendererStatus.Phase.ERROR, "渲染器消息格式无效")
            return
        }
        if (envelope.protocolVersion != PROTOCOL_VERSION) {
            _status.value = RendererStatus(RendererStatus.Phase.ERROR, "渲染协议版本不兼容")
            return
        }

        when (envelope.type) {
            "ready" -> {
                _status.value = RendererStatus(RendererStatus.Phase.READY, "本地渲染器已就绪")
                pendingSpec?.let(::updateSpec)
            }
            "specApplied" -> {
                val result = pending.remove(envelope.requestId)
                val wasPreview = previewRequestIds.remove(envelope.requestId)
                if (result != null || wasPreview) {
                    _status.value = RendererStatus(RendererStatus.Phase.READY, "预览已更新")
                    result?.complete(envelope)
                }
            }
            "exportStarted" -> {
                if (exportAssemblies.containsKey(envelope.requestId)) {
                    _status.value = RendererStatus(RendererStatus.Phase.EXPORTING, "正在绘制高清图片…")
                }
            }
            "exportChunk" -> {
                val assembly = exportAssemblies[envelope.requestId] ?: return
                runCatching {
                    val payload = envelope.payload.jsonObject
                    assembly.accept(
                        index = payload["index"]?.jsonPrimitive?.intOrNull
                            ?: throw RendererException("导出分块缺少序号"),
                        total = payload["total"]?.jsonPrimitive?.intOrNull
                            ?: throw RendererException("导出分块缺少总数"),
                        byteLength = payload["byteLength"]?.jsonPrimitive?.intOrNull
                            ?: throw RendererException("导出分块缺少字节数"),
                        encoded = payload["base64"]?.jsonPrimitive?.contentOrNull
                            ?: throw RendererException("导出分块缺少数据"),
                    )
                }.onFailure { cause ->
                    exportAssemblies.remove(envelope.requestId)
                    assembly.abort()
                    val failure = if (cause is RendererException) cause else {
                        RendererException(cause.message ?: "导出分块无效", cause)
                    }
                    _status.value = RendererStatus(RendererStatus.Phase.ERROR, failure.message ?: "导出分块无效")
                    pending.remove(envelope.requestId)?.completeExceptionally(failure)
                    scope.launch { sendCancel(envelope.requestId) }
                }
            }
            "renderError" -> {
                val result = pending.remove(envelope.requestId)
                val wasPreview = previewRequestIds.remove(envelope.requestId)
                if (result != null || wasPreview) {
                    val payload = envelope.payload.jsonObject
                    val message = payload["message"]?.jsonPrimitive?.contentOrNull ?: "渲染失败"
                    exportAssemblies.remove(envelope.requestId)?.abort()
                    _status.value = RendererStatus(RendererStatus.Phase.ERROR, message)
                    result?.completeExceptionally(RendererException(message))
                }
            }
            "exportCompleted", "measured", "paletteExtracted", "pong" -> {
                pending.remove(envelope.requestId)?.complete(envelope)
            }
        }
    }

    private fun createExportAssembly(spec: RenderSpec): ExportAssembly {
        val outputDir = File(appContext.cacheDir, "exports").apply {
            check(isDirectory || mkdirs()) { "无法创建导出缓存目录" }
        }
        val safeTitle = spec.song.title
            .ifBlank { "lyrics-card" }
            .replace(Regex("[^A-Za-z0-9\\u4e00-\\u9fff_-]+"), "-")
            .take(48)
        val suffix = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        return ExportAssembly(
            partFile = File(outputDir, ".$safeTitle-$suffix.png.part"),
            finalFile = File(outputDir, "$safeTitle-$suffix.png"),
        )
    }

    private suspend fun finalizeExport(
        payload: JsonObject,
        spec: RenderSpec,
        assembly: ExportAssembly,
    ): ExportedImage =
        withContext(Dispatchers.IO) {
            val mimeType = payload["mimeType"]?.jsonPrimitive?.contentOrNull
                ?: throw RendererException("导出结果缺少 MIME 类型")
            require(mimeType == "image/png") { "仅允许 PNG 导出" }
            val reportedWidth = payload["width"]?.jsonPrimitive?.intOrNull
                ?: throw RendererException("导出结果缺少宽度")
            val reportedHeight = payload["height"]?.jsonPrimitive?.intOrNull
                ?: throw RendererException("导出结果缺少高度")
            val reportedBytes = payload["totalBytes"]?.jsonPrimitive?.longOrNull
                ?: throw RendererException("导出结果缺少总字节数")
            val reportedChunks = payload["totalChunks"]?.jsonPrimitive?.intOrNull
                ?: throw RendererException("导出结果缺少总块数")
            val expectedWidth = Math.multiplyExact(spec.canvas.width, spec.canvas.pixelRatio)
            val expectedHeight = Math.multiplyExact(spec.canvas.height, spec.canvas.pixelRatio)
            require(reportedWidth == expectedWidth && reportedHeight == expectedHeight) {
                "渲染器报告的导出尺寸与请求不一致"
            }

            try {
                val partFile = assembly.finish(reportedBytes, reportedChunks)
                require(partFile.length() in MIN_PNG_BYTES..MAX_PNG_BYTES) { "PNG 文件大小异常" }
                partFile.inputStream().use { input ->
                    val signature = ByteArray(PNG_SIGNATURE.size)
                    require(input.read(signature) == signature.size && signature.contentEquals(PNG_SIGNATURE)) {
                        "渲染器返回的文件不是有效 PNG"
                    }
                }
                partFile.inputStream().use { input ->
                    val skipped = input.skip(partFile.length() - PNG_IEND_TRAILER.size)
                    require(skipped == partFile.length() - PNG_IEND_TRAILER.size) { "PNG 文件不完整" }
                    val trailer = ByteArray(PNG_IEND_TRAILER.size)
                    require(input.read(trailer) == trailer.size && trailer.contentEquals(PNG_IEND_TRAILER)) {
                        "PNG 文件缺少完整结束标记"
                    }
                }
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(partFile.absolutePath, options)
                require(options.outWidth == expectedWidth && options.outHeight == expectedHeight) {
                    "PNG 实际尺寸与导出请求不一致"
                }
                check(partFile.renameTo(assembly.finalFile)) { "无法发布导出的 PNG" }
                ExportedImage(assembly.finalFile, options.outWidth, options.outHeight, mimeType)
            } catch (cause: Throwable) {
                assembly.abort()
                assembly.finalFile.delete()
                if (cause is RendererException) throw cause
                throw RendererException(cause.message ?: "写入 PNG 失败", cause)
            }
        }

    private fun secureClient(assetLoader: WebViewAssetLoader) = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !isAllowedRendererUrl(request.url)

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse {
            val url = request.url
            if (isAllowedRendererUrl(url) || isAllowedMediaUrl(url)) {
                assetLoader.shouldInterceptRequest(url)?.let { return it }
            }
            return blockedResponse()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) {
                _status.value = RendererStatus(
                    RendererStatus.Phase.ERROR,
                    "本地渲染器加载失败（${error.errorCode}）",
                )
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            val failure = RendererException("WebView 渲染进程已终止")
            pending.values.forEach { it.completeExceptionally(failure) }
            pending.clear()
            exportAssemblies.values.forEach(ExportAssembly::abort)
            exportAssemblies.clear()
            previewRequestIds.clear()
            view.destroy()
            webView = null
            if (!closed && recoveryAttempts < MAX_AUTOMATIC_RECOVERIES) {
                recoveryAttempts += 1
                _status.value = RendererStatus(
                    RendererStatus.Phase.STARTING,
                    if (detail.didCrash()) "WebView 渲染进程崩溃，正在自动恢复…" else "WebView 渲染进程已被系统回收，正在自动恢复…",
                )
                _generation.value += 1
            } else {
                _status.value = RendererStatus(
                    RendererStatus.Phase.ERROR,
                    if (detail.didCrash()) "WebView 渲染进程反复崩溃，请点击重试" else "WebView 渲染进程反复被回收，请点击重试",
                )
            }
            return true
        }
    }

    private fun destroyWebView() {
        val view = webView ?: return
        webView = null
        runCatching { view.stopLoading() }
        runCatching { view.loadUrl("about:blank") }
        runCatching { view.clearHistory() }
        runCatching { view.removeAllViews() }
        runCatching { view.destroy() }
    }

    private fun openRendererAsset(path: String): WebResourceResponse? {
        val assetPath = path.substringBefore('?').substringBefore('#')
        if (!SAFE_RENDERER_PATH.matches(assetPath) || assetPath.split('/').any { it == ".." }) return null
        return runCatching {
            val mime = when (assetPath.substringAfterLast('.', "")) {
                "html" -> "text/html"
                "js" -> "application/javascript"
                "css" -> "text/css"
                "json" -> "application/json"
                "svg" -> "image/svg+xml"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "woff2" -> "font/woff2"
                "otf" -> "font/otf"
                else -> "application/octet-stream"
            }
            val cacheControl = when {
                assetPath == "index.html" || assetPath.endsWith(".json") -> "no-cache"
                else -> "public, max-age=31536000, immutable"
            }
            WebResourceResponse(
                mime,
                if (mime.startsWith("text/") || mime.contains("json") || mime.contains("javascript")) "utf-8" else null,
                appContext.assets.open("renderer/$assetPath"),
            ).also {
                it.responseHeaders = mapOf(
                    "Cache-Control" to cacheControl,
                    "X-Content-Type-Options" to "nosniff",
                )
            }
        }.getOrNull()
    }

    private fun blockedResponse() = WebResourceResponse(
        "text/plain",
        "utf-8",
        403,
        "Blocked",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream("Blocked by Lyrics Card renderer policy".toByteArray()),
    )

    private fun isAllowedRendererUrl(uri: Uri): Boolean =
        uri.scheme == "https" && uri.host == APP_ASSET_HOST && uri.path?.startsWith("/renderer/") == true

    private fun isAllowedMediaUrl(uri: Uri): Boolean =
        uri.scheme == "https" && uri.host == APP_ASSET_HOST && uri.path?.startsWith("/media/") == true

    private fun CoroutineScope.launchSafely(block: suspend CoroutineScope.() -> Unit): Job =
        launch {
            runCatching { block() }.onFailure { cause ->
                if (cause !is kotlinx.coroutines.CancellationException) {
                    _status.value = RendererStatus(
                        RendererStatus.Phase.ERROR,
                        cause.message ?: "渲染器通信失败",
                    )
                }
            }
        }

    private companion object {
        const val APP_ASSET_HOST = "appassets.androidplatform.net"
        const val TRUSTED_ORIGIN = "https://$APP_ASSET_HOST"
        const val RENDERER_URL = "$TRUSTED_ORIGIN/renderer/index.html"
        const val BRIDGE_OBJECT = "LyricsCardNative"
        const val PROTOCOL_VERSION = 1
        const val PREVIEW_DEBOUNCE_MS = 120L
        const val READY_TIMEOUT_MS = 10_000L
        const val SPEC_TIMEOUT_MS = 8_000L
        const val EXPORT_TIMEOUT_MS = 30_000L
        const val MAX_AUTOMATIC_RECOVERIES = 2
        val SAFE_RENDERER_PATH = Regex("^[A-Za-z0-9._/-]+$")
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        val PNG_IEND_TRAILER = byteArrayOf(
            0x00, 0x00, 0x00, 0x00,
            0x49, 0x45, 0x4E, 0x44,
            0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
        )
        val MIN_PNG_BYTES = (PNG_SIGNATURE.size + PNG_IEND_TRAILER.size).toLong()
    }
}

internal class ExportAssembly(
    val partFile: File,
    val finalFile: File,
) {
    private var sink: FileOutputStream? = FileOutputStream(partFile, false)
    private var nextIndex = 0
    private var expectedTotal: Int? = null
    private var receivedBytes = 0L

    @Synchronized
    fun accept(index: Int, total: Int, byteLength: Int, encoded: String) {
        val output = sink ?: throw RendererException("导出临时文件已关闭")
        require(total in 1..MAX_EXPORT_CHUNKS) { "导出分块总数无效" }
        require(index == nextIndex && index in 0 until total) { "导出分块顺序无效" }
        require(expectedTotal == null || expectedTotal == total) { "导出分块总数不一致" }
        require(byteLength in 0..EXPORT_CHUNK_BYTES) { "导出分块字节数无效" }
        require(encoded.length <= MAX_BASE64_CHARS_PER_CHUNK) { "导出分块超过内存保护上限" }
        val decoded = runCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse { throw RendererException("导出分块 Base64 无效", it) }
        require(decoded.size == byteLength) { "导出分块字节数不一致" }
        require(receivedBytes + decoded.size <= MAX_PNG_BYTES) { "导出图片超过 Alpha 文件大小上限" }
        output.write(decoded)
        expectedTotal = total
        receivedBytes += decoded.size
        nextIndex += 1
    }

    @Synchronized
    fun finish(reportedBytes: Long, reportedChunks: Int): File {
        val output = sink ?: throw RendererException("导出临时文件已关闭")
        val expected = expectedTotal ?: throw RendererException("导出没有收到任何数据分块")
        require(reportedChunks == expected && nextIndex == expected) { "导出分块数量不完整" }
        require(reportedBytes == receivedBytes && reportedBytes in 1..MAX_PNG_BYTES) { "导出总字节数不一致" }
        output.flush()
        output.fd.sync()
        output.close()
        sink = null
        require(partFile.length() == receivedBytes) { "导出临时文件长度不一致" }
        return partFile
    }

    @Synchronized
    fun abort() {
        runCatching { sink?.close() }
        sink = null
        partFile.delete()
    }

}
