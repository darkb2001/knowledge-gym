"use client";
import { useLocale } from "@/components/locale";
import type { LeaderboardUser } from "@/lib/dashboard";

export default function Leaderboard({ users }: { users: LeaderboardUser[] }) {
  const { t, formatLocale } = useLocale();
  if (!users.length) return <p className="text-sm text-subtle">{t("Bảng xếp hạng sẽ hiện khi có người tích lũy XP.")}</p>;
  return <ol className="space-y-2">
    {users.map((user) => <li key={user.userId} className="flex items-center gap-3 rounded-sm border border-line px-3 py-2">
      <span className="w-8 text-center font-display text-warning">{user.rank}</span>
      <span className="min-w-0 flex-1 truncate">{user.displayName}</span>
      <span className="text-sm tabular-nums text-positive">{user.xp.toLocaleString(formatLocale)} {t(" XP")}</span>
    </li>)}
  </ol>;
}
