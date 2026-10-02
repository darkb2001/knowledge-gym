import { describe, expect, it } from "vitest";
import config from "../tailwind.config";

function luminance(hex: string) {
  const rgb = [1, 3, 5].map(index => parseInt(hex.slice(index, index + 2), 16) / 255).map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
  return rgb.reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0);
}
function contrast(first: string, second: string) {
  const values = [luminance(first), luminance(second)].sort((a, b) => a - b);
  return (values[1] + 0.05) / (values[0] + 0.05);
}
describe("Study Commons contrast", () => {
  const colors = config.theme!.extend!.colors as Record<string, string | Record<string, string>>;
  it("keeps readable text and placeholders on all main light surfaces", () => {
    for (const text of ["strong", "body", "subtle"]) {
      for (const background of ["canvas", "surface", "sand", "muted"]) {
        expect(contrast(colors[text] as string, colors[background] as string), text + " on " + background).toBeGreaterThanOrEqual(4.5);
      }
    }
  });
  it("makes field boundaries distinguishable from light backgrounds", () => {
    for (const background of ["canvas", "surface", "muted"]) expect(contrast(colors.control as string, colors[background] as string)).toBeGreaterThanOrEqual(3);
  });
  it("keeps primary and selected-state controls readable", () => {
    const accent = colors.accent as Record<string, string>;
    expect(contrast(colors["on-accent"] as string, accent.DEFAULT)).toBeGreaterThanOrEqual(4.5);
    expect(contrast(colors["on-accent"] as string, accent.hover)).toBeGreaterThanOrEqual(4.5);
    expect(contrast(colors.positive as string, colors.sage as string)).toBeGreaterThanOrEqual(4.5);
  });
});
