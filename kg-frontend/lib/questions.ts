import { apiRequest } from "./api-client";
import type { Module, PageResponse, QuestionDetail, QuestionSummary, Topic } from "./types";

export type QuestionQuery = {
  moduleId?: string;
  tag?: string;
  difficulty?: string;
  q?: string;
  page?: number;
  size?: number;
};

export async function listTopics(signal?: AbortSignal): Promise<Topic[]> {
  return apiRequest<Topic[]>("/topics", { signal });
}

export async function listModules(topicId?: string, signal?: AbortSignal): Promise<Module[]> {
  const qs = topicId ? `?topicId=${encodeURIComponent(topicId)}` : "";
  return apiRequest<Module[]>(`/modules${qs}`, { signal });
}

export async function listQuestions(
  query: QuestionQuery = {},
  signal?: AbortSignal,
): Promise<PageResponse<QuestionSummary>> {
  const params = new URLSearchParams();
  if (query.moduleId) params.set("moduleId", query.moduleId);
  if (query.tag) params.set("tag", query.tag);
  if (query.difficulty) params.set("difficulty", query.difficulty);
  if (query.q) params.set("q", query.q);
  params.set("page", String(query.page ?? 1));
  params.set("size", String(query.size ?? 20));
  return apiRequest<PageResponse<QuestionSummary>>(`/questions?${params}`, { signal });
}

export async function getQuestion(id: string, signal?: AbortSignal): Promise<QuestionDetail> {
  return apiRequest<QuestionDetail>(`/questions/${encodeURIComponent(id)}`, { signal });
}
