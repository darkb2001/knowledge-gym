"use client";
import { useLocale } from "@/components/locale";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { RequireAuth, ContentLanguageNotice } from "@/components/ui";
import { ApiError } from "@/lib/api-client";
import { getQuestion } from "@/lib/questions";
import { sanitizeAnswerHtml } from "@/lib/sanitize-html";
import type { QuestionDetail } from "@/lib/types";

function QuestionDetailView() {
  const { t } = useLocale();
  const params = useParams<{ id: string }>();
  const id = params.id;
  const [question, setQuestion] = useState<QuestionDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const ac = new AbortController();
    (async () => {
      try {
        const data = await getQuestion(id, ac.signal);
        if (!cancelled) setQuestion(data);
      } catch (err) {
        if (cancelled || (err instanceof DOMException && err.name === "AbortError")) return;
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message : "Không tải được câu hỏi");
        }
      }
    })();
    return () => {
      cancelled = true;
      ac.abort();
    };
  }, [id]);

  if (error) {
    return (
      <div>
        <p className="text-warning" role="alert">
          {t(error)}
        </p>
        <Link href="/questions" className="mt-4 inline-block text-sm text-positive hover:underline">
          {t("← Danh sách")}</Link>
      </div>
    );
  }

  if (!question) {
    return <p className="animate-soft-pulse text-subtle">{t("Đang mở đáp án…")}</p>;
  }

  return (
    <article className="kg-reading">
      <Link href="/questions" className="text-sm text-subtle hover:text-warning">
        {t("← Danh sách")}</Link>
      <p className="mt-6 text-sm text-subtle">
        {question.moduleSlug} {t(" · ")}{question.difficulty}
      </p>
      <h1 className="mt-2 font-display text-3xl text-strong sm:text-4xl">{question.title}</h1>
      {question.tags.length > 0 ? (
        <p className="mt-3 text-sm text-subtle">{question.tags.join(" · ")}</p>
      ) : null}

      <ContentLanguageNotice />
      <div
        className="answer-html mt-8 border-t border-line pt-8"
        // Server Jsoup Safelist + client DOMPurify (defense-in-depth).
        dangerouslySetInnerHTML={{ __html: sanitizeAnswerHtml(question.answerHtml) }}
      />
    </article>
  );
}

export default function QuestionDetailPage() {
  return (
    <RequireAuth>
      <QuestionDetailView />
    </RequireAuth>
  );
}
