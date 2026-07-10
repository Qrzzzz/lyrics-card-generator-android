import { describe, expect, it } from "vitest";
import { estimateExportBytes } from "../src/export";

describe("export sizing", () => {
  it("accounts for the squared PNG pixel ratio", () => {
    expect(estimateExportBytes(1080, 1350, 1)).toBe(5_832_000);
    expect(estimateExportBytes(1080, 1350, 2)).toBe(23_328_000);
  });
});
