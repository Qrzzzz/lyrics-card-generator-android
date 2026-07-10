import Ajv2020, { type ErrorObject } from "ajv/dist/2020.js";
import renderSpecSchema from "../schema/render-spec-v1.schema.json";
import { normalizeHex } from "./color";
import type { RenderSpec } from "./types";

const ajv = new Ajv2020({ allErrors: true, strict: true, strictTypes: false });
const validateV1 = ajv.compile<RenderSpec>(renderSpecSchema);

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
