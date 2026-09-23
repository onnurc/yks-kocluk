import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const indexStyles = readFileSync(resolve(process.cwd(), "src/index.css"), "utf8");
const appStyles = readFileSync(resolve(process.cwd(), "src/components/app-layout.css"), "utf8");

const cssHexVariable = (styles: string, name: string): string => {
  const value = styles.match(new RegExp(`${name}:\\s*(#[0-9a-fA-F]{6})`))?.[1];
  if (!value) throw new Error(`Missing CSS variable: ${name}`);
  return value;
};

const relativeLuminance = (hex: string): number => {
  const channels = hex.match(/[0-9a-fA-F]{2}/g)?.map((channel) => parseInt(channel, 16) / 255);
  if (!channels || channels.length !== 3) throw new Error(`Invalid color: ${hex}`);
  const [red, green, blue] = channels.map((channel) => (
    channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
  ));
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
};

const contrastRatio = (foreground: string, background: string): number => {
  const lighter = Math.max(relativeLuminance(foreground), relativeLuminance(background));
  const darker = Math.min(relativeLuminance(foreground), relativeLuminance(background));
  return (lighter + 0.05) / (darker + 0.05);
};

describe("global light-theme consistency", () => {
  it("does not apply an incomplete OS-driven dark palette", () => {
    expect(indexStyles).toContain("color-scheme: light;");
    expect(indexStyles).not.toContain("prefers-color-scheme: dark");
  });

  it("keeps inherited headings and body text readable on authenticated light surfaces", () => {
    const heading = cssHexVariable(indexStyles, "--text-h");
    const body = cssHexVariable(indexStyles, "--text");
    const appPaper = cssHexVariable(appStyles, "--app-paper");

    expect(contrastRatio(heading, appPaper)).toBeGreaterThanOrEqual(3);
    expect(contrastRatio(body, appPaper)).toBeGreaterThanOrEqual(4.5);
  });
});
