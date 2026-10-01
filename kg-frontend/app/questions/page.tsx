"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { RequireAuth, inputClass } from "@/components/ui";
import { ApiError } from "@/lib/api-client";
import { listModules, listQuestions, listTopics } from "@/lib/questions";
import type { Module, QuestionSummary, Topic } from "@/lib/types";

function difficultyTone(d: string): string {
  switch (d) {
    case "JUNIOR":
      return "text-moss-400 border-moss-600/50";
    case "SENIOR":
      return "text-ember-400 border-ember-500/40";
    default:
      return "text-ink-200 border-ink-600";
  }
}

function isAbortError(err: unknown): boolean {
  return err instanceof DOMException && err.name === "AbortError";
}

function QuestionsBrowser() {
  const searchParams = useSearchParams();
  const moduleParam = searchParams.get("moduleId") ?? "";
  const [topics, setTopics] = useState<Topic[]>([]);
  const [modules, setModules] = useState<Module[]>([]);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [items, setItems] = useState<QuestionSummary[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [moduleId, setModuleId] = useState(moduleParam);
  const [difficulty, setDifficulty] = useState("");
  const [tag, setTag] = useState("");
  const [tagDraft, setTagDraft] = useState("");
  const [q, setQ] = useState("");
  const [qDraft, setQDraft] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [catalogTick, setCatalogTick] = useState(0);

  // Deep link `?moduleId=…` (vd nút "quay lại danh sách câu hỏi" từ trang flashcard) phải áp vào
  // bộ lọc ngay cả khi trang đã mount — `useState(initial)` chỉ chạy một lần nên không đủ.
  useEffect(() => {
    setPage(1);
    setModuleId(moduleParam);
  }, [moduleParam]);

  // Debounce tag so each keystroke does not fire a new request (stale-response race).
  useEffect(() => {
    const handle = window.setTimeout(() => {
      const next = tagDraft.trim();
      if (next === tag) return;
      setPage(1);
      setTag(next);
    }, 300);
    return () => window.clearTimeout(handle);
  }, [tagDraft, tag]);

  useEffect(() => {
    const ac = new AbortController();
    (async () => {
      try {
        const [t, m] = await Promise.all([
          listTopics(ac.signal),
          listModules(undefined, ac.signal),
        ]);
        if (ac.signal.aborted) return;
        setTopics(t);
        setModules(m);
        setCatalogError(null);
      } catch (err) {
        if (ac.signal.aborted || isAbortError(err)) return;
        setCatalogError(err instanceof ApiError ? err.message : "Không tải được catalog");
      }
    })();
    return () => ac.abort();
  }, [catalogTick]);

  const load = useCallback(
    async (signal: AbortSignal) => {
      setLoading(true);
      setError(null);
      try {
        const data = await listQuestions(
          {
            moduleId: moduleId || undefined,
            difficulty: difficulty || undefined,
            tag: tag || undefined,
            q: q || undefined,
            page,
            size: 20,
          },
          signal,
        );
        if (signal.aborted) return;
        setItems(data.items);
        setTotalPages(Math.max(1, data.totalPages));
        setTotalElements(data.totalElements);
      } catch (err) {
        if (signal.aborted || isAbortError(err)) return;
        setError(err instanceof ApiError ? err.message : "Không tải được câu hỏi");
        setItems([]);
      } finally {
        if (!signal.aborted) setLoading(false);
      }
    },
    [moduleId, difficulty, tag, q, page],
  );

  useEffect(() => {
    const ac = new AbortController();
    void load(ac.signal);
    return () => ac.abort();
  }, [load]);

  const tagSuggestions = useMemo(() => {
    const set = new Set<string>();
    items.forEach((item) => item.tags.forEach((t) => set.add(t)));
    return Array.from(set).slice(0, 12);
  }, [items]);

  function onSearch(e: FormEvent) {
    e.preventDefault();
    setPage(1);
    setQ(qDraft.trim());
  }

  return (
    <div className="animate-fade-up">
      <header className="mb-8">
        <p className="font-display text-3xl text-ink-50 sm:text-4xl">Knowledge Gym</p>
        <p className="mt-2 max-w-xl text-ink-400">
          Lọc theo module / độ khó / tag, tìm full-text — mở câu để đọc đáp án đã sanitize.
        </p>
      </header>

      <form
        onSubmit={onSearch}
        className="mb-6 grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end"
      >
        <label className="block text-sm">
          <span className="mb-1.5 block text-ink-200">Tìm kiếm</span>
          <input
            className={inputClass}
            placeholder="vd: heap, bo nho, equals…"
            value={qDraft}
            onChange={(e) => setQDraft(e.target.value)}
          />
        </label>
        <button
          type="submit"
          className="rounded-sm bg-ember-500 px-5 py-2.5 font-medium text-ink-950 transition hover:bg-ember-400"
        >
          Tìm
        </button>
      </form>

      <div className="mb-8 grid gap-3 sm:grid-cols-3">
        <label className="block text-sm">
          <span className="mb-1.5 block text-ink-200">Module</span>
          <select
            className={inputClass}
            value={moduleId}
            onChange={(e) => {
              setPage(1);
              setModuleId(e.target.value);
            }}
          >
            <option value="">Tất cả</option>
            {modules.map((m) => (
              <option key={m.id} value={m.id}>
                {m.slug} · {m.name} ({m.questionCount})
              </option>
            ))}
          </select>
        </label>
        <label className="block text-sm">
          <span className="mb-1.5 block text-ink-200">Độ khó</span>
          <select
            className={inputClass}
            value={difficulty}
            onChange={(e) => {
              setPage(1);
              setDifficulty(e.target.value);
            }}
          >
            <option value="">Tất cả</option>
            <option value="JUNIOR">JUNIOR</option>
            <option value="MID">MID</option>
            <option value="SENIOR">SENIOR</option>
          </select>
        </label>
        <label className="block text-sm">
          <span className="mb-1.5 block text-ink-200">Tag</span>
          <input
            className={inputClass}
            list="tag-suggestions"
            placeholder="slug section…"
            value={tagDraft}
            onChange={(e) => setTagDraft(e.target.value)}
          />
          <datalist id="tag-suggestions">
            {tagSuggestions.map((t) => (
              <option key={t} value={t} />
            ))}
          </datalist>
        </label>
      </div>

      {catalogError ? (
        <p className="mb-4 text-sm text-ember-400" role="alert">
          {catalogError}{" "}
          <button
            type="button"
            className="underline"
            onClick={() => setCatalogTick((n) => n + 1)}
          >
            Thử lại
          </button>
        </p>
      ) : null}

      {moduleId ? (
        <Link
          href={`/flashcard/${encodeURIComponent(moduleId)}`}
          className="mb-6 inline-flex items-center gap-2 rounded-sm border border-moss-600/60 px-4 py-2 text-sm text-moss-400 transition hover:bg-moss-600/10"
        >
          Ôn flashcard module này →
        </Link>
      ) : null}

      <p className="mb-4 text-xs uppercase tracking-[0.18em] text-ink-400">
        {topics.length} topics · {modules.length} modules · {totalElements} hits
      </p>

      {error ? (
        <p className="mb-4 text-sm text-ember-400" role="alert">
          {error}
        </p>
      ) : null}

      {loading ? (
        <p className="animate-soft-pulse text-ink-400">Đang tải…</p>
      ) : items.length === 0 ? (
        <p className="text-ink-400">Không có câu hỏi khớp bộ lọc.</p>
      ) : (
        <ul className="divide-y divide-ink-800 border-y border-ink-800">
          {items.map((item, index) => (
            <li
              key={item.id}
              className="animate-fade-up py-4"
              style={{ animationDelay: `${Math.min(index, 8) * 30}ms` }}
            >
              <Link
                href={`/questions/${item.id}`}
                className="group block transition hover:translate-x-0.5"
              >
                <div className="flex flex-wrap items-baseline gap-2">
                  <span
                    className={`rounded-sm border px-1.5 py-0.5 text-[10px] uppercase tracking-wider ${difficultyTone(item.difficulty)}`}
                  >
                    {item.difficulty}
                  </span>
                  <span className="text-xs text-ink-400">{item.moduleSlug}</span>
                </div>
                <p className="mt-1 font-display text-lg text-ink-50 group-hover:text-ember-300">
                  {item.title}
                </p>
                {item.tags.length > 0 ? (
                  <p className="mt-1 text-xs text-ink-400">{item.tags.join(" · ")}</p>
                ) : null}
              </Link>
            </li>
          ))}
        </ul>
      )}

      <div className="mt-8 flex items-center justify-between gap-4 text-sm">
        <button
          type="button"
          disabled={page <= 1 || loading}
          onClick={() => setPage((p) => Math.max(1, p - 1))}
          className="rounded-sm border border-ink-600 px-3 py-1.5 disabled:opacity-40"
        >
          Trước
        </button>
        <span className="text-ink-400">
          Trang {page} / {totalPages}
        </span>
        <button
          type="button"
          disabled={page >= totalPages || loading}
          onClick={() => setPage((p) => p + 1)}
          className="rounded-sm border border-ink-600 px-3 py-1.5 disabled:opacity-40"
        >
          Sau
        </button>
      </div>
    </div>
  );
}

export default function QuestionsPage() {
  // `useSearchParams` opts the route into client-side rendering — Next requires a Suspense
  // boundary so the shell can still be prerendered.
  return (
    <RequireAuth>
      <Suspense fallback={<p className="animate-soft-pulse text-ink-400">Đang tải…</p>}>
        <QuestionsBrowser />
      </Suspense>
    </RequireAuth>
  );
}
