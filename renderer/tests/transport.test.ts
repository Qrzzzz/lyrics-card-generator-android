import { describe, expect, it } from "vitest";
import {
  blobToBase64Chunks,
  createEnvelope,
  EXPORT_CHUNK_BYTES,
  parseHostEnvelope,
  ProtocolMessageError
} from "../src/transport";

describe("renderer protocol", () => {
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

  it("streams export bytes as bounded independently decodable chunks", async () => {
    const source = new Uint8Array(EXPORT_CHUNK_BYTES * 2 + 17).map((_, index) => index % 251);
    const chunks: Array<{ index: number; total: number; byteLength: number; base64: string }> = [];
    const count = await blobToBase64Chunks(new Blob([source]), (chunk) => chunks.push(chunk));

    expect(count).toBe(3);
    expect(chunks.map((chunk) => chunk.index)).toEqual([0, 1, 2]);
    expect(chunks.every((chunk) => chunk.total === 3 && chunk.byteLength <= EXPORT_CHUNK_BYTES)).toBe(true);
    const restored = Buffer.concat(chunks.map((chunk) => Buffer.from(chunk.base64, "base64")));
    expect(restored.equals(Buffer.from(source))).toBe(true);
  });
});
