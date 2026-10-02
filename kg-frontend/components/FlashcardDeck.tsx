"use client";
import { useLocale } from "@/components/locale";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "@/lib/api-client";
import { sanitizeAnswerHtml } from "@/lib/sanitize-html";
import { QUALITY, reviewCard, type DueCard } from "@/lib/srs";
import { formatReviewInterval } from "@/lib/i18n";

type Rating = {
  label: string;
  quality: number;
  hint: string;
  tone: string;
};

/** Thứ tự nút và phím tắt 1–4 khớp `quality` gửi lên BE (0 Again → 3 Easy). */
const RATINGS: Rating[] = [
  {
    label: "Again",
    quality: QUALITY.AGAIN,
    hint: "Quên — reset về 1 ngày",
    tone: "border-accent/60 text-warning hover:bg-accent/10",
  },
  {
    label: "Hard",
    quality: QUALITY.HARD,
    hint: "Nhớ nhưng khó",
    tone: "border-line text-body hover:border-accent hover:text-warning",
  },
  {
    label: "Good",
    quality: QUALITY.GOOD,
    hint: "Nhớ bình thường",
    tone: "border-accent/60 text-positive hover:bg-accent/10",
  },
  {
    label: "Easy",
    quality: QUALITY.EASY,
    hint: "Quá dễ",
    tone: "border-accent text-positive hover:bg-accent/15",
  },
];

export type ReviewSummary = {
  cardId: string;
  quality: number;
  intervalDays: number;
  nextReview: string;
};

function ratingLabel(quality: number): string {
  return RATINGS.find((r) => r.quality === quality)?.label ?? String(quality);
}

/**
 * Phiên flashcard: lật thẻ → tự chấm 4 mức → gọi `/srs/review/{cardId}`.
 *
 * `cards` là queue ban đầu từ BE; component tự tiến index và báo `onFinished` khi hết thẻ. Không tự
 * refetch sau mỗi lần chấm — thẻ vừa chấm chắc chắn không còn đến hạn hôm nay, và refetch giữa phiên
 * làm mất thứ tự đang học.
 */
