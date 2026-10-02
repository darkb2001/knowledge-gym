"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { ArrowRightIcon as ArrowRight, BookOpenIcon as BookOpen, ChartLineIcon as ChartLine, CirclesThreeIcon as CirclesThree, CompassIcon as Compass, GearSixIcon as GearSix, NotebookIcon as Notebook, SignOutIcon as SignOut, StackIcon as Stack, TextAlignLeftIcon as TextAlignLeft, UserCircleIcon as UserCircle, UsersThreeIcon as UsersThree } from "@phosphor-icons/react";
import { getAccessToken, RefreshUnreachableError } from "@/lib/api-client";
import { logout, readStoredUser, restoreSession } from "@/lib/auth";
import { LanguageSwitch, useLocale } from "@/components/locale";
import type { User } from "@/lib/types";

const navigation = [
  { href: "/learn", label: "Chọn chủ đề", icon: Compass },
  { href: "/questions", label: "Thư viện câu hỏi", icon: BookOpen },
  { href: "/mock-interview", label: "Luyện phỏng vấn", icon: UsersThree },
  { href: "/dashboard", label: "Tiến độ học tập", icon: ChartLine },
  { href: "/profile", label: "Hồ sơ", icon: UserCircle },
  { href: "/notes", label: "Ghi chú", icon: Notebook },
  { href: "/mindmap", label: "Sơ đồ kiến thức", icon: CirclesThree },
  { href: "/blog", label: "Bài viết", icon: TextAlignLeft },
];

function Brand() {
  return <Link href="/learn" className="inline-flex items-center gap-3 text-strong">
    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-accent text-on-accent"><Stack size={24} weight="bold" aria-hidden /></span>
    <span className="text-[17px] font-semibold leading-tight tracking-[-0.035em]">Knowledge<br />Gym<span className="text-accent">.</span></span>
  </Link>;
}

function Navigation({ user, onNavigate }: { user: User | null; onNavigate?: () => void }) {
  const pathname = usePathname();
  const { t } = useLocale();
  const admin = user?.role === "ADMIN" || user?.role === "ROLE_ADMIN";
  const links = (items: typeof navigation) => items.map(({ href, label, icon: Icon }) => {
    const active = pathname === href || pathname.startsWith(href + "/");
    return <Link key={href} href={href} onClick={onNavigate} aria-current={active ? "page" : undefined} className={`flex min-h-11 items-center gap-3 rounded-lg px-3.5 py-2.5 text-[13px] font-medium transition-colors ${active ? "bg-sage text-strong" : "text-body hover:bg-surface/60 hover:text-strong"}`}><Icon size={19} weight={active ? "fill" : "regular"} aria-hidden /><span>{t(label)}</span></Link>;
  });
  return <nav aria-label={t("Điều hướng chính")} className="space-y-1">
    {links(navigation)}
    {admin && <div className="mt-7 border-t border-line pt-5"><p className="mb-2 px-3.5 text-xs font-medium text-subtle">{t("Công cụ quản trị")}</p>{links([
      { href: "/admin/writer", label: "Quản trị nội dung", icon: TextAlignLeft },
      { href: "/admin/search", label: "Quản trị tìm kiếm", icon: GearSix },
    ])}</div>}
  </nav>;
}

