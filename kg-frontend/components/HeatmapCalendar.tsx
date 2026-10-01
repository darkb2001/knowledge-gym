import type { HeatmapDay } from "@/lib/dashboard";

function intensity(count: number): string {
  if (count <= 0) return "bg-ink-800";
  if (count === 1) return "bg-moss-700";
  if (count <= 3) return "bg-moss-500";
  return "bg-moss-300";
}

export default function HeatmapCalendar({ days }: { days: HeatmapDay[] }) {
  if (!days.length || days.every((day) => day.count === 0)) {
    return <div><p className="text-sm text-ink-400">Chưa có hoạt động trong 90 ngày qua.</p><Calendar days={days} /></div>;
  }
  return <div className="space-y-3">
    <Calendar days={days} />
    <div className="flex items-center justify-end gap-2 text-xs text-ink-400"><span>Ít</span>{[0, 1, 2, 4].map((n) => <span key={n} className={`h-3 w-3 rounded-sm ${intensity(n)}`} />)}<span>Nhiều</span></div>
  </div>;
}

function Calendar({ days }: { days: HeatmapDay[] }) {
  const firstDay = days.length ? new Date(`${days[0].date}T00:00:00Z`).getUTCDay() : 0;
  const cells: (HeatmapDay | null)[] = [...Array.from({ length: firstDay }, () => null), ...days];
  while (cells.length % 7) cells.push(null);
  const columns = Math.ceil(cells.length / 7);
  return <div className="grid gap-1 overflow-x-auto" role="grid" aria-label="Hoạt động học tập 90 ngày" style={{ gridTemplateRows: "repeat(7,minmax(0,1fr))", gridAutoFlow: "column", gridTemplateColumns: `repeat(${columns},minmax(0,1fr))` }}>
    {cells.map((day, index) => <span key={day?.date ?? `empty-${index}`} role={day ? "gridcell" : undefined} aria-label={day ? `${day.date}: ${day.count} lượt học` : undefined} title={day ? `${day.date}: ${day.count} lượt học` : undefined} className={`h-3 w-3 rounded-sm ${day ? intensity(day.count) : ""}`} />)}
  </div>;
}
