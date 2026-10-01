import { sanitizeAnswerHtml } from "./sanitize-html";

/** Convert Markdown to sanitized HTML for notes preview (DOMPurify after parse). */
export async function renderSafeMarkdown(markdown: string): Promise<string> {
  if (!markdown?.trim()) return "";
  const { marked } = await import("marked");
  marked.setOptions({ gfm: true, breaks: true });
  const html = await marked.parse(markdown);
  return sanitizeAnswerHtml(typeof html === "string" ? html : String(html));
}
