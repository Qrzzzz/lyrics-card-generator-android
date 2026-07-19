import renderSpecSchema from "../schema/render-spec-v1.schema.json";
import type { RenderSpec } from "./types";

export type LyricTextPath = "content.lyrics" | "content.translation";

const contentSchema = renderSpecSchema.properties.content.properties;
const lyricLineLimit = contentSchema.lyrics.maxLines;
const translationLineLimit = contentSchema.translation.maxLines;

if (
  !Number.isSafeInteger(lyricLineLimit) ||
  lyricLineLimit < 1 ||
  lyricLineLimit !== translationLineLimit
) {
  throw new Error("Renderer lyric line limits are invalid or inconsistent");
}

/** The source JSON Schema is the Renderer-side source of truth for this value. */
export const MAX_LYRIC_LINES = lyricLineLimit;

export class LyricLineLimitError extends RangeError {
  readonly path: LyricTextPath;
  readonly limit: number;
  readonly actual: number;

  constructor(path: LyricTextPath, actual: number) {
    super(`${path} must contain at most ${MAX_LYRIC_LINES} physical lines (received ${actual})`);
    this.name = "LyricLineLimitError";
    this.path = path;
    this.limit = MAX_LYRIC_LINES;
    this.actual = actual;
  }
}

export function countPhysicalLines(value: string) {
  let lines = 1;
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    if (code === 13) {
      lines += 1;
      if (value.charCodeAt(index + 1) === 10) index += 1;
    } else if (code === 10) {
      lines += 1;
    }
  }
  return lines;
}

export function assertLyricLineLimit(value: string, path: LyricTextPath) {
  const actual = countPhysicalLines(value);
  if (actual > MAX_LYRIC_LINES) {
    throw new LyricLineLimitError(path, actual);
  }
}

export function assertRenderSpecLineLimits(spec: Pick<RenderSpec, "content">) {
  assertLyricLineLimit(spec.content.lyrics, "content.lyrics");
  assertLyricLineLimit(spec.content.translation, "content.translation");
}
