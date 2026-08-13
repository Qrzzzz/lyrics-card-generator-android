import { readFileSync } from "node:fs";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  blobToBase64Chunks,
  createEnvelope,
  EXPORT_CHUNK_BYTES,
  isTrustedWindowMessageOrigin,
  parseHostEnvelope,
  ProtocolMessageError
} from "../src/transport";

describe("renderer protocol", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("parses object and JSON messages", () => {
    const envelope = createEnvelope("request-1", "ping", {});
    expect(parseHostEnvelope(envelope)).toEqual(envelope);
    expect(parseHostEnvelope(JSON.stringify(envelope))).toEqual(envelope);
  });

  it("preserves request IDs while rejecting unsupported versions", () => {
    try {
      parseHostEnvelope({ protocolVersion: 2, requestId: "request-2", type: "ping", payload: {} });
      throw new Error("expected parseHostEnvelope to throw");
    } catch (error) {
      expect(error).toBeInstanceOf(ProtocolMessageError);
      expect((error as ProtocolMessageError).requestId).toBe("request-2");
      expect((error as ProtocolMessageError).code).toBe("UNSUPPORTED_PROTOCOL");
    }
  });

  it("rejects unsafe request identifiers", () => {
    expect(() =>
      parseHostEnvelope({ protocolVersion: 1, requestId: "../request", type: "ping", payload: {} })
    ).toThrow(/requestId/);
  });

  it("accepts window messages only from the document's exact origin", () => {
    const appassetsOrigin = "https://appassets.androidplatform.net";
    expect(isTrustedWindowMessageOrigin(appassetsOrigin, appassetsOrigin)).toBe(true);
    expect(isTrustedWindowMessageOrigin("https://attacker.invalid", appassetsOrigin)).toBe(false);
    expect(isTrustedWindowMessageOrigin("null", appassetsOrigin)).toBe(false);
    expect(isTrustedWindowMessageOrigin("", appassetsOrigin)).toBe(false);

    // Keep direct-file developer harnesses working without making a trusted HTTPS
    // document accept opaque-origin messages.
    expect(isTrustedWindowMessageOrigin("null", "null")).toBe(true);
  });

  it("streams export bytes as bounded independently decodable chunks", async () => {
    const source = new Uint8Array(EXPORT_CHUNK_BYTES * 2 + 17).map((_, index) => index % 251);
    const chunks: Array<{ index: number; total: number; byteLength: number; base64: string }> = [];
    const blob = new Blob([source]);
    const slice = vi.spyOn(blob, "slice");
    const count = await blobToBase64Chunks(blob, (chunk) => chunks.push(chunk));

    expect(count).toBe(3);
    expect(slice).not.toHaveBeenCalled();
    expect(chunks.map((chunk) => chunk.index)).toEqual([0, 1, 2]);
    expect(chunks.every((chunk) => chunk.total === 3 && chunk.byteLength <= EXPORT_CHUNK_BYTES)).toBe(true);
    const restored = Buffer.concat(chunks.map((chunk) => Buffer.from(chunk.base64, "base64")));
    expect(restored.equals(Buffer.from(source))).toBe(true);
  });

  it("streams chunks through one FileReader read when legacy WebView lacks Blob.arrayBuffer", async () => {
    const source = new Uint8Array([0, 1, 2, 127, 128, 254, 255]);
    const legacyBlob = {
      size: source.byteLength,
      legacyBytes: source,
      slice: vi.fn(() => {
        throw new Error("legacy chunking must not create Blob children");
      })
    } as unknown as Blob;
    class LegacyFileReader {
      error: Error | null = null;
      result: ArrayBuffer | null = null;
      onerror: (() => void) | null = null;
      onload: (() => void) | null = null;

      readAsArrayBuffer(value: Blob) {
        const bytes = (value as unknown as { legacyBytes: Uint8Array }).legacyBytes;
        this.result = bytes.slice().buffer;
        this.onload?.();
      }
    }
    vi.stubGlobal("FileReader", LegacyFileReader);
    const chunks: Array<{ index: number; total: number; byteLength: number; base64: string }> = [];

    expect(await blobToBase64Chunks(legacyBlob, (chunk) => chunks.push(chunk))).toBe(1);
    expect(legacyBlob.slice).not.toHaveBeenCalled();
    expect(Buffer.from(chunks[0].base64, "base64").equals(Buffer.from(source))).toBe(true);
  });

  it("yields between chunks so cancellation can stop later delivery", async () => {
    const source = new Uint8Array(EXPORT_CHUNK_BYTES + 1).fill(0x2a);
    let cancelled = false;
    const delivered: number[] = [];

    await expect(blobToBase64Chunks(new Blob([source]), (chunk) => {
      if (cancelled) throw new Error("cancelled before next chunk");
      delivered.push(chunk.index);
      if (chunk.index === 0) setTimeout(() => { cancelled = true; }, 0);
    })).rejects.toThrow("cancelled before next chunk");
    expect(delivered).toEqual([0]);
  });

  it("keeps the chunk-yield scheduler compatible with WebView 69", () => {
    const source = readFileSync(new URL("../src/transport.ts", import.meta.url), "utf8");

    expect(source).not.toContain("globalThis");
    expect(source).toContain("setTimeout(resolve, 0)");
  });
});
