export type User = {
  id: string;
  email: string;
  displayName: string;
  role: string;
};

export type TokenResponse = {
  accessToken: string;
  expiresIn: number;
  user: User;
};

export type Topic = {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  displayOrder: number;
  moduleCount: number;
};

export type Module = {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  displayOrder: number;
  topicId: string;
  topicSlug: string;
  questionCount: number;
};

export type Difficulty = "JUNIOR" | "MID" | "SENIOR";

export type QuestionSummary = {
  id: string;
  moduleId: string;
  moduleSlug: string;
  title: string;
  difficulty: Difficulty;
  tags: string[];
  sortOrder: number;
};

export type QuestionDetail = QuestionSummary & {
  answerHtml: string;
  options: { id: string; content: string; displayOrder: number }[];
};

export type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ApiProblem = {
  title?: string;
  detail?: string;
  status?: number;
};
