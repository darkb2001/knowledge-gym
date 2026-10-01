import type { LeaderboardUser } from "@/lib/dashboard";

export default function Leaderboard({ users }: { users: LeaderboardUser[] }) {
  if (!users.length) return <p className="text-sm text-ink-400">Bảng xếp hạng sẽ hiện khi có người tích lũy XP.</p>;
  return <ol className="space-y-2">
    {users.map((user) => <li key={user.userId} className="flex items-center gap-3 rounded-sm border border-ink-700 px-3 py-2">
      <span className="w-8 text-center font-display text-ember-400">{user.rank}</span>
      <span className="min-w-0 flex-1 truncate">{user.displayName}</span>
      <span className="text-sm tabular-nums text-moss-400">{user.xp.toLocaleString("vi-VN")} XP</span>
    </li>)}
  </ol>;
}
