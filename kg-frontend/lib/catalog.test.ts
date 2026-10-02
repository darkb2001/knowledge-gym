import { describe, expect, it } from "vitest";
import { filterModules, normalizeSearch } from "./catalog";
import type { Module, Topic } from "./types";

const topics: Topic[] = [
  { id: "java", slug: "java", name: "Java", description: null, displayOrder: 1, moduleCount: 2 },
  { id: "db", slug: "db", name: "Cơ sở dữ liệu", description: null, displayOrder: 2, moduleCount: 1 },
];
const modules: Module[] = [
  { id: "sql", slug: "sql", name: "SQL", description: null, displayOrder: 1, topicId: "db", topicSlug: "db", questionCount: 0 },
  { id: "jvm", slug: "jvm", name: "Bộ nhớ JVM", description: "Heap và stack", displayOrder: 2, topicId: "java", topicSlug: "java", questionCount: 4 },
  { id: "core", slug: "core", name: "Java Core", description: null, displayOrder: 1, topicId: "java", topicSlug: "java", questionCount: 7 },
];
describe("topic-first directory", () => {
  it("matches Vietnamese with or without accents and crossed d", () => {
    expect(normalizeSearch("  CƠ SỞ DỮ LIỆU ĐƯỢC  ")).toBe("co so du lieu duoc");
    expect(filterModules(modules, topics, "", "bo nho").map(item => item.id)).toEqual(["jvm"]);
  });
  it("combines the topic selection and query without leaking other topics", () => {
    expect(filterModules(modules, topics, "java", "").map(item => item.id)).toEqual(["core", "jvm"]);
    expect(filterModules(modules, topics, "java", "sql")).toEqual([]);
  });
  it("searches topic names and descriptions using real catalog fields", () => {
    expect(filterModules(modules, topics, "", "co so du lieu").map(item => item.id)).toEqual(["sql"]);
    expect(filterModules(modules, topics, "", "stack").map(item => item.id)).toEqual(["jvm"]);
  });
  it("returns an honest empty state and never mutates the source array", () => {
    const order = modules.map(item => item.id);
    expect(filterModules(modules, topics, "missing", "")).toEqual([]);
    filterModules(modules, topics, "", "");
    expect(modules.map(item => item.id)).toEqual(order);
  });
});
