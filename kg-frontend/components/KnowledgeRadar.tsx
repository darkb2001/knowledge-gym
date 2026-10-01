import type { RadarModule } from "@/lib/dashboard";

function point(index: number, count: number, radius: number): [number, number] {
  const angle = -Math.PI / 2 + (2 * Math.PI * index) / count;
  return [160 + Math.cos(angle) * radius, 145 + Math.sin(angle) * radius];
}

export default function KnowledgeRadar({ modules }: { modules: RadarModule[] }) {
  if (!modules.length) return <p className="text-sm text-ink-400">Làm quiz hoặc ôn flashcard để tạo dữ liệu mastery.</p>;
  const items = modules.slice(0, 12);
  const axis = items.map((item, index) => point(index, items.length, 106));
  const value = items.map((item, index) => point(index, items.length, 106 * Math.min(100, Math.max(0, item.masteryPct)) / 100));
  const polygon = (points: [number, number][]) => points.map(([x, y]) => `${x},${y}`).join(" ");
  return <div className="mx-auto w-full max-w-lg">
    <svg viewBox="0 0 320 290" role="img" aria-label="Mastery theo module" className="w-full overflow-visible">
      {[0.25, 0.5, 0.75, 1].map((scale) => <polygon key={scale} points={polygon(items.map((_, i) => point(i, items.length, 106 * scale)))} fill="none" stroke="rgb(71 85 105)" strokeWidth="1" />)}
      {axis.map(([x, y], i) => <line key={i} x1="160" y1="145" x2={x} y2={y} stroke="rgb(71 85 105)" />)}
      <polygon points={polygon(value)} fill="rgba(240,154,92,.24)" stroke="rgb(240 154 92)" strokeWidth="2" />
      {axis.map(([x, y], i) => <text key={items[i].moduleId} x={160 + (x - 160) * 1.13} y={145 + (y - 145) * 1.13} textAnchor={x < 145 ? "end" : x > 175 ? "start" : "middle"} dominantBaseline="middle" fill="rgb(226 232 240)" fontSize="8">{items[i].name.slice(0, 19)}</text>)}
    </svg>
    <p className="text-center text-xs text-ink-400">{items.length} module{items.length === 1 ? "" : "s"} · tối đa 12 hiển thị</p>
  </div>;
}
