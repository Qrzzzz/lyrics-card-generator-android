import type { ErrorObject, ValidateFunction } from "ajv";
import { normalizeHex } from "./color";
import generatedValidateV1 from "./generated/renderSpecValidator";
import { assertRenderSpecLineLimits, LyricLineLimitError } from "./renderLimits";
import type { RenderSpec } from "./types";
import { isLyricDocumentV2, serializeLyricDocument } from "./desktop/lyrics-document-v2";

const validateV1 = generatedValidateV1 as ValidateFunction<RenderSpec>;

export class InvalidRenderSpecError extends Error {
  readonly validationErrors: readonly ErrorObject[];

  constructor(errors: readonly ErrorObject[]) {
    super(formatValidationErrors(errors));
    this.name = "InvalidRenderSpecError";
    this.validationErrors = errors;
  }
}

export function parseRenderSpec(input: unknown): RenderSpec {
  const candidate = typeof input === "string" ? parseJson(input) : input;
  if (!validateV1(candidate)) {
    throw new InvalidRenderSpecError(validateV1.errors ?? []);
  }
  const document = candidate.content.lyricDocument;
  const text = isLyricDocumentV2(document) ? serializeLyricDocument(document) : null;
  if (!text || text.source !== candidate.content.lyrics.replace(/\r\n?/g, "\n") || text.translation !== candidate.content.translation.replace(/\r\n?/g, "\n")) {
    throw new InvalidRenderSpecError([{ instancePath: "/content/lyricDocument", schemaPath: "", keyword: "document", params: {}, message: "must be a valid V2 document matching text projections" }]);
  }
  try {
    assertRenderSpecLineLimits(candidate);
  } catch (error) {
    if (error instanceof LyricLineLimitError) {
      const field = error.path.substring("content.".length);
      throw new InvalidRenderSpecError([
        {
          instancePath: `/content/${field}`,
          schemaPath: `#/properties/content/properties/${field}/maxLines`,
          keyword: "maxLines",
          params: { limit: error.limit, actual: error.actual },
          message: `must NOT have more than ${error.limit} physical lines`
        }
      ]);
    }
    throw error;
  }

  return normalizeRenderSpec(candidate);
}

export function normalizeRenderSpec(spec: RenderSpec): RenderSpec {
  return {
    ...spec,
    song: { ...spec.song },
    content: { ...spec.content },
    canvas: { ...spec.canvas },
    typography: {
      ...spec.typography,
      customTextColor: spec.typography.customTextColor
        ? normalizeHex(spec.typography.customTextColor, "#F8F4EA")
        : null
    },
    visual: {
      ...spec.visual,
      palette: {
        ...spec.visual.palette,
        dominant: normalizeHex(spec.visual.palette.dominant),
        secondary: normalizeHex(spec.visual.palette.secondary),
        accent: normalizeHex(spec.visual.palette.accent)
      }
    },
    visibility: { ...spec.visibility },
    branding: { ...spec.branding },
    media: { ...spec.media }
  };
}

export function resolveCoverAssetUrl(assetId: string | null) {
  if (!assetId || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(assetId)) {
    return null;
  }
  return `../media/${encodeURIComponent(assetId)}`;
}

export function formatValidationErrors(errors: readonly ErrorObject[]) {
  if (errors.length === 0) {
    return "RenderSpec is invalid";
  }
  return errors
    .slice(0, 12)
    .map((error) => `${error.instancePath || "/"} ${error.message ?? "is invalid"}`)
    .join("; ");
}

function parseJson(value: string) {
  try {
    return JSON.parse(value) as unknown;
  } catch {
    throw new InvalidRenderSpecError([
      {
        instancePath: "",
        schemaPath: "#",
        keyword: "parse",
        params: {},
        message: "must be valid JSON"
      }
    ]);
  }
}
