"use client";
import { useLocale } from "@/components/locale";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { RequireAuth, inputClass, PageHeading, ContentLanguageNotice } from "@/components/ui";
import { ArrowRightIcon as ArrowRight, MagnifyingGlassIcon as MagnifyingGlass } from "@phosphor-icons/react";
import { ApiError } from "@/lib/api-client";
import { listModules, listQuestions, listTopics } from "@/lib/questions";
import type { Module, QuestionSummary, Topic } from "@/lib/types";

function difficultyTone(d: string): string {
  switch (d) {
    case "JUNIOR":
      return "text-positive border-accent/50";
    case "SENIOR":
      return "text-warning border-accent/40";
    default:
      return "text-body border-line";
  }
}

function isAbortError(err: unknown): boolean {
  return err instanceof DOMException && err.name === "AbortError";
}

function QuestionsBrowser() {
  const { t } = useLocale();
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
  }, [load, catalogTick]);

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

  const selectedModule = modules.find(module => module.id === moduleId);
  function clearFilters() {
    setPage(1); setModuleId(""); setDifficulty(""); setTag(""); setTagDraft(""); setQ(""); setQDraft("");
  }
  return <div>
    <PageHeading title="Thư viện câu hỏi" description="Tìm câu hỏi theo module, độ khó hoặc từ khóa." action={<Link href="/learn" className="kg-secondary">{t("Chọn chủ đề")}<ArrowRight size={18} aria-hidden /></Link>} />
    <form onSubmit={onSearch} className="kg-panel mb-7">
      <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
        <label className="block text-sm font-medium"><span className="mb-2 block">{t("Tìm kiếm")}</span><div className="relative"><MagnifyingGlass size={20} aria-hidden className="pointer-events-none absolute left-4 top-3.5 text-subtle" /><input className={inputClass + " pl-12"} placeholder={t("Ví dụ: heap, bộ nhớ, equals…")} value={qDraft} onChange={event => setQDraft(event.target.value)} /></div></label>
        <button type="submit" className="kg-button">{t("Tìm")}</button>
      </div>
      <div className="mt-5 grid gap-4 sm:grid-cols-3">
        <label className="block text-sm"><span className="mb-2 block font-medium">{t("Module")}</span><select className={inputClass} value={moduleId} onChange={event => { setPage(1); setModuleId(event.target.value); }}><option value="">{t("Tất cả")}</option>{modules.map(module => <option key={module.id} value={module.id}>{module.name} ({module.questionCount})</option>)}</select></label>
        <label className="block text-sm"><span className="mb-2 block font-medium">{t("Độ khó")}</span><select className={inputClass} value={difficulty} onChange={event => { setPage(1); setDifficulty(event.target.value); }}><option value="">{t("Tất cả")}</option><option value="JUNIOR">Junior</option><option value="MID">Mid</option><option value="SENIOR">Senior</option></select></label>
        <label className="block text-sm"><span className="mb-2 block font-medium">{t("Tag")}</span><input className={inputClass} list="tag-suggestions" placeholder={t("Ví dụ: collections")} value={tagDraft} onChange={event => setTagDraft(event.target.value)} /><datalist id="tag-suggestions">{tagSuggestions.map(tag => <option key={tag} value={tag} />)}</datalist></label>
      </div>
    </form>
    {catalogError && <p role="alert" className="mb-5">{t(catalogError)}<button type="button" className="ml-4 underline" onClick={() => setCatalogTick(value => value + 1)}>{t("Thử lại")}</button></p>}
    <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
      <div><h2 className="text-xl">{selectedModule?.name ?? t("Câu hỏi")}</h2><p className="mt-1 text-sm tabular-nums text-subtle">{totalElements} {t("câu hỏi")}<span className="mx-2" aria-hidden>·</span>{topics.length} {t("Chủ đề").toLowerCase()}</p></div>
      <div className="flex flex-wrap gap-2"><Link href="/mock-interview" className="kg-secondary">{t("Luyện phỏng vấn")}</Link>{moduleId && <><Link href={`/quiz/${encodeURIComponent(moduleId)}`} className="kg-secondary">{t("Làm quiz module này")}</Link><Link href={`/flashcard/${encodeURIComponent(moduleId)}`} className="kg-button">{t("Ôn flashcard module này")}</Link></>}</div>
    </div>
    {error ? <p role="alert">{t(error)}<button type="button" className="ml-4 underline" onClick={() => setCatalogTick(value => value + 1)}>{t("Thử lại")}</button></p> : loading ? <p role="status" className="kg-panel text-subtle">{t("Đang tải…")}</p> : items.length === 0 ? <div className="kg-panel py-10"><p className="text-subtle">{t("Không có câu hỏi khớp bộ lọc.")}</p><button type="button" className="kg-secondary mt-5" onClick={clearFilters}>{t("Xóa bộ lọc")}</button></div> : <ul className="overflow-hidden rounded-2xl border border-line/80 bg-surface">
      {items.map(item => <li key={item.id} className="border-b border-line/60 last:border-b-0"><Link href={`/questions/${item.id}`} className="group flex items-center gap-4 px-5 py-5 transition-colors hover:bg-muted/45 sm:px-6"><span className="min-w-0 flex-1"><span className="flex flex-wrap items-center gap-2"><span className={`rounded-md border px-2 py-1 text-[11px] font-medium ${difficultyTone(item.difficulty)}`}>{item.difficulty}</span><span className="text-xs text-subtle">{item.moduleSlug}</span></span><span className="mt-2 block break-words text-lg font-medium text-strong group-hover:text-accent">{item.title}</span>{item.tags.length > 0 && <span className="mt-2 block text-xs text-subtle">{item.tags.join(", ")}</span>}</span><ArrowRight size={19} aria-hidden className="shrink-0 text-subtle group-hover:text-accent" /></Link></li>)}
    </ul>}
    <div className="mt-6 flex items-center justify-between gap-4 text-sm"><button type="button" disabled={page <= 1 || loading} onClick={() => setPage(value => Math.max(1, value - 1))} className="kg-secondary">{t("Trước")}</button><span className="tabular-nums text-subtle">{t("Trang")} {page} / {totalPages}</span><button type="button" disabled={page >= totalPages || loading} onClick={() => setPage(value => value + 1)} className="kg-secondary">{t("Sau")}</button></div>
    <ContentLanguageNotice />
  </div>;
}

export default function QuestionsPage() {
  const { t } = useLocale();
  // `useSearchParams` opts the route into client-side rendering — Next requires a Suspense
  // boundary so the shell can still be prerendered.
  return (
    <RequireAuth>
      <Suspense fallback={<p className="animate-soft-pulse text-subtle">{t("Đang tải…")}</p>}>
        <QuestionsBrowser />
      </Suspense>
    </RequireAuth>
  );
}
