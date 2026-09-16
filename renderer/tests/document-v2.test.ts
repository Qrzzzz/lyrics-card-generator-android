import { describe, it, expect } from 'vitest';
import { createLyricDocumentV2, reconcileLyricDocumentV2, serializeLyricDocument, getLyricDocumentRows } from '../src/desktop/lyrics-document-v2';
import { DEFAULT_RENDER_SPEC } from '../src/defaultSpec';
import { parseRenderSpec } from '../src/spec';

describe('V2 document boundary', () => {
  it('preserves independent blank lines and pairs paragraph units', () => {
    const source = '\n甲\n乙\n\n\n丙\n';
    const translation = 'A\nB\n\nC\n';
    const document = createLyricDocumentV2(source, translation);
    expect(serializeLyricDocument(document)).toEqual({ source, translation });
    expect(getLyricDocumentRows(document).map(row => [row.source, row.translation])).toEqual([[['甲'], ['A']], [['乙'], ['B']], [['丙'], ['C']]]);
  });
  it('preserves identities across inserted lines and does not consume a lyric for a separator', () => {
    const before = createLyricDocumentV2('甲\n乙', 'A\nB');
    const after = reconcileLyricDocumentV2(before, '新\n甲\n乙', 'N\nA\nB');
    expect(after.id).toBe(before.id);
    expect(after.blocks[0].units[1].id).toBe(before.blocks[0].units[0].id);
    const rows = getLyricDocumentRows(createLyricDocumentV2('甲\n<separator />\n乙', 'A\nB'));
    expect(rows[1].isSeparator).toBe(true);
    expect(rows[2].translation).toEqual(['B']);
  });
  it('rejects old schemas, duplicate IDs and divergent text projections', () => {
    expect(() => parseRenderSpec({ ...DEFAULT_RENDER_SPEC, schemaVersion: 1 })).toThrow();
    const spec = structuredClone(DEFAULT_RENDER_SPEC);
    spec.content.lyricDocument.blocks[0].id = spec.content.lyricDocument.id;
    expect(() => parseRenderSpec(spec)).toThrow();
    expect(() => parseRenderSpec({ ...DEFAULT_RENDER_SPEC, content: { ...DEFAULT_RENDER_SPEC.content, lyrics: 'changed' } })).toThrow();
  });
});
