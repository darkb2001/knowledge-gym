"use client";
import { useLocale } from "@/components/locale";

import { useCallback, useEffect, useState } from "react";
import { apiRequest, ensureAccessToken, getAccessToken, getApiBase } from "@/lib/api-client";
import { renderSafeMarkdown } from "@/lib/markdown";
import { RequireAuth, PageHeading } from "@/components/ui";

type Note = {
  id: string;
  questionId: string | null;
  moduleId: string | null;
  noteType: string;
  content: string | null;
  tags: string[];
};
type Hit = { type: string; id: string; title: string; excerpt: string };
type Draft = { id?: string; noteType: string; content: string; questionId: string; tags: string };

const EMPTY: Draft = { noteType: "QUICK", content: "", questionId: "", tags: "" };

function Notes() {
  const { t } = useLocale();
  const [notes, setNotes] = useState<Note[]>([]);
  const [bookmarks, setBookmarks] = useState<Note[]>([]);
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [previewHtml, setPreviewHtml] = useState("");
  const [query, setQuery] = useState("");
  const [hits, setHits] = useState<Hit[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [all, marks] = await Promise.all([
        apiRequest<Note[]>("/notes"),
        apiRequest<Note[]>("/users/me/bookmarks"),
      ]);
      setNotes(all);
      setBookmarks(marks);
      setError("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Không tải được ghi chú");
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    let cancelled = false;
    const timer = setTimeout(() => {
      void renderSafeMarkdown(draft.content).then((html) => {
        if (!cancelled) setPreviewHtml(html);
      });
    }, 180);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [draft.content]);

  async function save() {
    if (!draft.content.trim()) return;
    setBusy(true);
    try {
      const body = {
        noteType: draft.noteType,
        content: draft.content,
        tags: draft.tags.split(",").map((t) => t.trim()).filter(Boolean),
        questionId: draft.questionId.trim() || null,
      };
      if (draft.id) {
        await apiRequest(`/notes/${draft.id}`, { method: "PUT", body });
      } else {
        await apiRequest("/notes", { method: "POST", body });
      }
      setDraft(EMPTY);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Không lưu được ghi chú");
    } finally {
      setBusy(false);
    }
  }

  async function remove(id: string) {
    if (!window.confirm(t("Xóa ghi chú này?"))) return;
    try {
      await apiRequest(`/notes/${id}`, { method: "DELETE" });
      if (draft.id === id) setDraft(EMPTY);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Không xóa được ghi chú");
    }
  }

  async function convert(id: string) {
    try {
      const res = await apiRequest<{ cardId: string }>(`/notes/${id}/convert`, { method: "POST" });
      setError("");
      window.alert(t("Đã tạo flashcard {cardId}", { cardId: res.cardId }));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Không chuyển thành flashcard được");
    }
  }

  async function search(value: string) {
    setQuery(value);
    if (!value.trim()) { setHits([]); return; }
    try {
      setHits(await apiRequest<Hit[]>(`/search?q=${encodeURIComponent(value)}`));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Tìm kiếm thất bại");
    }
  }

  async function exportMarkdown() {
    if (!(await ensureAccessToken())) return;
    const response = await fetch(`${getApiBase()}/notes/export?format=md`, {
      headers: { Authorization: `Bearer ${getAccessToken()}` },
      credentials: "include",
    });
    if (!response.ok) { setError("Không xuất được ghi chú"); return; }
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = url;
    link.download = "notes.md";
    link.click();
    URL.revokeObjectURL(url);
  }

  function edit(note: Note) {
    setDraft({
      id: note.id,
      noteType: note.noteType,
      content: note.content ?? "",
      questionId: note.questionId ?? "",
      tags: (note.tags ?? []).join(", "),
    });
    window.scrollTo({ top: 0, behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
  }

  return (
    <main className="mx-auto max-w-4xl space-y-7 p-6">
      <PageHeading title="Ghi chú" description="Lưu lại những điều bạn muốn nhớ." action={<button type="button" onClick={() => void exportMarkdown()} className="kg-secondary">{t("Xuất Markdown")}</button>} />

      <label className="block space-y-1 text-sm text-body">
        {t("Tìm câu hỏi và ghi chú")}<input
          className="w-full rounded-sm border border-line bg-surface px-3 py-2 text-strong"
          value={query}
          onChange={(e) => void search(e.target.value)}
          placeholder={t("Nhập từ khóa…")}
        />
      </label>
      {hits.length > 0 && (
        <section aria-label={t("Kết quả tìm kiếm")} className="space-y-2 rounded-sm border border-line p-4">
          <h2 className="font-display text-lg text-strong">{t("Kết quả")}</h2>
          <ul className="space-y-2">
            {hits.map((h) => (
              <li key={`${h.type}-${h.id}`} className="border-b border-line pb-2">
                <span className="mr-2 text-[10px] uppercase tracking-wider text-positive">{h.type}</span>
                <span className="text-strong">{h.title}</span>
                <p className="text-sm text-subtle">{h.excerpt}</p>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="kg-panel space-y-4">
        <h2 className="font-display text-xl text-strong">{draft.id ? t("Sửa ghi chú") : t("Ghi chú mới")}</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="text-sm text-body">{t("Loại")}<select
              className="mt-1 w-full rounded-sm border border-line bg-surface px-3 py-2"
              value={draft.noteType}
              onChange={(e) => setDraft((d) => ({ ...d, noteType: e.target.value }))}
            >
              <option value="QUICK">{t("QUICK")}</option>
              <option value="STUDY">{t("STUDY")}</option>
              <option value="HIGHLIGHT">{t("HIGHLIGHT")}</option>
              <option value="BOOKMARK">{t("BOOKMARK")}</option>
            </select>
          </label>
          <label className="text-sm text-body">{t("Question ID (tùy chọn)")}<input
              className="mt-1 w-full rounded-sm border border-line bg-surface px-3 py-2 font-mono text-sm"
              value={draft.questionId}
              onChange={(e) => setDraft((d) => ({ ...d, questionId: e.target.value }))}
              placeholder={t("UUID câu hỏi để convert SRS")}
            />
          </label>
        </div>
        <label className="block text-sm text-body">{t("Tags (phẩy tách)")}<input
            className="mt-1 w-full rounded-sm border border-line bg-surface px-3 py-2"
            value={draft.tags}
            onChange={(e) => setDraft((d) => ({ ...d, tags: e.target.value }))}
            placeholder={t("java, jvm")}
          />
        </label>
        <textarea
          className="min-h-40 w-full rounded-sm border border-line bg-surface p-3 font-mono text-sm text-strong"
          value={draft.content}
          onChange={(e) => setDraft((d) => ({ ...d, content: e.target.value }))}
          placeholder={t("Viết ghi chú Markdown…")}
          aria-label={t("Nội dung ghi chú")}
        />
        <div>
          {!previewHtml && <p className="mb-3 text-sm text-subtle">{t("Chưa có nội dung")}</p>}
          <p className="mb-2 text-sm font-medium text-subtle">{t("Xem trước an toàn")}</p>
          <div
            className="answer-html min-h-24 rounded-sm border border-line bg-surface/80 p-3"
            dangerouslySetInnerHTML={{ __html: previewHtml }}
          />
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled={busy}
            onClick={() => void save()}
            className="kg-button"
          >
            {draft.id ? t("Cập nhật") : t("Lưu ghi chú")}
          </button>
          {draft.id && (
            <button type="button" onClick={() => setDraft(EMPTY)} className="rounded-sm border border-line px-4 py-2 text-sm">
              {t("Hủy sửa")}</button>
          )}
        </div>
      </section>

      {error && <p role="alert" className="rounded-sm border border-accent/50 p-3 text-warning">{t(error)}</p>}

      {bookmarks.length > 0 && (
        <section className="space-y-3">
          <h2 className="font-display text-xl text-strong">{t("Bookmarks")}</h2>
          <ul className="space-y-2">
            {bookmarks.map((note) => (
              <li key={note.id} className="rounded-sm border border-line p-3 text-sm text-body">
                <span className="text-xs font-medium text-accent">{t(note.noteType)}</span>
                <p className="mt-1 whitespace-pre-wrap">{note.content}</p>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="space-y-3">
        <h2 className="font-display text-xl text-strong">{t("Ghi chú của bạn")}</h2>
        {notes.length === 0 ? (
          <p className="text-subtle">{t("Chưa có ghi chú.")}</p>
        ) : (
          <ul className="space-y-3">
            {notes.map((note) => (
              <li key={note.id} className="space-y-2 rounded-sm border border-line p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-xs font-medium text-positive">{t(note.noteType)}</span>
                  <div className="flex flex-wrap gap-2 text-sm">
                    <button type="button" className="underline" onClick={() => edit(note)}>{t("Sửa")}</button>
                    {note.questionId && (
                      <button type="button" className="underline" onClick={() => void convert(note.id)}>{t("→ Flashcard")}</button>
                    )}
                    <button type="button" className="underline text-warning" onClick={() => void remove(note.id)}>{t("Xóa")}</button>
                  </div>
                </div>
                <p className="whitespace-pre-wrap text-strong">{note.content}</p>
                {note.tags?.length > 0 && (
                  <p className="text-xs text-subtle">{t("Tags: ")}{note.tags.join(", ")}</p>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}

export default function NotesPage() {
  return <RequireAuth><Notes /></RequireAuth>;
}
