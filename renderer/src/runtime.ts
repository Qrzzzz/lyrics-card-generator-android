import manifest from "../renderer-manifest.json";
import { DEFAULT_RENDER_SPEC } from "./defaultSpec";
import { ExportRenderError } from "./export";
import { extractPaletteFromAsset, PaletteExtractionError } from "./palette";
import { InvalidRenderSpecError, parseRenderSpec } from "./spec";
import { blobToBase64Chunks, createEnvelope, parseHostEnvelope, ProtocolMessageError, readObjectPayload } from "./transport";
import {
  RENDERER_VERSION,
  type HostEnvelope,
  type RendererController,
  type RendererErrorCode,
  type RendererMessageType,
  type RenderSpec
} from "./types";

let controller: RendererController | null = null;
let activeSpec: RenderSpec = DEFAULT_RENDER_SPEC;
let activeExportRequestId: string | null = null;
let exportSerial: Promise<void> = Promise.resolve();
const cancelledRequests = new Set<string>();
let listenersInstalled = false;

export function installRendererController(nextController: RendererController) {
  controller = nextController;
  installMessageListeners();
  void sendReady("renderer-ready");
  return () => {
    if (controller === nextController) controller = null;
  };
}

async function handleIncoming(input: unknown) {
  let message: HostEnvelope;
  try {
    message = parseHostEnvelope(input);
  } catch (error) {
    if (error instanceof ProtocolMessageError) {
      sendError(
        error.requestId,
        error.code === "UNSUPPORTED_PROTOCOL" ? "UNSUPPORTED_PROTOCOL" : "INVALID_SPEC",
        error.message,
        false
      );
      return;
    }
    sendError("unknown", "INVALID_SPEC", "Message could not be parsed", false);
    return;
  }

  try {
    switch (message.type) {
      case "initialize":
        await sendReady(message.requestId);
        return;
      case "ping":
        send(message.requestId, "pong", { rendererVersion: RENDERER_VERSION, timestamp: Date.now() });
        return;
      case "setSpec":
        await applySpecMessage(message);
        return;
      case "measure":
        await measureMessage(message);
        return;
      case "extractPalette":
        await extractPaletteMessage(message);
        return;
      case "exportPng":
        {
          const run = exportSerial.catch(() => undefined).then(() => exportMessage(message));
          exportSerial = run.catch(() => undefined);
          await run;
        }
        return;
      case "cancel": {
        const payload = readObjectPayload(message.payload);
        const target = typeof payload.requestId === "string" ? payload.requestId : activeExportRequestId;
        if (target) cancelledRequests.add(target);
        return;
      }
    }
  } catch (error) {
    reportOperationError(message.requestId, error);
  }
}

async function applySpecMessage(message: HostEnvelope) {
  const nextSpec = extractSpec(message.payload);
  const activeController = requireController();
  await activeController.applySpec(nextSpec);
  activeSpec = nextSpec;
  send(message.requestId, "specApplied", {
    schemaVersion: nextSpec.schemaVersion,
    width: nextSpec.canvas.width,
    height: nextSpec.canvas.height
  });
}

async function measureMessage(message: HostEnvelope) {
  const nextSpec = extractSpec(message.payload, true);
  const activeController = requireController();
  const measurement = await activeController.measure(nextSpec);
  activeSpec = nextSpec;
  send(message.requestId, "measured", measurement);
}

async function extractPaletteMessage(message: HostEnvelope) {
  const payload = readObjectPayload(message.payload);
  const assetId = typeof payload.assetId === "string" ? payload.assetId : "";
  const palette = await extractPaletteFromAsset(assetId);
  send(message.requestId, "paletteExtracted", palette);
}

