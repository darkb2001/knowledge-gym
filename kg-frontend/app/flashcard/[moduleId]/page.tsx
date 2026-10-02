"use client";
import { useLocale } from "@/components/locale";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { FlashcardDeck, type ReviewSummary } from "@/components/FlashcardDeck";
import { RequireAuth } from "@/components/ui";
import { ApiError } from "@/lib/api-client";
import { listModules } from "@/lib/questions";
import { enrollModule, listDue, type DueCard } from "@/lib/srs";

type Session =
  | { phase: "loading" }
  | { phase: "error"; message: string }
  | { phase: "empty"; moduleName: string; created: number }
  | { phase: "reviewing"; moduleName: string; cards: DueCard[]; created: number }
  | { phase: "done"; moduleName: string; summaries: ReviewSummary[] };

function isAbortError(err: unknown): boolean {
  return err instanceof DOMException && err.name === "AbortError";
}

function moduleLabel(modules: { id: string; slug: string; name: string }[], moduleId: string) {
  const match = modules.find((m) => m.id === moduleId);
  return match ? `${match.slug} · ${match.name}` : "Module";
}

/** Tally theo `quality` để màn kết thúc nói được user vừa quên bao nhiêu thẻ. */
function tally(summaries: ReviewSummary[]) {
  const again = summaries.filter((s) => s.quality === 0).length;
  const hard = summaries.filter((s) => s.quality === 1).length;
  const good = summaries.filter((s) => s.quality === 2).length;
  const easy = summaries.filter((s) => s.quality === 3).length;
  return { again, hard, good, easy };
}