export function FlashcardDeck({
  cards,
  onFinished,
}: {
  cards: DueCard[];
  onFinished?: (summaries: ReviewSummary[]) => void;
}) {
  const { t, locale } = useLocale();
  const [index, setIndex] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [summaries, setSummaries] = useState<ReviewSummary[]>([]);

  const shownAtRef = useRef<number>(Date.now());
  const card = cards[index];

  // Thời gian đo từ lúc thẻ được hiển thị — mỗi thẻ mới bắt đầu lại đồng hồ.
  useEffect(() => {
    shownAtRef.current = Date.now();
    setFlipped(false);
    setError(null);
  }, [index, card?.cardId]);

  const submit = useCallback(
    async (quality: number) => {
      if (!card || submitting) return;
      setSubmitting(true);
      setError(null);
      const timeMs = Date.now() - shownAtRef.current;
      try {
        const result = await reviewCard(card.cardId, quality, timeMs);
        const summary: ReviewSummary = {
          cardId: card.cardId,
          quality,
          intervalDays: result.intervalDays,
          nextReview: result.nextReview,
        };
        const next = [...summaries, summary];
        setSummaries(next);
        if (index + 1 < cards.length) {
          setIndex(index + 1);
        } else {
          onFinished?.(next);
        }
      } catch (err) {
        setError(err instanceof ApiError ? err.message : "Không gửi được kết quả ôn");
        // Gửi lỗi thì đồng hồ phải bắt đầu lại: giữ nguyên `shownAtRef` sẽ khiến lần thử lại
        // cộng dồn cả thời gian của lần hỏng vào `time_ms`, làm hỏng số liệu tốc độ của thẻ này.
        shownAtRef.current = Date.now();
      } finally {
        setSubmitting(false);
      }
    },
    [card, cards.length, index, onFinished, submitting, summaries],
  );

  // Phím tắt: Space/Enter lật; 1–4 (hoặc A/H/G/E) chấm — khớp nút chuột (chấm được cả khi chưa lật).
  // Chỉ một listener ở window: gắn thêm ở khung thẻ sẽ làm Space/Enter toggle 2 lần.
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.repeat) return;
      // Bỏ qua khi focus đang ở phần tử tương tác: để link/button/nút chấm nhận phím native của
      // chúng, thay vì bị preventDefault cướp mất (Space vẫn cuộn được khi focus ở các phần tử này).
      if (
        event.target instanceof HTMLElement &&
        ["INPUT", "TEXTAREA", "SELECT", "BUTTON", "A"].includes(event.target.tagName)
      ) {
        return;
      }
      if (event.key === " " || event.key === "Enter") {
        event.preventDefault();
        setFlipped((value) => !value);
        return;
      }
      const byDigit = RATINGS[Number(event.key) - 1];
      const byLetter = RATINGS.find(
        (r) => r.label.charAt(0).toLowerCase() === event.key.toLowerCase(),
      );
      const rating = byDigit ?? byLetter;
      if (rating) {
        event.preventDefault();
        void submit(rating.quality);
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [submit]);

  if (!card) {
    return (
      <div className="rounded-sm border border-line bg-surface/50 p-8 text-center">
        <p className="font-display text-xl text-positive">{t("Hết thẻ trong phiên")}</p>
        <p className="mt-2 text-sm text-subtle">
          {t("Đã ôn ")}{summaries.length} {t(" thẻ. Quay lại sau khi thẻ đến hạn.")}</p>
      </div>
    );
  }

  return (
    <div className="animate-fade-up">
      <div className="mb-3 flex items-baseline justify-between gap-4 text-xs uppercase tracking-[0.18em] text-subtle">
        <span>
          {t("Thẻ ")}{index + 1} {t(" / ")}{cards.length}
        </span>
        {card.moduleSlug ? <span>{card.moduleSlug}</span> : null}
      </div>

      {/* Cả vùng là nút lật thẻ — bàn phím vẫn dùng được nhờ role="button" + tabIndex. */}
      <div
        role="button"
        tabIndex={0}
        aria-label={flipped ? t("Lật về mặt câu hỏi") : t("Lật xem đáp án")}
        onClick={() => setFlipped((value) => !value)}
        className="group relative min-h-[22rem] cursor-pointer rounded-sm border border-line bg-surface/60 transition hover:border-accent/60 [perspective:1600px]"
      >
        <div
          className={`absolute inset-0 transition-transform duration-500 [transform-style:preserve-3d] ${
            flipped ? "[transform:rotateY(180deg)]" : ""
          }`}
        >
          {/*
           * Hai mặt `absolute inset-0` chồng lên nhau — nếu để flow bình thường thì mặt đáp án
           * nằm dưới khung `min-h` sau khi lật (viewport trống). `invisible` (không chỉ
           * backface-visibility) để screen reader / Ctrl+F không đọc đáp án khi chưa lật.
           * Scroll nằm trên từng mặt để nội dung dài vẫn cuộn được trong khung cố định.
           */}
          <div
            aria-hidden={flipped}
            className={`absolute inset-0 overflow-y-auto p-6 [backface-visibility:hidden] sm:p-10 ${
              flipped ? "invisible" : ""
            }`}
          >
            <p className="text-xs uppercase tracking-[0.18em] text-subtle">
              {card.difficulty ?? "—"}
            </p>
            <p className="mt-4 font-display text-2xl text-strong sm:text-3xl">{card.title}</p>
            <p className="mt-10 text-sm text-subtle">{t("Nhấn để lật card — hoặc dùng Space")}</p>
          </div>

          <div
            aria-hidden={!flipped}
            className={`absolute inset-0 overflow-y-auto p-6 [backface-visibility:hidden] [transform:rotateY(180deg)] sm:p-10 ${
              flipped ? "" : "invisible"
            }`}
          >
            <p className="text-xs uppercase tracking-[0.18em] text-positive">{t("Đáp án")}</p>
            <p className="mt-3 font-display text-xl text-strong">{card.title}</p>
            <div
              className="answer-html mt-6 border-t border-line pt-6"
              dangerouslySetInnerHTML={{ __html: sanitizeAnswerHtml(card.answerHtml) }}
            />
          </div>
        </div>
      </div>

      {error ? (
        <p className="mt-4 text-sm text-warning" role="alert">
          {t(error)}
        </p>
      ) : null}

      <div className="mt-6 grid gap-2 sm:grid-cols-4">
        {RATINGS.map((rating) => (
          <button
            key={rating.quality}
            type="button"
            disabled={submitting}
            onClick={() => void submit(rating.quality)}
            className={`rounded-sm border px-3 py-3 text-left transition disabled:opacity-50 ${rating.tone}`}
          >
            <span className="block font-display text-base">{t(rating.label)}</span>
            <span className="mt-0.5 block text-xs text-subtle">{t(rating.hint)}</span>
          </button>
        ))}
      </div>

      <p className="mt-3 text-xs text-subtle">
        {flipped
          ? t("Chấm mức độ nhớ — phím 1–4 cũng dùng được.")
          : t("Chưa lật card vẫn chấm được, nhưng thời gian phản hồi sẽ phản ánh điều đó.")}
      </p>

      {summaries.length > 0 ? (
        <ul className="mt-8 space-y-1 border-t border-line pt-4 text-xs text-subtle">
          {summaries.slice(-5).reverse().map((summary) => (
            <li key={summary.cardId}>
              {t(ratingLabel(summary.quality))} {t(" · lần sau sau ")}{formatReviewInterval(summary.intervalDays, locale)} {t(" (")}{summary.nextReview}{t(")")}</li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
