import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

const rendererRoot = import.meta.dirname;
const outputDirectory = process.env.RENDERER_OUT_DIR
  ? resolve(process.env.RENDERER_OUT_DIR)
  : resolve(rendererRoot, "dist");
const normalizeNewlines = (value: string) => value.replace(/\r\n/g, "\n");

export default defineConfig({
  base: "./",
  plugins: [
    {
      name: "normalize-renderer-html",
      transformIndexHtml(html) {
        return normalizeNewlines(html);
      }
    },
    react(),
    {
      name: "emit-renderer-contract",
      generateBundle() {
        const schema = normalizeNewlines(
          readFileSync(resolve(rendererRoot, "schema/render-spec-v1.schema.json"), "utf8")
        );
        const manifest = JSON.parse(
          readFileSync(resolve(rendererRoot, "renderer-manifest.json"), "utf8")
        ) as Record<string, unknown> & {
          fontLicense: string;
          fonts: Array<{ file: string }>;
        };

        this.emitFile({
          type: "asset",
          fileName: "renderer-schema.json",
          source: `${schema.trim()}\n`
        });
        this.emitFile({
          type: "asset",
          fileName: "renderer-manifest.json",
          source: `${JSON.stringify(manifest, null, 2)}\n`
        });
        this.emitFile({
          type: "asset",
          fileName: manifest.fontLicense,
          source: normalizeNewlines(
            readFileSync(resolve(rendererRoot, "public", manifest.fontLicense), "utf8")
          )
        });
        for (const font of manifest.fonts) {
          this.emitFile({
            type: "asset",
            fileName: font.file,
            source: readFileSync(resolve(rendererRoot, "public", font.file))
          });
        }
      }
    }
  ],
  publicDir: false,
  build: {
    outDir: outputDirectory,
    emptyOutDir: true,
    assetsDir: "assets",
    sourcemap: false,
    target: "chrome105"
  },
  test: {
    environment: "node",
    include: ["tests/**/*.test.ts"],
    testTimeout: 10_000
  }
});
