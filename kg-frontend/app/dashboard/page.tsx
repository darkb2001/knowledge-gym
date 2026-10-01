"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/ui";
import KnowledgeRadar from "@/components/KnowledgeRadar";
import HeatmapCalendar from "@/components/HeatmapCalendar";
import Leaderboard from "@/components/Leaderboard";
import { loadDashboard, type DashboardData } from "@/lib/dashboard";

function Dashboard() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    loadDashboard(controller.signal).then((value) => {
      if (!controller.signal.aborted) { setData(value); setError(""); }
    }).catch((reason: unknown) => {
      if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "Không tải được dashboard");
    });
    return () => controller.abort();
  }, [reload]);

  return <div className="space-y-7">
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div><p className="text-xs uppercase tracking-[0.2em] text-ember-400">Tiến độ học tập</p><h1 className="font-display text-3xl text-ink-50">Dashboard</h1></div>
      {data && <div className="rounded-sm border border-moss-600/60 px-4 py-2 text-sm text-moss-300">🔥 {data.stats.currentStreak} ngày liên tiếp · {data.stats.xp.toLocaleString("vi-VN")} XP</div>}
    </div>
    {error && <div role="alert" className="rounded-sm border border-ember-500/50 p-4 text-ember-300">{error}<button onClick={() => setReload((n) => n + 1)} className="ml-3 underline">Thử lại</button></div>}
    {!data && !error && <p className="animate-soft-pulse text-ink-400">Đang tải tiến độ…</p>}
    {data && <>
      <div className="grid gap-5 lg:grid-cols-2">
        <section className="space-y-3 rounded-sm border border-ink-700 bg-ink-900/50 p-5"><h2 className="font-display text-xl">Mastery theo module</h2><KnowledgeRadar modules={data.radar} /></section>
        <section className="space-y-4 rounded-sm border border-ink-700 bg-ink-900/50 p-5"><h2 className="font-display text-xl">Hoạt động · 90 ngày</h2><HeatmapCalendar days={data.heatmap} /></section>
      </div>
      <section className="space-y-4 rounded-sm border border-ink-700 bg-ink-900/50 p-5"><div><h2 className="font-display text-xl">Bảng xếp hạng</h2><p className="text-xs text-ink-400">Top người học theo XP</p></div><Leaderboard users={data.leaderboard} /></section>
    </>}
  </div>;
}

export default function DashboardPage() { return <RequireAuth><Dashboard /></RequireAuth>; }