async function exportMessage(message: HostEnvelope) {
  if (cancelledRequests.has(message.requestId)) {
    cancelledRequests.delete(message.requestId);
    throw exportCancelledError();
  }
  activeExportRequestId = message.requestId;

  const payload = readObjectPayload(message.payload);
  const nextSpec = payload.spec !== undefined
    ? parseRenderSpec(payload.spec)
    : payload.schemaVersion !== undefined
      ? parseRenderSpec(payload)
      : activeSpec;
  const requestedPixelRatio = payload.pixelRatio ?? nextSpec.canvas.pixelRatio;
  const pixelRatio = requestedPixelRatio === 1 ? 1 : 2;
  const activeController = requireController();

  send(message.requestId, "exportStarted", {
    width: nextSpec.canvas.width,
    height: nextSpec.canvas.height,
    pixelRatio
  });
  send(message.requestId, "exportProgress", { progress: 0.1, phase: "render" });

  try {
    const blob = await activeController.exportPng(nextSpec, pixelRatio);
    assertNotCancelled(message.requestId);
    send(message.requestId, "exportProgress", { progress: 0.7, phase: "encode" });
    const totalChunks = await blobToBase64Chunks(blob, (chunk) => {
      assertNotCancelled(message.requestId);
      send(message.requestId, "exportChunk", chunk);
    });
    activeSpec = nextSpec;
    send(message.requestId, "exportCompleted", {
      mimeType: "image/png",
      width: nextSpec.canvas.width * pixelRatio,
      height: nextSpec.canvas.height * pixelRatio,
      totalBytes: blob.size,
      totalChunks
    });
  } finally {
    if (activeExportRequestId === message.requestId) activeExportRequestId = null;
    cancelledRequests.delete(message.requestId);
  }
}

function extractSpec(payload: unknown, allowCurrent = false) {
  const object = readObjectPayload(payload);
  if (object.spec !== undefined) return parseRenderSpec(object.spec);
  if (allowCurrent && Object.keys(object).length === 0) return activeSpec;
  return parseRenderSpec(payload);
}

function assertNotCancelled(requestId: string) {
  if (cancelledRequests.has(requestId) || activeExportRequestId !== requestId) {
    throw exportCancelledError();
  }
}

function exportCancelledError() {
  const error = new Error("PNG export was cancelled") as Error & { code: RendererErrorCode };
  error.code = "EXPORT_CANCELLED";
  return error;
}

function requireController() {
  if (!controller) {
    const error = new Error("Renderer is not ready") as Error & { code: RendererErrorCode };
    error.code = "RENDERER_NOT_READY";
    throw error;
  }
  return controller;
}

async function sendReady(requestId: string) {
  if (!controller) return;
  send(requestId, "ready", {
    rendererVersion: RENDERER_VERSION,
    schemaVersion: 1,
    protocolVersion: 1,
    sourceCommit: manifest.sourceCommit,
    fontManifestHash: manifest.fontManifestHash,
    capabilities: ["setSpec", "measure", "extractPalette", "exportPng", "cancel", "ping"]
  });
}

function send<TPayload>(requestId: string, type: RendererMessageType, payload: TPayload) {
  const envelope = createEnvelope(requestId, type, payload);
  const serialized = JSON.stringify(envelope);
  if (window.LyricsCardNative?.postMessage) {
    window.LyricsCardNative.postMessage(serialized);
    return;
  }
  if (window.parent !== window) {
    window.parent.postMessage(envelope, window.location.origin);
    return;
  }
  window.dispatchEvent(new CustomEvent("lyrics-card-renderer-message", { detail: envelope }));
}

function sendError(
  requestId: string,
  code: RendererErrorCode,
  message: string,
  recoverable: boolean
) {
  send(requestId, "renderError", {
    code,
    message,
    requestId,
    rendererVersion: RENDERER_VERSION,
    recoverable
  });
}

function reportOperationError(requestId: string, error: unknown) {
  if (error instanceof InvalidRenderSpecError) {
    sendError(requestId, "INVALID_SPEC", error.message, true);
    return;
  }
  if (error instanceof ExportRenderError) {
    sendError(requestId, error.code, error.message, error.code !== "EXPORT_OUT_OF_MEMORY");
    return;
  }
  if (error instanceof PaletteExtractionError) {
    sendError(requestId, error.code, error.message, true);
    return;
  }
  const candidate = error as { code?: RendererErrorCode; message?: string };
  sendError(requestId, candidate.code ?? "EXPORT_FAILED", candidate.message ?? "Renderer operation failed", true);
}

function installMessageListeners() {
  if (listenersInstalled) return;
  listenersInstalled = true;
  window.LyricsCardRenderer = {
    receive(message: unknown) {
      void handleIncoming(message);
    }
  };
  window.addEventListener("message", (event) => {
    if (event.origin && event.origin !== "null" && event.origin !== window.location.origin) return;
    void handleIncoming(event.data);
  });
}

installMessageListeners();
