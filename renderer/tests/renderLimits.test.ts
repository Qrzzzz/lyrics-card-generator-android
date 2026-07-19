import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { LyricsCard, splitUsefulLines } from "../src/Card";
import { DEFAULT_RENDER_SPEC } from "../src/defaultSpec";
import {
  countPhysicalLines,
  LyricLineLimitError,
  MAX_LYRIC_LINES
} from "../src/renderLimits";
import { InvalidRenderSpecError, parseRenderSpec } from "../src/spec";

function withLyrics(lyrics: string, translation = "", translationEnabled = false) {
  return {
    ...DEFAULT_RENDER_SPEC,
    content: {
      ...DEFAULT_RENDER_SPEC.content,
      lyrics,
      translation,
      translationEnabled
    }
  };
}

function lines(count: number, prefix: string, separator = "\n") {
  return Array.from({ length: count }, (_, index) => `${prefix}-${index}`).join(separator);
}

function expectLineLimitRejected(spec: ReturnType<typeof withLyrics>) {
  try {
    parseRenderSpec(spec);
    throw new Error("Expected RenderSpec to be rejected");
  } catch (error) {
    expect(error).toBeInstanceOf(InvalidRenderSpecError);
    expect((error as InvalidRenderSpecError).validationErrors).toEqual(
      expect.arrayContaining([expect.objectContaining({ keyword: "maxLines" })])
    );
  }
}

describe("lyric physical line limit", () => {
  it("accepts within and exactly at 400 lines and rejects line 401", () => {
    expect(() => parseRenderSpec(withLyrics(lines(MAX_LYRIC_LINES - 1, "within")))).not.toThrow();
    expect(() => parseRenderSpec(withLyrics(lines(MAX_LYRIC_LINES, "exact", "\r\n")))).not.toThrow();
    expectLineLimitRejected(withLyrics(lines(MAX_LYRIC_LINES + 1, "over")));
  });

  it("counts CRLF, lone CR or LF, and a trailing separator consistently", () => {
    expect(countPhysicalLines("")).toBe(1);
    expect(countPhysicalLines("a\r\nb\rc\n")).toBe(4);
    expect(splitUsefulLines("a\rb\r\nc\n")).toEqual(["a", "b", "c"]);

    const exactWithTrailingNewline = "line\n".repeat(MAX_LYRIC_LINES - 1);
    expect(countPhysicalLines(exactWithTrailingNewline)).toBe(MAX_LYRIC_LINES);
    expect(() => parseRenderSpec(withLyrics(exactWithTrailingNewline))).not.toThrow();
    expectLineLimitRejected(withLyrics(`${exactWithTrailingNewline}\n`));
  });

  it("rejects the 200,000-newline payload before card construction", () => {
    const newlineBomb = "\n".repeat(200_000);
    const spec = withLyrics(newlineBomb);

    expect(countPhysicalLines(newlineBomb)).toBe(200_001);
    expectLineLimitRejected(spec);
    expect(() => renderToStaticMarkup(createElement(LyricsCard, { spec }))).toThrow(LyricLineLimitError);
  });

  it("does not reject one maximum-length lyric line", () => {
    expect(() => parseRenderSpec(withLyrics("a".repeat(200_000)))).not.toThrow();
  });

  it("bounds the deterministic lyric row and element count at the limit", () => {
    const spec = withLyrics(
      lines(MAX_LYRIC_LINES, "lyric"),
      lines(MAX_LYRIC_LINES, "translation"),
      true
    );
    const markup = renderToStaticMarkup(createElement(LyricsCard, { spec }));
    const rowCount = markup.split('class="lyric-pair"').length - 1;
    const lyricNodeCount = markup.split('class="lyric-line"').length - 1;
    const translationNodeCount = markup.split('class="translation-line"').length - 1;
    const elementCount = markup.match(/<[a-z][a-z0-9-]*(?:\s|>)/g)?.length ?? 0;

    expect(rowCount).toBe(MAX_LYRIC_LINES);
    expect(lyricNodeCount).toBe(MAX_LYRIC_LINES);
    expect(translationNodeCount).toBe(MAX_LYRIC_LINES);
    expect(elementCount).toBeLessThan(1_400);
  });
});
