"use client";

import { useEffect, useRef, useState } from "react";
import { RequireAuth, Field, inputClass, primaryBtnClass, ghostBtnClass } from "@/components/ui";
import { apiRequest } from "@/lib/api-client";
import { uploadAvatar } from "@/lib/storage";
import type { UserStats } from "@/lib/dashboard";

type Profile = {
  id: string;
  email: string;
  displayName: string;
  avatarUrl?: string | null;
  role: string;
  authProvider: string;
  stats: UserStats;
};

function ProfilePage() {
  const fileRef = useRef<HTMLInputElement>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [displayName, setDisplayName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    apiRequest<Profile>("/users/me", { signal: controller.signal })
      .then((next) => {
        if (!controller.signal.aborted) {
          setProfile(next);
          setDisplayName(next.displayName);
          setError("");
        }
      })
      .catch((reason) => {
        if (!controller.signal.aborted) {
          setError(reason instanceof Error ? reason.message : "Không tải được hồ sơ");
        }
      });
    return () => controller.abort();
  }, []);

  async function saveProfile(nextAvatarUrl?: string) {
    if (!profile || busy) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const updated = await apiRequest<Profile>("/users/me", {
        method: "PATCH",
        body: {
          displayName,
          avatarUrl: nextAvatarUrl ?? profile.avatarUrl ?? null,
        },
      });
      setProfile(updated);
      setDisplayName(updated.displayName);
      setMessage("Đã lưu hồ sơ.");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Không lưu được hồ sơ");
    } finally {
      setBusy(false);
    }
  }

  async function onPickAvatar(file: File | undefined) {
    if (!file) return;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const publicUrl = await uploadAvatar(file);
      await saveProfile(publicUrl);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Không tải ảnh đại diện");
      setBusy(false);
    }
  }

  return (
    <div className="space-y-6">
      <header>
        <p className="text-xs uppercase tracking-[0.2em] text-ember-400">Tài khoản</p>
        <h1 className="font-display text-3xl text-ink-50">Hồ sơ của bạn</h1>
        <p className="mt-2 text-sm text-ink-400">Cập nhật tên hiển thị và ảnh đại diện.</p>
      </header>
      {error && <p role="alert" className="rounded-sm border border-ember-500/50 p-3 text-ember-300">{error}</p>}
      {message && <p role="status" className="rounded-sm border border-moss-600 p-3 text-moss-300">{message}</p>}
      {profile && (
        <section className="space-y-6 rounded-sm border border-ink-700 bg-ink-900/50 p-5">
          <div className="flex flex-wrap items-center gap-4">
            {profile.avatarUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={profile.avatarUrl} alt="" className="h-20 w-20 rounded-full object-cover" />
            ) : (
              <span className="flex h-20 w-20 items-center justify-center rounded-full bg-ink-800 text-ink-400">
                {profile.displayName.slice(0, 1).toUpperCase()}
              </span>
            )}
            <div>
              <p className="text-sm text-ink-300">{profile.email}</p>
              <p className="text-xs text-ink-500">{profile.authProvider}</p>
              <button
                type="button"
                disabled={busy}
                onClick={() => fileRef.current?.click()}
                className={`${ghostBtnClass} mt-3 w-auto px-4 py-2 text-sm`}
              >
                {busy ? "Đang tải…" : "Đổi ảnh đại diện"}
              </button>
              <input
                ref={fileRef}
                type="file"
                accept="image/*"
                hidden
                onChange={(event) => void onPickAvatar(event.target.files?.[0])}
              />
            </div>
          </div>
          <Field label="Tên hiển thị">
            <input
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              className={inputClass}
              maxLength={80}
            />
          </Field>
          <div className="flex flex-wrap items-center justify-between gap-4 border-t border-ink-700 pt-4">
            <p className="text-sm text-ink-400">
              XP: {profile.stats.xp} · Streak: {profile.stats.currentStreak}
            </p>
            <button
              type="button"
              disabled={busy}
              onClick={() => void saveProfile()}
              className={`${primaryBtnClass} w-auto px-5`}
            >
              {busy ? "Đang lưu…" : "Lưu hồ sơ"}
            </button>
          </div>
        </section>
      )}
    </div>
  );
}

export default function ProfileRoute() {
  return (
    <RequireAuth>
      <ProfilePage />
    </RequireAuth>
  );
}
