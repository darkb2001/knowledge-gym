"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { LOCALE_KEY, localeTag, readLocale, translateText, type Locale } from "@/lib/i18n";

type Translator = (source: string | null | undefined, values?: Record<string, string | number>) => string;
type LocaleContextValue = { locale: Locale; formatLocale: string; setLocale: (locale: Locale) => void; t: Translator };
const LocaleContext = createContext<LocaleContextValue | null>(null);

export function LocaleProvider({ children }: { children: ReactNode }) {
  // Stable server/client initial render; persisted preference is restored after hydration.
  const [locale, updateLocale] = useState<Locale>("vi");
  useEffect(() => { updateLocale(readLocale(window.localStorage)); }, []);
  useEffect(() => { document.documentElement.lang = locale; }, [locale]);
  const setLocale = useCallback((next: Locale) => {
    updateLocale(next);
    try { window.localStorage.setItem(LOCALE_KEY, next); } catch { /* Private browsing still supports in-memory preference. */ }
  }, []);
  useEffect(() => {
    const sync = (event: StorageEvent) => {
      if (event.key === LOCALE_KEY || event.key === null) updateLocale(readLocale(window.localStorage));
    };
    window.addEventListener("storage", sync);
    return () => window.removeEventListener("storage", sync);
  }, []);
  const t = useCallback<Translator>((source, values) => translateText(source, locale, values), [locale]);
  const value = useMemo(() => ({ locale, formatLocale: localeTag(locale), setLocale, t }), [locale, setLocale, t]);
  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}

export function useLocale() {
  const context = useContext(LocaleContext);
  if (!context) throw new Error("useLocale must be used within LocaleProvider");
  return context;
}

export function LanguageSwitch() {
  const { locale, setLocale, t } = useLocale();
  return <div role="group" aria-label={t("Ngôn ngữ")} className="inline-flex shrink-0 rounded-lg border border-line bg-surface p-1">
    {(["vi", "en"] as const).map((language) => <button key={language} type="button" lang={language} aria-pressed={locale === language} aria-label={language === "vi" ? "Tiếng Việt" : "English"} onClick={() => setLocale(language)} className={`min-h-8 min-w-10 rounded-md px-2 text-xs font-semibold transition-colors ${locale === language ? "bg-accent text-on-accent" : "text-subtle hover:bg-muted"}`}>{language === "vi" ? "VI" : "EN"}</button>)}
  </div>;
}
