import { describe, expect, it } from "vitest";
import { readFileSync, readdirSync } from "node:fs";
import { resolve } from "node:path";
import ts from "typescript";
import { formatReviewInterval, LOCALE_KEY, messages, readLocale, translateText } from "./i18n";

describe("UI localization", () => {
  it("restores only valid locale values and tolerates blocked storage", () => {
    expect(readLocale({ getItem: key => key === LOCALE_KEY ? "en" : null })).toBe("en");
    expect(readLocale({ getItem: () => "unknown" })).toBe("vi");
    expect(readLocale({ getItem: () => { throw new Error("blocked"); } })).toBe("vi");
  });
  it("translates controls without altering unknown learning content", () => {
    expect(translateText("Chọn chủ đề", "en")).toBe("Choose a topic");
    expect(translateText("Again", "vi")).toBe("Học lại");
    expect(translateText("Java Core", "en")).toBe("Java Core");
    expect(translateText("Câu hỏi từ API chưa được dịch", "en")).toBe("Câu hỏi từ API chưa được dịch");
  });
  it("preserves inline spaces, nullable feedback and interpolation", () => {
    expect(translateText("  Đăng nhập ", "en")).toBe("  Sign in ");
    expect(translateText(null, "en")).toBe("");
    expect(translateText("Chuyển công cụ tìm kiếm sang {mode}?", "en", { mode: "AUTO" })).toBe("Switch the search engine to AUTO?");
    expect(translateText("câu ·", "en")).toBe("questions ·");
  });
  it("gives known browser transport failures useful localized UI copy", () => {
    expect(translateText("Failed to fetch", "vi")).toBe("Không kết nối được máy chủ. Thử lại.");
    expect(translateText("Load failed", "en")).toBe("Could not connect to the server. Try again.");
    expect(translateText("NetworkError when attempting to fetch resource.", "vi")).toBe("Không kết nối được máy chủ. Thử lại.");
  });
  it("formats review durations in both languages with singular English units", () => {
    expect(formatReviewInterval(1, "en")).toBe("1 day");
    expect(formatReviewInterval(4, "en")).toBe("4 days");
    expect(formatReviewInterval(30, "en")).toBe("1 month");
    expect(formatReviewInterval(60, "vi")).toBe("2 tháng");
    expect(formatReviewInterval(365, "en")).toBe("1 year");
  });
  it("has an English entry for every Vietnamese literal passed to the UI translator", () => {
    const missing: string[] = [];
    const walk = (directory: string) => {
      for (const entry of readdirSync(directory, { withFileTypes: true })) {
        const path = resolve(directory, entry.name);
        if (entry.isDirectory()) walk(path);
        else if (path.endsWith(".tsx")) {
          const file = ts.createSourceFile(path, readFileSync(path, "utf8"), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
          const inspect = (node: ts.Node) => {
            if (ts.isCallExpression(node) && ts.isIdentifier(node.expression) && node.expression.text === "t" && node.arguments[0] && ts.isStringLiteral(node.arguments[0])) {
              const source = node.arguments[0].text;
              const key = source.trim();
              if (/[À-ỹđĐ]/.test(key) && !messages[key] && !messages[source]) missing.push(key);
            }
            ts.forEachChild(node, inspect);
          };
          inspect(file);
        }
      }
    };
    walk(resolve(__dirname, "../app"));
    walk(resolve(__dirname, "../components"));
    expect(missing).toEqual([]);
  });
});