function FlashcardSession() {
  const { t } = useLocale();
  const params = useParams<{ moduleId: string }>();
  const moduleId = params.moduleId;
  const [session, setSession] = useState<Session>({ phase: "loading" });
  const [attempt, setAttempt] = useState(0);

  // Enroll mode A mỗi lần vào phiên: idempotent ở BE (ON CONFLICT DO NOTHING), nên gọi lại
  // sau khi user đã học chỉ trả `enrolled: 0` — không cần phân nhánh "đã enroll chưa" ở FE.
  useEffect(() => {
    const ac = new AbortController();
    (async () => {
      try {
        const [modules, enrolled] = await Promise.all([
          listModules(undefined, ac.signal),
          enrollModule(moduleId, ac.signal),
        ]);
        if (ac.signal.aborted) return;
        const name = moduleLabel(modules, moduleId);
        // MAX_LIMIT của BE = 100; mặc định 20 sẽ cắt im phiên ôn ở module lớn.
        const cards = await listDue({ moduleId, limit: 100 }, ac.signal);
        if (ac.signal.aborted) return;
        setSession(
          cards.length === 0
            ? { phase: "empty", moduleName: name, created: enrolled.enrolled }
            : { phase: "reviewing", moduleName: name, cards, created: enrolled.enrolled },
        );
      } catch (err) {
        if (ac.signal.aborted || isAbortError(err)) return;
        setSession({
          phase: "error",
          message: err instanceof ApiError ? err.message : "Không mở được phiên ôn tập",
        });
      }
    })();
    return () => ac.abort();
  }, [moduleId, attempt]);

  const reload = useCallback(() => {
    setSession({ phase: "loading" });
    setAttempt((n) => n + 1);
  }, []);

  if (session.phase === "loading") {
    return (
      <div className="animate-fade-up">
        <p className="animate-soft-pulse text-subtle">{t("Đang chuẩn bị phiên ôn…")}</p>
      </div>
    );
  }

  if (session.phase === "error") {
    return (
      <div className="animate-fade-up">
        <p className="text-warning" role="alert">
          {t(session.message)}
        </p>
        <div className="mt-4 flex flex-wrap items-center gap-4 text-sm">
          <button
            type="button"
            onClick={reload}
            className="rounded-sm border border-line px-3 py-1.5 hover:border-accent hover:text-warning"
          >
            {t("Thử lại")}</button>
          <Link
            href={`/questions?moduleId=${encodeURIComponent(moduleId)}`}
            className="text-subtle hover:text-warning"
          >
            {t("← Danh sách câu hỏi")}</Link>
        </div>
      </div>
    );
  }

  if (session.phase === "empty") {
    return (
      <div className="animate-fade-up">
        <header className="mb-6">
          <p className="text-xs uppercase tracking-[0.18em] text-subtle">
            {session.moduleName}
          </p>
          <h1 className="mt-2 font-display text-3xl text-strong">{t("Ôn flashcard")}</h1>
        </header>
        <div className="rounded-sm border border-line bg-surface/50 p-8 text-center">
          <p className="font-display text-xl text-positive">{t("Không có thẻ nào đến hạn")}</p>
          <p className="mt-2 text-sm text-subtle">
            {t("Các thẻ của module này chưa đến hạn ôn. SM-2 sẽ nhắc lại đúng lúc.")}</p>
        </div>
        <Link
          href={`/questions?moduleId=${encodeURIComponent(moduleId)}`}
          className="mt-6 inline-block text-sm text-positive hover:underline"
        >
          {t("← Danh sách câu hỏi")}</Link>
      </div>
    );
  }

  if (session.phase === "done") {
    const stats = tally(session.summaries);
    return (
      <div className="animate-fade-up">
        <header className="mb-6">
          <p className="text-xs uppercase tracking-[0.18em] text-subtle">
            {session.moduleName}
          </p>
          <h1 className="mt-2 font-display text-3xl text-strong">{t("Hết thẻ trong phiên")}</h1>
        </header>
        <div className="rounded-sm border border-line bg-surface/50 p-8">
          <p className="font-display text-xl text-positive">
            {t("Đã ôn ")}{session.summaries.length} {t(" thẻ")}</p>
          <dl className="mt-6 grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
            {(
              [
                ["Again", stats.again, "text-warning"],
                ["Hard", stats.hard, "text-strong"],
                ["Good", stats.good, "text-positive"],
                ["Easy", stats.easy, "text-positive"],
              ] as const
            ).map(([label, value, tone]) => (
              <div key={label} className="rounded-sm border border-line px-3 py-2">
                <dt className="text-xs font-medium text-subtle">{t(label)}</dt>
                <dd className={`mt-1 font-display text-2xl ${tone}`}>{value}</dd>
              </div>
            ))}
          </dl>
        </div>
        <div className="mt-6 flex flex-wrap items-center gap-4 text-sm">
          <button
            type="button"
            onClick={reload}
            className="rounded-sm border border-line px-3 py-1.5 hover:border-accent hover:text-positive"
          >
            {t("Kiểm tra thẻ đến hạn")}</button>
          <Link
            href={`/questions?moduleId=${encodeURIComponent(moduleId)}`}
            className="text-subtle hover:text-warning"
          >
            {t("← Danh sách câu hỏi")}</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="animate-fade-up">
      <header className="mb-6">
        <p className="text-xs uppercase tracking-[0.18em] text-subtle">{session.moduleName}</p>
        <h1 className="mt-2 font-display text-3xl text-strong">{t("Ôn flashcard")}</h1>
        {session.created > 0 ? (
          <p className="mt-2 text-sm text-subtle">
            {t("Đã thêm ")}{session.created} {t(" thẻ mới vào lịch ôn.")}</p>
        ) : null}
      </header>

      <FlashcardDeck
        cards={session.cards}
        onFinished={(summaries) =>
          setSession({ phase: "done", moduleName: session.moduleName, summaries })
        }
      />

      <Link
        href={`/questions?moduleId=${encodeURIComponent(moduleId)}`}
        className="mt-8 inline-block text-sm text-subtle hover:text-warning"
      >
        {t("← Danh sách câu hỏi")}</Link>
    </div>
  );
}

export default function FlashcardPage() {
  return (
    <RequireAuth>
      <FlashcardSession />
    </RequireAuth>
  );
}
