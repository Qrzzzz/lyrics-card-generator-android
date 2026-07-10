import { PROTOCOL_VERSION, type HostEnvelope, type HostMessageType, type ProtocolEnvelope } from "./types";

const HOST_MESSAGE_TYPES = new Set<HostMessageType>([
  "initialize",
  "setSpec",
  "measure",
  "extractPalette",
  "exportPng",
  "cancel",
  "ping"
]);

export class ProtocolMessageError extends Error {
  constructor(
    message: string,
    readonly code: "UNSUPPORTED_PROTOCOL" | "INVALID_MESSAGE",
    readonly requestId = "unknown"
  ) {
    super(message);
    this.name = "ProtocolMessageError";
  }
}

export function parseHostEnvelope(input: unknown): HostEnvelope {
  let candidate: unknown = input;
  if (typeof candidate === "string") {
    try {
      candidate = JSON.parse(candidate) as unknown;
    } catch {
      throw new ProtocolMessageError("Message must be valid JSON", "INVALID_MESSAGE");
    }
  }

  if (!isRecord(candidate)) {
    throw new ProtocolMessageError("Message must be an object", "INVALID_MESSAGE");
  }

  const requestId = typeof candidate.requestId === "string" ? candidate.requestId : "unknown";
  if (candidate.protocolVersion !== PROTOCOL_VERSION) {
    throw new ProtocolMessageError(
      `Unsupported protocol version: ${String(candidate.protocolVersion)}`,
      "UNSUPPORTED_PROTOCOL",
      requestId
    );
  }
  if (!isRequestId(candidate.requestId)) {
    throw new ProtocolMessageError("requestId must contain 1..128 safe characters", "INVALID_MESSAGE", requestId);
  }
  if (typeof candidate.type !== "string" || !HOST_MESSAGE_TYPES.has(candidate.type as HostMessageType)) {
    throw new ProtocolMessageError(`Unsupported message type: ${String(candidate.type)}`, "INVALID_MESSAGE", requestId);
  }

  return {
    protocolVersion: PROTOCOL_VERSION,
    requestId: candidate.requestId,
    type: candidate.type as HostMessageType,
    payload: candidate.payload ?? {}
  };
}

export function createEnvelope<TType extends string, TPayload>(
  requestId: string,
  type: TType,
  payload: TPayload
): ProtocolEnvelope<TType, TPayload> {
  return {
    protocolVersion: PROTOCOL_VERSION,
    requestId,
    type,
    payload
  };
}

export async function blobToBase64Chunks(
  blob: Blob,
  onChunk: (chunk: { index: number; total: number; byteLength: number; base64: string }) => void
) {
  const total = Math.max(1, Math.ceil(blob.size / EXPORT_CHUNK_BYTES));
  for (let index = 0; index < total; index += 1) {
    const start = index * EXPORT_CHUNK_BYTES;
    const bytes = new Uint8Array(await blob.slice(start, Math.min(blob.size, start + EXPORT_CHUNK_BYTES)).arrayBuffer());
    const segmentSize = 32_768;
    const segments: string[] = [];
    for (let offset = 0; offset < bytes.length; offset += segmentSize) {
      const segment = bytes.subarray(offset, Math.min(offset + segmentSize, bytes.length));
      let binary = "";
      for (const value of segment) binary += String.fromCharCode(value);
      segments.push(binary);
    }
    onChunk({ index, total, byteLength: bytes.length, base64: btoa(segments.join("")) });
  }
  return total;
}

export const EXPORT_CHUNK_BYTES = 384 * 1024;

export function readObjectPayload(value: unknown): Record<string, unknown> {
  return isRecord(value) ? value : {};
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isRequestId(value: unknown): value is string {
  return typeof value === "string" && /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/.test(value);
}