export function AppHeader({ user }: { user: User | null }) {
  const router = useRouter();
  const { t } = useLocale();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function onLogout() {
    setBusy(true);
    try { await logout(); } catch { setError(t("Không kết nối được máy chủ. Thử lại.")); }
    finally { setBusy(false); router.replace("/login"); }
  }
  return <header className="border-b border-line/70 bg-canvas">
    <div className="flex min-h-[76px] items-center justify-between gap-3 px-5 sm:px-8 xl:px-12">
      <div className="lg:hidden"><Brand /></div>
      <p className="hidden text-sm text-subtle lg:block">{t("Không gian học của bạn")}</p>
      <div className="flex items-center gap-3 sm:gap-5">
        <LanguageSwitch />
        {user ? <><span className="hidden max-w-40 truncate text-sm font-medium text-strong sm:inline">{user.displayName}</span><button type="button" onClick={onLogout} disabled={busy} aria-label={t("Đăng xuất")} title={t("Đăng xuất")} className="flex min-h-11 min-w-11 items-center justify-center rounded-lg text-subtle hover:bg-muted disabled:opacity-50"><SignOut size={20} aria-hidden /></button></> : <Link href="/login" className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-lg text-accent hover:bg-muted" aria-label={t("Đăng nhập")}><UserCircle size={21} className="sm:hidden" aria-hidden /><span className="hidden text-sm font-medium sm:inline">{t("Đăng nhập")}</span></Link>}
      </div>
    </div>
    {error && <p role="alert" className="px-5 pb-3 text-sm text-danger">{error}</p>}
  </header>;
}

export function AppFrame({ user, children }: { user: User | null; children: ReactNode }) {
  const { t } = useLocale();
  const [menuOpen, setMenuOpen] = useState(false);
  const pathname = usePathname();
  const [currentUser, setCurrentUser] = useState(user);
  useEffect(() => {
    setCurrentUser(user);
    const update = () => setCurrentUser(readStoredUser());
    window.addEventListener("kg:user-changed", update);
    return () => window.removeEventListener("kg:user-changed", update);
  }, [user]);
  useEffect(() => { setMenuOpen(false); }, [pathname]);
  return <div className="kg-shell lg:grid lg:grid-cols-[232px_minmax(0,1fr)]">
    <a href="#main-content" className="fixed left-5 top-3 z-50 -translate-y-24 rounded-lg bg-accent px-4 py-3 text-on-accent focus:translate-y-0">{t("Đi đến nội dung")}</a>
    <aside className="sticky top-0 hidden h-[100dvh] flex-col overflow-y-auto border-r border-line/70 bg-sand/60 px-4 py-7 lg:flex">
      <div className="mb-10 px-3"><Brand /></div>
      <Navigation user={currentUser} />
      <div className="mt-auto px-3 pt-8"><p className="text-sm font-medium leading-relaxed text-strong">{t("Chọn điều muốn hiểu. Luyện cho đến khi nhớ.")}</p><p className="mt-3 text-xs leading-relaxed text-subtle">{t("Nội dung học giữ nguyên ngôn ngữ gốc.")}</p></div>
    </aside>
    <div className="min-w-0">
      <AppHeader user={currentUser} />
      <div className="border-b border-line/70 px-5 py-2 lg:hidden"><button type="button" aria-expanded={menuOpen} aria-controls="mobile-navigation" onClick={() => setMenuOpen(open => !open)} className="flex min-h-11 items-center gap-2 text-sm font-medium text-strong"><TextAlignLeft size={20} aria-hidden />{t(menuOpen ? "Đóng điều hướng" : "Mở điều hướng")}</button>{menuOpen && <div id="mobile-navigation" className="pb-3"><Navigation user={currentUser} onNavigate={() => setMenuOpen(false)} /></div>}</div>
      <main id="main-content" tabIndex={-1} className="kg-main outline-none">{children}</main>
    </div>
  </div>;
}

/** Public reading routes remain public; no auth bypass or refresh request is introduced. */
export function PublicShell({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  useEffect(() => { setUser(readStoredUser()); }, []);
  return <AppFrame user={user}>{children}</AppFrame>;
}

/** Preserve refresh/session restoration and its network-error recovery. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { t } = useLocale();
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
          if (!ok && !token) { if (!cancelled) router.replace("/login"); return; }
        }
        if (cancelled) return;
        setUser(readStoredUser()); setReady(Boolean(getAccessToken()));
      } catch (err) {
        if (cancelled) return;
        setRestoreError(err instanceof RefreshUnreachableError ? "Không kết nối được máy chủ để làm mới phiên" : "Không kết nối được máy chủ. Thử lại.");
      }
    })();
    return () => { cancelled = true; };
  }, [router]);
  if (restoreError) return <main className="flex min-h-[100dvh] flex-col items-center justify-center gap-5 bg-canvas px-5"><p className="max-w-lg text-center text-danger" role="alert">{t(restoreError)}</p><button type="button" className="kg-secondary" onClick={() => window.location.reload()}>{t("Thử lại")}</button><Link href="/login" className="inline-flex min-h-11 items-center text-accent underline">{t("← Quay lại đăng nhập")}</Link><LanguageSwitch /></main>;
  if (!ready) return <main className="flex min-h-[100dvh] items-center justify-center bg-canvas text-subtle"><p role="status">{t("Đang mở phòng tập…")}</p></main>;
  return <AppFrame user={user}>{children}</AppFrame>;
}

export function AuthShell({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  const { t } = useLocale();
  return <div className="min-h-[100dvh] bg-canvas lg:grid lg:grid-cols-[1fr_1.1fr]">
    <aside className="hidden flex-col justify-between border-r border-line/70 bg-sand/55 px-12 py-10 lg:flex xl:px-20">
      <Brand />
      <div className="py-16"><h2 className="max-w-md text-5xl font-medium leading-[1.15] tracking-[-0.035em]">{t("Học theo cách của bạn.")}</h2><p className="mt-7 max-w-sm text-lg leading-relaxed text-body">{t("Một nơi để ôn kiến thức, thử sức và lưu lại những điều bạn học được.")}</p><div className="mt-12 flex items-center gap-3 text-sm text-accent"><BookOpen size={21} aria-hidden /><span>{t("Đọc một chút. Hiểu thêm một chút.")}</span></div></div>
      <p className="text-sm text-subtle">Knowledge Gym</p>
    </aside>
    <main className="flex flex-col px-5 py-6 sm:px-10 sm:py-9">
      <div className="flex items-center justify-between gap-4"><div className="lg:hidden"><Brand /></div><div className="ml-auto"><LanguageSwitch /></div></div>
      <div className="mx-auto flex w-full max-w-[420px] flex-1 flex-col justify-center py-14"><h1 className="text-3xl sm:text-4xl">{t(title)}</h1>{subtitle && <p className="mt-3 text-sm leading-relaxed text-subtle">{t(subtitle)}</p>}<div className="mt-8">{children}</div></div>
    </main>
  </div>;
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  const { t } = useLocale();
  return <label className="mb-5 block text-sm font-medium text-strong"><span className="mb-2 block">{t(label)}</span>{children}</label>;
}

export function PageHeading({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
  const { t } = useLocale();
  return <header className="mb-8 flex flex-wrap items-start justify-between gap-5"><div><h1 className="kg-page-heading">{t(title)}</h1>{description && <p className="kg-intro">{t(description)}</p>}</div>{action}</header>;
}

export function ContentLanguageNotice() {
  const { t } = useLocale();
  return <p className="mt-5 text-xs text-subtle">{t("Nội dung học giữ nguyên ngôn ngữ gốc.")}</p>;
}

export const inputClass = "kg-field";
export const primaryBtnClass = "kg-button w-full";
export const ghostBtnClass = "kg-secondary w-full";
export { ArrowRight };
