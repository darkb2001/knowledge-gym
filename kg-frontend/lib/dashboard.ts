import { apiRequest } from "./api-client";

export type RadarModule = { moduleId: string; name: string; masteryPct: number };
export type HeatmapDay = { date: string; count: number };
export type ProgressModule = { moduleId: string; masteryPct: number; totalAttempts: number; streak: number };
export type UserStats = { xp: number; currentStreak: number; longestStreak: number | null; level: number | null; badges: string[] };
export type LeaderboardUser = { rank: number; userId: string; displayName: string; xp: number };
export type DashboardData = {
  radar: RadarModule[];
  heatmap: HeatmapDay[];
  progress: ProgressModule[];
  stats: UserStats;
  leaderboard: LeaderboardUser[];
};

export async function loadDashboard(signal?: AbortSignal): Promise<DashboardData> {
  const [radar, heatmap, progress, stats, leaderboard] = await Promise.all([
    apiRequest<{ modules: RadarModule[] }>("/dashboard/radar", { signal }),
    apiRequest<HeatmapDay[]>("/dashboard/heatmap?days=90", { signal }),
    apiRequest<ProgressModule[]>("/users/me/progress", { signal }),
    apiRequest<UserStats>("/users/me/stats", { signal }),
    apiRequest<LeaderboardUser[]>("/dashboard/leaderboard", { signal }),
  ]);
  return { radar: radar.modules, heatmap: fillHeatmap(heatmap, 90), progress, stats, leaderboard };
}

/** Fill missing dates from the API's last date so the calendar remains in the configured server zone. */
export function fillHeatmap(days: HeatmapDay[], length: number): HeatmapDay[] {
  if (length < 1) return [];
  const counts = new Map(days.map((day) => [day.date, day.count]));
  const end = days.length ? parseDate(days[days.length - 1].date) : new Date();
  const start = new Date(Date.UTC(end.getUTCFullYear(), end.getUTCMonth(), end.getUTCDate() - length + 1));
  return Array.from({ length }, (_, index) => {
    const date = new Date(Date.UTC(start.getUTCFullYear(), start.getUTCMonth(), start.getUTCDate() + index));
    const key = date.toISOString().slice(0, 10);
    return { date: key, count: counts.get(key) ?? 0 };
  });
}

function parseDate(value: string): Date {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day));
}
