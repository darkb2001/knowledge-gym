"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { getAccessToken, RefreshUnreachableError } from "@/lib/api-client";
import { logout, readStoredUser, restoreSession } from "@/lib/auth";
import type { User } from "@/lib/types";

export function AppHeader({ user }: { user: User | null }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);

  async function onLogout() {
    setBusy(true);
    try {
      await logout();
      router.replace("/login");
    } finally {
      setBusy(false);
    }
  }

  return (
    <header className="border-b border-ink-700/80 bg-ink-900/70 backdrop-blur-md">
      <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
        <Link href="/questions" className="group flex items-baseline gap-2">
          <span className="font-display text-xl tracking-tight text-ink-50 transition group-hover:text-ember-400">
            Knowledge Gym
          </span>
          <span className="hidden text-xs uppercase tracking-[0.2em] text-ink-400 sm:inline">
            drill
          </span>
        </Link>
        <div className="flex items-center gap-3 text-sm">
          {user ? (
            <>
              <Link href="/dashboard" className="text-ink-200 transition hover:text-ember-300">Dashboard</Link>
              <Link href="/profile" className="text-ink-200 transition hover:text-ember-300">Hồ sơ</Link>
              <span className="hidden text-ink-200 sm:inline">{user.displayName}</span>
              <button
                type="button"
                onClick={onLogout}
                disabled={busy}
                className="rounded-sm border border-ink-600 px-3 py-1.5 text-ink-100 transition hover:border-ember-400 hover:text-ember-300 disabled:opacity-50"
              >
                Đăng xuất
              </button>
            </>
          ) : (
            <Link
              href="/login"
              className="rounded-sm bg-moss-500 px-3 py-1.5 font-medium text-ink-50 transition hover:bg-moss-400"
            >
              Đăng nhập
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}

/** Redirect to /login when session cannot be restored (no JWT + refresh cookie failed). */
export function RequireAuth({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [ready, setReady] = useState(false);
  const [user, setUser] = useState<User | null>(null);
  const [restoreError, setRestoreError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        let token = getAccessToken();
        if (!token) {
          const ok = await restoreSession();
          token = getAccessToken();
          // Only bounce if still no token — a sibling Strict Mode mount may have won the refresh.
          if (!ok && !token) {
            if (!cancelled) router.replace("/login");
            return;
          }
        }
        if (cancelled) return;
        setUser(readStoredUser());
        setReady(Boolean(getAccessToken()));
      } catch (err) {
        // Network blip on refresh must not hard-redirect to /login (m4b review MAJOR).
        if (cancelled) return;
        const detail =
          err instanceof RefreshUnreachableError
            ? err.message
            : "Không kết nối được máy chủ. Thử lại.";
        setRestoreError(detail);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [router]);

  if (restoreError) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-desk-grain px-4 text-ink-200">
        <p className="font-display text-lg text-ember-300" role="alert">
          {restoreError}
        </p>
        <button
          type="button"
          className="rounded-sm border border-ink-600 px-4 py-2 text-sm hover:border-ember-400"
          onClick={() => window.location.reload()}
        >
          Thử lại
        </button>
      </div>
    );
  }

  if (!ready) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-desk-grain text-ink-200">
        <p className="animate-soft-pulse font-display text-lg">Đang mở phòng tập…</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-desk-grain text-ink-100">
      <AppHeader user={user} />
      <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6">{children}</main>
    </div>
  );
}

export function AuthShell({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
}) {
  return (
    <div className="relative flex min-h-screen flex-col justify-center bg-desk-grain px-4 py-12 text-ink-100">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-0 h-64 bg-[radial-gradient(ellipse_at_top,_rgba(240,154,92,0.15),_transparent_60%)]"
      />
      <div className="relative mx-auto w-full max-w-md animate-fade-up">
        <p className="font-display text-3xl tracking-tight text-ink-50 sm:text-4xl">
          Knowledge Gym
        </p>
        <h1 className="mt-6 font-display text-2xl text-ember-300">{title}</h1>
        {subtitle ? <p className="mt-2 text-sm text-ink-400">{subtitle}</p> : null}
        <div className="mt-8">{children}</div>
      </div>
    </div>
  );
}

export function Field({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <label className="mb-4 block text-sm">
      <span className="mb-1.5 block text-ink-200">{label}</span>
      {children}
    </label>
  );
}

export const inputClass =
  "w-full rounded-sm border border-ink-600 bg-ink-900/80 px-3 py-2.5 text-ink-50 outline-none transition placeholder:text-ink-600 focus:border-moss-400 focus:ring-1 focus:ring-moss-400/40";

export const primaryBtnClass =
  "inline-flex w-full items-center justify-center rounded-sm bg-ember-500 px-4 py-2.5 font-medium text-ink-950 transition hover:bg-ember-400 disabled:cursor-not-allowed disabled:opacity-50";

export const ghostBtnClass =
  "inline-flex w-full items-center justify-center rounded-sm border border-ink-600 px-4 py-2.5 text-ink-100 transition hover:border-ink-400 hover:bg-ink-800/60 disabled:opacity-50";
