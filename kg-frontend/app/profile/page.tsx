"use client";

import { useEffect, useRef, useState } from "react";
import { RequireAuth, Field, PageHeading } from "@/components/ui";
import { useLocale } from "@/components/locale";
import { apiRequest } from "@/lib/api-client";
import { uploadAvatar } from "@/lib/storage";
import { readStoredUser, storeUser } from "@/lib/auth";
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
  const { t, formatLocale } = useLocale();
  const fileRef = useRef<HTMLInputElement>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [displayName, setDisplayName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError("");
    apiRequest<Profile>("/users/me", { signal: controller.signal })
      .then(next => {
        if (!controller.signal.aborted) { setProfile(next); setDisplayName(next.displayName); }
      })
      .catch(reason => {
        if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "Không tải được hồ sơ");
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [attempt]);

  // Both normal saves and uploads share persistence, not the busy guard.
  // Calling the guarded save while an upload is busy previously skipped PATCH entirely.
  async function persistProfile(avatarUrl: string | null) {
    const updated = await apiRequest<Profile>("/users/me", {
      method: "PATCH", body: { displayName: displayName.trim(), avatarUrl },
    });
    setProfile(updated); setDisplayName(updated.displayName); setMessage("Đã lưu hồ sơ.");
    const stored = readStoredUser();
    if (stored) {
      storeUser({ ...stored, displayName: updated.displayName });
      window.dispatchEvent(new Event("kg:user-changed"));
    }
  }

  async function saveProfile() {
    if (!profile || busy || !displayName.trim()) return;
    setBusy(true); setError(""); setMessage("");
    try { await persistProfile(profile.avatarUrl ?? null); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Không lưu được hồ sơ"); }
    finally { setBusy(false); }
  }

  async function onPickAvatar(file: File | undefined) {
    if (!file || !profile || busy) return;
    if (!displayName.trim()) { setError("Vui lòng nhập tên hiển thị trước khi lưu."); return; }
    setBusy(true); setError(""); setMessage("");
    try {
      const publicUrl = await uploadAvatar(file);
      await persistProfile(publicUrl);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Không tải ảnh đại diện"); }
    finally { setBusy(false); }
  }

  return <div className="max-w-3xl">
    <PageHeading title="Hồ sơ của bạn" description="Cập nhật tên hiển thị và ảnh đại diện." />
    {error && <p role="alert" className="mb-6">{t(error)}{!profile && <button type="button" onClick={() => setAttempt(value => value + 1)} className="ml-4 underline">{t("Thử lại")}</button>}</p>}
    {message && <p role="status" className="mb-6">{t(message)}</p>}
    {loading && <p role="status">{t("Đang tải hồ sơ…")}</p>}
    {profile && <section className="kg-panel space-y-7">
      <div className="flex flex-wrap items-center gap-5">
        {profile.avatarUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={profile.avatarUrl} alt={`${t("Ảnh đại diện")}: ${profile.displayName}`} className="h-20 w-20 rounded-full bg-sage object-cover" />
        ) : <span aria-hidden="true" className="flex h-20 w-20 items-center justify-center rounded-full bg-sage text-3xl font-medium text-strong">{profile.displayName.slice(0, 1).toUpperCase()}</span>}
        <div className="min-w-0 flex-1"><p className="break-all font-medium text-strong">{profile.email}</p><p className="mt-1 text-xs text-subtle">{profile.authProvider}</p><button type="button" disabled={busy || loading} onClick={() => fileRef.current?.click()} className="kg-secondary mt-3">{t(busy ? "Đang tải…" : "Đổi ảnh đại diện")}</button><input ref={fileRef} aria-label={t("Ảnh đại diện")} type="file" accept="image/*" hidden onChange={event => { const file = event.target.files?.[0]; event.target.value = ""; void onPickAvatar(file); }} /></div>
      </div>
      <form onSubmit={event => { event.preventDefault(); void saveProfile(); }}>
        <Field label="Tên hiển thị"><input required value={displayName} disabled={busy || loading} onChange={event => setDisplayName(event.target.value)} className="kg-field" maxLength={80} autoComplete="nickname" /></Field>
        <div className="flex flex-wrap items-center justify-between gap-4 border-t border-line pt-5"><p className="text-sm tabular-nums text-body">{t("Kinh nghiệm tích lũy")}: {profile.stats.xp.toLocaleString(formatLocale)} XP<span className="mx-2" aria-hidden>·</span>{t("Ngày liên tiếp")}: {profile.stats.currentStreak.toLocaleString(formatLocale)}</p><button type="submit" disabled={busy || loading || !displayName.trim() || displayName.trim() === profile.displayName} className="kg-button">{t(busy ? "Đang lưu…" : "Lưu hồ sơ")}</button></div>
      </form>
    </section>}
  </div>;
}

export default function ProfileRoute() { return <RequireAuth><ProfilePage /></RequireAuth>; }
