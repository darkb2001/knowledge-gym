"use client";
import { useLocale } from "@/components/locale";
import type { RadarModule } from "@/lib/dashboard";

function point(index: number, count: number, radius: number): [number, number] {
  const angle = -Math.PI / 2 + (2 * Math.PI * index) / count;
  return [160 + Math.cos(angle) * radius, 145 + Math.sin(angle) * radius];
}

export default function KnowledgeRadar({ modules }: { modules: RadarModule[] }) {
  const { t } = useLocale();
  if (!modules.length) return <p className="text-sm text-subtle">{t("Làm quiz hoặc ôn flashcard để tạo dữ liệu mastery.")}</p>;
  const items = modules.slice(0, 12);
  const axis = items.map((item, index) => point(index, items.length, 106));
  const value = items.map((item, index) => point(index, items.length, 106 * Math.min(100, Math.max(0, item.masteryPct)) / 100));
  const polygon = (points: [number, number][]) => points.map(([x, y]) => `${x},${y}`).join(" ");
  return <div className="mx-auto w-full max-w-lg">
    <svg viewBox="0 0 320 290" role="img" aria-label={t("Mastery theo module")} className="w-full overflow-visible">
      {[0.25, 0.5, 0.75, 1].map((scale) => <polygon key={scale} points={polygon(items.map((_, i) => point(i, items.length, 106 * scale)))} fill="none" stroke="rgb(71 85 105)" strokeWidth="1" />)}
      {axis.map(([x, y], i) => <line key={i} x1="160" y1="145" x2={x} y2={y} stroke="rgb(71 85 105)" />)}
      <polygon points={polygon(value)} fill="rgba(52,95,115,.16)" stroke="#345f73" strokeWidth="2" />
      {axis.map(([x, y], i) => <text key={items[i].moduleId} x={160 + (x - 160) * 1.13} y={145 + (y - 145) * 1.13} textAnchor={x < 145 ? "end" : x > 175 ? "start" : "middle"} dominantBaseline="middle" fill="#43525a" fontSize="9">{items[i].name.slice(0, 19)}</text>)}
    </svg>
    <ul className="mt-4 grid gap-x-6 gap-y-2 text-xs sm:grid-cols-2">{items.map(item => <li key={item.moduleId} className="flex justify-between gap-3"><span className="text-body">{item.name}</span><span className="tabular-nums text-accent">{Math.round(item.masteryPct)}%</span></li>)}</ul><p className="mt-4 text-center text-xs text-subtle">{t("tối đa 12 hiển thị")}</p>
  </div>;
}
