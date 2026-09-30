import DOMPurify from "isomorphic-dompurify";

/**
 * Defense-in-depth for `answerHtml` rendered via `dangerouslySetInnerHTML`.
 * Server already runs Jsoup Safelist; this strips anything that slipped through
 * (legacy rows, bug in sanitizer, admin bypass). Keep tags/classes that FE styles.
 */
const ALLOWED_TAGS = [
  "p", "br", "hr", "strong", "b", "em", "i", "u", "s", "code", "pre",
  "ul", "ol", "li", "blockquote", "small", "sup", "sub",
  "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption",
  "h1", "h2", "h3", "h4", "h5", "h6",
  "dl", "dt", "dd", "span", "div", "figure", "figcaption", "a",
];

const ALLOWED_ATTR = ["class", "href", "title", "colspan", "rowspan"];

export function sanitizeAnswerHtml(html: string): string {
  if (!html) return "";
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    ALLOWED_URI_REGEXP: /^(?:(?:https?):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i,
  });
}
