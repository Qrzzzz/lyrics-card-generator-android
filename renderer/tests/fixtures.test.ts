import { readdirSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { parseRenderSpec } from "../src/spec";

const rendererRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const fixtureDirectory = resolve(rendererRoot, "fixtures");

describe("RenderSpec fixtures", () => {
  const fixtureNames = readdirSync(fixtureDirectory).filter((name) => name.endsWith(".json"));

  it("ships multiple layout and content fixtures", () => {
    expect(fixtureNames.length).toBeGreaterThanOrEqual(5);
  });

  it.each(fixtureNames)("validates %s against RenderSpec v1", (fixtureName) => {
    const raw = JSON.parse(readFileSync(resolve(fixtureDirectory, fixtureName), "utf8")) as unknown;
    const spec = parseRenderSpec(raw);
    expect(spec.schemaVersion).toBe(1);
    expect(spec.rendererVersion).toBe("android-alpha-renderer-1");
  });
});
