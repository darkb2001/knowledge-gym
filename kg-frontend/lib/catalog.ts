import type { Module, Topic } from "./types";

export function normalizeSearch(value: string): string {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/đ/g, "d").replace(/Đ/g, "D").toLowerCase().trim();
}

export function filterModules(modules: Module[], topics: Topic[], topicId: string, query: string): Module[] {
  const term = normalizeSearch(query);
  const topicNames = new Map(topics.map(topic => [topic.id, topic.name]));
  return modules.filter(module => (!topicId || module.topicId === topicId) && (!term || normalizeSearch([module.name, module.slug, module.description, topicNames.get(module.topicId)].filter(Boolean).join(" ")).includes(term))).sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name));
}
