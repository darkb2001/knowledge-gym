"use client";

import { useCallback, useEffect, useState } from "react";
import { apiRequest, ensureAccessToken, getAccessToken, getApiBase } from "@/lib/api-client";
import { renderSafeMarkdown } from "@/lib/markdown";
import { RequireAuth } from "@/components/ui";

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
    if (!window.confirm("Xóa ghi chú này?")) return;
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
      window.alert(`Đã gắn SRS card ${res.cardId}`);
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
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  return (
    <main className="mx-auto max-w-4xl space-y-7 p-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-xs uppercase tracking-[0.2em] text-ember-400">Ghi chú cá nhân</p>
          <h1 className="font-display text-3xl text-ink-50">Notes</h1>
        </div>
        <div className="flex gap-2">
          <a href="/dashboard" className="rounded-sm border border-ink-700 px-3 py-2 text-sm hover:border-moss-500">Dashboard</a>
          <button type="button" onClick={() => void exportMarkdown()} className="rounded-sm border border-ink-700 px-3 py-2 text-sm hover:border-moss-500">Xuất Markdown</button>
        </div>
      </div>

      <label className="block space-y-1 text-sm text-ink-200">
        Tìm câu hỏi và ghi chú
        <input
          className="w-full rounded-sm border border-ink-700 bg-ink-950 px-3 py-2 text-ink-50"
          value={query}
          onChange={(e) => void search(e.target.value)}
          placeholder="Nhập từ khóa…"
        />
      </label>
      {hits.length > 0 && (
        <section aria-label="Kết quả tìm kiếm" className="space-y-2 rounded-sm border border-ink-700 p-4">
          <h2 className="font-display text-lg text-ink-50">Kết quả</h2>
          <ul className="space-y-2">
            {hits.map((h) => (
              <li key={`${h.type}-${h.id}`} className="border-b border-ink-800 pb-2">
                <span className="mr-2 text-[10px] uppercase tracking-wider text-moss-400">{h.type}</span>
                <span className="text-ink-100">{h.title}</span>
                <p className="text-sm text-ink-400">{h.excerpt}</p>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="space-y-3 rounded-sm border border-ink-700 bg-ink-900/40 p-5">
        <h2 className="font-display text-xl text-ink-50">{draft.id ? "Sửa ghi chú" : "Ghi chú mới"}</h2>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="text-sm text-ink-300">Loại
            <select
              className="mt-1 w-full rounded-sm border border-ink-700 bg-ink-950 px-3 py-2"
              value={draft.noteType}
              onChange={(e) => setDraft((d) => ({ ...d, noteType: e.target.value }))}
            >
              <option value="QUICK">QUICK</option>
              <option value="STUDY">STUDY</option>
              <option value="HIGHLIGHT">HIGHLIGHT</option>
              <option value="BOOKMARK">BOOKMARK</option>
            </select>
          </label>
          <label className="text-sm text-ink-300">Question ID (tùy chọn)
            <input
              className="mt-1 w-full rounded-sm border border-ink-700 bg-ink-950 px-3 py-2 font-mono text-sm"
              value={draft.questionId}
              onChange={(e) => setDraft((d) => ({ ...d, questionId: e.target.value }))}
              placeholder="UUID câu hỏi để convert SRS"
            />
          </label>
        </div>
        <label className="block text-sm text-ink-300">Tags (phẩy tách)
          <input
            className="mt-1 w-full rounded-sm border border-ink-700 bg-ink-950 px-3 py-2"
            value={draft.tags}
            onChange={(e) => setDraft((d) => ({ ...d, tags: e.target.value }))}
            placeholder="java, jvm"
          />
        </label>
        <textarea
          className="min-h-40 w-full rounded-sm border border-ink-700 bg-ink-950 p-3 font-mono text-sm text-ink-100"
          value={draft.content}
          onChange={(e) => setDraft((d) => ({ ...d, content: e.target.value }))}
          placeholder="Viết ghi chú Markdown…"
          aria-label="Nội dung ghi chú"
        />
        <div>
          <p className="mb-1 text-xs uppercase tracking-wider text-ink-400">Xem trước an toàn</p>
          <div
            className="answer-html min-h-24 rounded-sm border border-ink-800 bg-ink-950/80 p-3"
            dangerouslySetInnerHTML={{ __html: previewHtml || "<p class='text-ink-500'>Chưa có nội dung</p>" }}
          />
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled={busy}
            onClick={() => void save()}
            className="rounded-sm bg-moss-700 px-4 py-2 text-sm text-ink-50 disabled:opacity-50"
          >
            {draft.id ? "Cập nhật" : "Lưu ghi chú"}
          </button>
          {draft.id && (
            <button type="button" onClick={() => setDraft(EMPTY)} className="rounded-sm border border-ink-700 px-4 py-2 text-sm">
              Hủy sửa
            </button>
          )}
        </div>
      </section>

      {error && <p role="alert" className="rounded-sm border border-ember-500/50 p-3 text-ember-300">{error}</p>}

      {bookmarks.length > 0 && (
        <section className="space-y-3">
          <h2 className="font-display text-xl text-ink-50">Bookmarks</h2>
          <ul className="space-y-2">
            {bookmarks.map((note) => (
              <li key={note.id} className="rounded-sm border border-ink-700 p-3 text-sm text-ink-200">
                <span className="text-xs uppercase text-ember-400">{note.noteType}</span>
                <p className="mt-1 whitespace-pre-wrap">{note.content}</p>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="space-y-3">
        <h2 className="font-display text-xl text-ink-50">Ghi chú của bạn</h2>
        {notes.length === 0 ? (
          <p className="text-ink-400">Chưa có ghi chú.</p>
        ) : (
          <ul className="space-y-3">
            {notes.map((note) => (
              <li key={note.id} className="space-y-2 rounded-sm border border-ink-700 p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-xs uppercase tracking-wider text-moss-400">{note.noteType}</span>
                  <div className="flex flex-wrap gap-2 text-sm">
                    <button type="button" className="underline" onClick={() => edit(note)}>Sửa</button>
                    {note.questionId && (
                      <button type="button" className="underline" onClick={() => void convert(note.id)}>→ Flashcard</button>
                    )}
                    <button type="button" className="underline text-ember-300" onClick={() => void remove(note.id)}>Xóa</button>
                  </div>
                </div>
                <p className="whitespace-pre-wrap text-ink-100">{note.content}</p>
                {note.tags?.length > 0 && (
                  <p className="text-xs text-ink-400">Tags: {note.tags.join(", ")}</p>
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
