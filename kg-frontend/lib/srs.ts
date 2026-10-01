import { apiRequest } from "./api-client";

/**
 * Khớp `SrsResponses.ReviewResponse` của BE. `correct` là diễn giải `quality >= 2` do BE tính —
 * FE hiển thị lại thay vì hardcode ngưỡng (đổi ngưỡng ở BE là một chỗ, không phải hai).
 */
export type ReviewResponse = {
  cardId: string;
  quality: number;
  intervalDays: number;
  easeFactor: number;
  repetitions: number;
  nextReview: string;
  correct: boolean;
};

export type EnrollResponse = {
  enrolled: number;
  cardIds: string[];
  deckId: string | null;
};

/** Thang đánh giá — index chính là `quality` gửi lên BE (0–3). */
export const QUALITY = {
  AGAIN: 0,
  HARD: 1,
  GOOD: 2,
  EASY: 3,
} as const;

export type DueCard = {
  cardId: string;
  questionId: string;
  moduleId: string;
  moduleSlug: string | null;
  title: string;
  answerHtml: string;
  difficulty: string | null;
  repetitions: number;
  easeFactor: number;
  intervalDays: number;
  nextReview: string;
};

/** Mode A: enroll cả module (auto deck). Mode B: danh sách câu cụ thể. */
export async function enrollModule(
  moduleId: string,
  signal?: AbortSignal,
): Promise<EnrollResponse> {
  return apiRequest<EnrollResponse>("/srs/enroll", {
    method: "POST",
    body: { moduleId },
    signal,
  });
}

export async function enrollQuestions(
  questionIds: string[],
  deckId?: string,
  signal?: AbortSignal,
): Promise<EnrollResponse> {
  return apiRequest<EnrollResponse>("/srs/enroll", {
    method: "POST",
    body: { questionIds, deckId },
    signal,
  });
}

export async function listDue(
  params: { moduleId?: string; limit?: number } = {},
  signal?: AbortSignal,
): Promise<DueCard[]> {
  const query = new URLSearchParams();
  if (params.moduleId) query.set("moduleId", params.moduleId);
  if (params.limit) query.set("limit", String(params.limit));
  const suffix = query.toString() ? `?${query}` : "";
  return apiRequest<DueCard[]>(`/srs/due${suffix}`, { signal });
}

/**
 * @param timeMs ms từ lúc thẻ được hiển thị (không phải từ lúc lật). Luôn gửi khi đo được —
 *   BE lưu NULL nếu thiếu; analytics phân biệt "không đo" với "0 ms".
 */
export async function reviewCard(
  cardId: string,
  quality: number,
  timeMs?: number,
  signal?: AbortSignal,
): Promise<ReviewResponse> {
  return apiRequest<ReviewResponse>(`/srs/review/${encodeURIComponent(cardId)}`, {
    method: "POST",
    body: { quality, timeMs },
    signal,
  });
}
