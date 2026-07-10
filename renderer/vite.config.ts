import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

const rendererRoot = import.meta.dirname;

export default defineConfig({
  base: "./",
  plugins: [
    react(),
    {
      name: "emit-renderer-contract",
      generateBundle() {
        const schema = readFileSync(resolve(rendererRoot, "schema/render-spec-v1.schema.json"), "utf8");
        const manifest = JSON.parse(
          readFileSync(resolve(rendererRoot, "renderer-manifest.json"), "utf8")
        ) as Record<string, unknown>;

        this.emitFile({
          type: "asset",
          fileName: "renderer-schema.json",
          source: `${schema.trim()}\n`
        });
        this.emitFile({
          type: "asset",
          fileName: "renderer-manifest.json",
          source: `${JSON.stringify({ ...manifest, buildTime: new Date().toISOString() }, null, 2)}\n`
        });
      }
    }
  ],
  publicDir: "public",
  build: {
    outDir: "../app/src/main/assets/renderer",
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
