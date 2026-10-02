"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { ArrowRightIcon as ArrowRight, BookOpenIcon as BookOpen, CardsIcon as Cards, MagnifyingGlassIcon as MagnifyingGlass, ExamIcon as Exam, CompassIcon as Compass } from "@phosphor-icons/react";
import { RequireAuth, PageHeading, ContentLanguageNotice } from "@/components/ui";
import { useLocale } from "@/components/locale";
import { listModules, listTopics } from "@/lib/questions";
import { filterModules } from "@/lib/catalog";
import type { Module, Topic } from "@/lib/types";

type PracticeMode = { icon: typeof BookOpen; label: string; description: string; href: string };

function PracticeLinks({ modes }: { modes: PracticeMode[] }) {
  const { t } = useLocale();
  return <div className="space-y-4">{modes.map(({ icon: Icon, label, description, href }) => <div key={href}><Link href={href} className="flex min-h-11 items-center gap-2 text-sm font-semibold text-accent underline-offset-4 hover:underline"><Icon size={20} aria-hidden />{t(label)}<ArrowRight size={16} aria-hidden className="ml-auto" /></Link><p className="mt-1 text-xs leading-relaxed text-body">{t(description)}</p></div>)}</div>;
}

function TopicExplorer() {
  const { t, formatLocale } = useLocale();
  const [topics, setTopics] = useState<Topic[]>([]);
  const [modules, setModules] = useState<Module[]>([]);
  const [topicId, setTopicId] = useState("");
  const [selectedId, setSelectedId] = useState("");
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError("");
    Promise.all([listTopics(controller.signal), listModules(undefined, controller.signal)])
      .then(([nextTopics, nextModules]) => { if (!controller.signal.aborted) { setTopics([...nextTopics].sort((a, b) => a.displayOrder - b.displayOrder)); setModules(nextModules); } })
      .catch(reason => { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "Không tải được chủ đề"); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [attempt]);
  const visible = useMemo(() => filterModules(modules, topics, topicId, query), [modules, topics, topicId, query]);
  const selected = visible.find(module => module.id === selectedId);
  const chosenTopic = topics.find(topic => topic.id === topicId);
  const number = (value: number) => value.toLocaleString(formatLocale);
  const modes = selected ? [
    { icon: BookOpen, label: "Đọc & khám phá", description: "Đọc câu hỏi, tìm hiểu đáp án và kết nối kiến thức.", href: `/questions?moduleId=${encodeURIComponent(selected.id)}` },
    { icon: Cards, label: "Ôn flashcard", description: "Tự kiểm tra trí nhớ với lịch ôn cách quãng.", href: `/flashcard/${encodeURIComponent(selected.id)}` },
    { icon: Exam, label: "Luyện trắc nghiệm", description: "Kiểm tra kiến thức và xem lại những câu chưa đúng.", href: `/quiz/${encodeURIComponent(selected.id)}` },
  ] : [];

  return <div>
    <PageHeading title="Bạn muốn học gì hôm nay?" description="Chọn một chủ đề, tìm module phù hợp rồi bắt đầu theo cách bạn thích." />
    <div className="mb-8 max-w-2xl"><label htmlFor="topic-search" className="sr-only">{t("Tìm chủ đề hoặc module")}</label><div className="relative"><MagnifyingGlass size={21} aria-hidden className="pointer-events-none absolute left-4 top-3.5 text-subtle" /><input id="topic-search" type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder={t("Ví dụ: Java, Spring, cơ sở dữ liệu…")} className="kg-field pl-12" /></div></div>
    {error ? <div role="alert" className="mb-6">{t(error)}<button type="button" onClick={() => setAttempt(value => value + 1)} className="ml-4 font-medium underline">{t("Thử lại")}</button></div> : null}
    {loading ? <div role="status" className="kg-panel flex min-h-64 items-center justify-center text-subtle">{t("Đang tải chủ đề…")}</div> : !error && <>
      <div className="mb-8 flex flex-wrap gap-2" role="group" aria-label={t("Chủ đề")}>
        <button type="button" aria-pressed={!topicId} onClick={() => { setTopicId(""); setSelectedId(""); }} className={`min-h-11 rounded-full border px-5 text-sm font-medium ${!topicId ? "border-accent bg-accent text-on-accent" : "border-line bg-surface text-body hover:bg-muted"}`}>{t("Tất cả chủ đề")}</button>
        {topics.map(topic => <button key={topic.id} type="button" aria-pressed={topic.id === topicId} onClick={() => { setTopicId(topic.id); setSelectedId(""); }} className={`min-h-11 rounded-full border px-5 text-sm font-medium transition-colors ${topic.id === topicId ? "border-accent bg-accent text-on-accent" : "border-line bg-surface text-body hover:bg-muted"}`}>{topic.name}</button>)}
      </div>
      {topics.length === 0 && <p className="mb-6 text-subtle">{t("Chưa có chủ đề để hiển thị.")}</p>}
      <div className="grid items-start gap-7 xl:grid-cols-[minmax(0,1fr)_310px]">
        <section className="min-w-0" aria-labelledby="module-directory-title">
          <div className="mb-4 flex flex-wrap items-baseline justify-between gap-3"><h2 id="module-directory-title" className="text-xl">{chosenTopic?.name ?? t("Các module")}</h2><span className="text-sm tabular-nums text-subtle">{number(visible.length)} {t("module")}</span></div>
          {chosenTopic?.description && <p className="mb-5 max-w-2xl text-sm leading-relaxed text-subtle">{chosenTopic.description}</p>}
          <div className="overflow-hidden rounded-2xl border border-line/80 bg-surface">
            {visible.length === 0 ? <div className="p-8"><p className="text-subtle">{t(query ? "Không có module khớp tìm kiếm." : "Chưa có module trong chủ đề này.")}</p>{query && <button type="button" onClick={() => setQuery("")} className="kg-secondary mt-4">{t("Xóa tìm kiếm")}</button>}</div> : visible.map(module => {
              const active = selected?.id === module.id;
              const parentTopic = topics.find(topic => topic.id === module.topicId);
              return <div key={module.id} className={`border-b border-line/60 last:border-b-0 ${active ? "bg-sage/70" : ""}`}><button type="button" aria-pressed={active} onClick={() => setSelectedId(module.id)} className={`group flex w-full items-center gap-4 px-5 py-5 text-left transition-colors sm:px-6 sm:py-6 ${active ? "" : "hover:bg-muted/50"}`}>
                <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${active ? "bg-positive/10 text-positive" : "bg-muted text-accent"}`}><BookOpen size={23} weight={active ? "fill" : "regular"} aria-hidden /></span>
                <span className="min-w-0 flex-1"><span className="block text-xs text-subtle">{parentTopic?.name ?? module.topicSlug}</span><span className="mt-1 block break-words text-lg font-semibold tracking-[-0.02em] text-strong">{module.name}</span>{module.description && <span className="mt-1 block text-sm leading-relaxed text-subtle">{module.description}</span>}<span className="mt-2 block text-xs tabular-nums text-body">{number(module.questionCount)} {t("câu hỏi")}</span></span>
                <ArrowRight size={20} aria-hidden className={active ? "text-positive" : "text-subtle group-hover:text-accent"} />
              </button>{active && <div role="group" aria-label={t("Bạn chọn cách học")} className="border-t border-positive/15 px-5 pb-5 pt-3 sm:px-6 xl:hidden"><PracticeLinks modes={modes} /></div>}</div>;
            })}
          </div>
          <ContentLanguageNotice />
        </section>
        <aside id="practice-options" aria-labelledby="practice-options-title" className={`rounded-2xl bg-sand/65 p-6 xl:sticky xl:top-8 ${selected ? "hidden xl:block" : ""}`}>
          {selected ? <><p className="mb-2 text-sm text-subtle">{t("Bạn chọn cách học")}</p><h2 id="practice-options-title" className="break-words text-2xl">{selected.name}</h2><div className="mt-6"><PracticeLinks modes={modes} /></div></> : <><Compass size={36} weight="duotone" className="mb-5 text-accent" aria-hidden /><h2 id="practice-options-title" className="text-xl">{t("Bắt đầu từ đây")}</h2><p className="mt-3 text-sm leading-relaxed text-body">{t("Chọn module để xem các cách luyện tập.")}</p></>}
          <div className="mt-7 border-t border-line pt-5"><Link href="/questions" className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-accent hover:underline">{t("Khám phá câu hỏi")}<ArrowRight size={17} aria-hidden /></Link></div>
        </aside>
      </div>
    </>}
  </div>;
}

export default function LearnPage() { return <RequireAuth><TopicExplorer /></RequireAuth>; }
