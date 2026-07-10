import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const html = readFileSync(resolve(import.meta.dirname, "../index.html"), "utf8");

describe("offline renderer document", () => {
  it("ships a restrictive content security policy", () => {
    expect(html).toContain("default-src 'none'");
    expect(html).toContain("connect-src 'self'");
    expect(html).toContain("object-src 'none'");
    expect(html).toContain("base-uri 'none'");
  });

  it("does not load scripts, styles, or fonts from a public origin", () => {
    expect(html).not.toMatch(/(?:src|href)=["']https?:\/\//i);
  });
});
