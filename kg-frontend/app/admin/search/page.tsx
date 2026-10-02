"use client";

import { useEffect, useState } from "react";
import { DatabaseIcon as Database, MagnifyingGlassIcon as MagnifyingGlass, ArrowsClockwiseIcon as ArrowsClockwise } from "@phosphor-icons/react";
import { RequireAuth, PageHeading } from "@/components/ui";
import { useLocale } from "@/components/locale";
import { apiRequest } from "@/lib/api-client";

type Mode = "POSTGRES" | "ELASTICSEARCH" | "AUTO";
type Settings = { mode: Mode; version: number; updatedAt: string; updatedBy: string | null; elasticsearchConfigured: boolean };
const needsElasticsearch = (mode: Mode) => mode !== "POSTGRES";
const options = [
  { mode: "POSTGRES" as const, name: "PostgreSQL", icon: Database, description: "Ổn định nhất, không phụ thuộc Elasticsearch." },
  { mode: "AUTO" as const, name: "Tự động", icon: ArrowsClockwise, description: "Ưu tiên ES, tự fallback PostgreSQL khi ES lỗi." },
  { mode: "ELASTICSEARCH" as const, name: "Elasticsearch", icon: MagnifyingGlass, description: "Dùng ranking của Elasticsearch." },
];

function SearchAdmin() {
  const { t, formatLocale } = useLocale();
  const [settings, setSettings] = useState<Settings | null>(null);
  const [mode, setMode] = useState<Mode>("POSTGRES");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [lifecycle, setLifecycle] = useState("");
  const [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    apiRequest<Settings>("/admin/search/settings", { signal: controller.signal }).then(next => { if (!controller.signal.aborted) { setSettings(next); setMode(next.mode); setError(""); } }).catch(reason => { if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "Không tải được cấu hình search"); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [reload]);
  async function runLifecycle(action: "status" | "start" | "stop") {
    if (busy) return;
    if (action === "stop" && !window.confirm(t("Tắt Elasticsearch? Search sẽ chuyển về PostgreSQL."))) return;
    setBusy(true); setError(""); setLifecycle("");
    try {
      if (action === "stop") {
        const next = await apiRequest<Settings>(`/admin/search/elasticsearch/stop`, { method: "POST" });
        setSettings(next); setMode(next.mode);
        setLifecycle("Elasticsearch đã dừng; search chuyển về PostgreSQL.");
      } else if (action === "start") {
        const result = await apiRequest<{ output: string }>(`/admin/search/elasticsearch/start`, { method: "POST" });
        setLifecycle(result.output || "Elasticsearch đã khởi động.");
        setReload(value => value + 1);
      } else {
        const result = await apiRequest<{ output: string }>(`/admin/search/elasticsearch/status`);
        setLifecycle(result.output || "Đã lấy trạng thái Elasticsearch.");
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Không điều khiển được Elasticsearch");
    } finally { setBusy(false); }
  }
  async function save() {
    if (!settings || busy) return;
    if (needsElasticsearch(mode) && !settings.elasticsearchConfigured) { setError("Elasticsearch chưa được bật ở backend, không thể chọn mode này."); return; }
    if (!window.confirm(t("Chuyển công cụ tìm kiếm sang {mode}?", { mode }))) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const next = await apiRequest<Settings>("/admin/search/settings", { method: "PUT", body: { mode, version: settings.version } });
      setSettings(next); setMode(next.mode); setMessage("Đã cập nhật search backend.");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Không lưu được cấu hình search"); }
    finally { setBusy(false); }
  }
  return <div>
    <PageHeading title="Cấu hình tìm kiếm" description="Chuyển backend runtime mà không cần restart ứng dụng." />
    {error && <p role="alert" className="mb-6">{t(error)}<button type="button" onClick={() => setReload(value => value + 1)} className="ml-4 underline">{t("Thử lại")}</button></p>}
    {message && <p role="status" className="mb-6">{t(message)}</p>}
    {lifecycle && <p role="status" className="mb-6 whitespace-pre-wrap break-words">{t(lifecycle)}</p>}
    {loading && <p role="status">{t("Đang tải cấu hình…")}</p>}
    {settings && <div className="grid items-start gap-7 xl:grid-cols-[minmax(0,1fr)_280px]">
      <section className="kg-panel">
        <fieldset disabled={busy || loading}><legend className="mb-5 text-lg font-semibold text-strong">{t("Công cụ tìm kiếm hiện tại")}</legend><div className="space-y-3">{options.map(({ mode: candidate, name, icon: Icon, description }) => {
          const unavailable = needsElasticsearch(candidate) && !settings.elasticsearchConfigured;
          return <label key={candidate} className={`flex items-start gap-4 rounded-xl border p-5 ${unavailable ? "cursor-not-allowed opacity-60" : "cursor-pointer"} ${mode === candidate ? "border-accent bg-accent-soft" : "border-line bg-surface"}`}><input className="mt-1" type="radio" name="search-mode" value={candidate} checked={mode === candidate} disabled={unavailable} onChange={() => setMode(candidate)} /><Icon size={22} aria-hidden className="mt-0.5 shrink-0 text-accent" /><span><strong className="block text-base text-strong">{t(name)}</strong><span className="mt-2 block text-sm leading-relaxed text-body">{t(unavailable ? "ES chưa được bật ở capability/config." : description)}</span></span></label>;
        })}</div></fieldset>
        <div className="mt-6 flex flex-wrap items-center justify-between gap-4 border-t border-line pt-5"><span className="text-sm text-subtle">Elasticsearch: {t(settings.elasticsearchConfigured ? "Đã cấu hình" : "Chưa bật")}</span><button type="button" disabled={busy || loading || mode === settings.mode} onClick={() => void save()} className="kg-button">{t(busy ? "Đang lưu…" : "Lưu cấu hình")}</button></div>
        <div className="mt-4 flex flex-wrap gap-3 border-t border-line pt-5">
          <button type="button" disabled={busy || loading} onClick={() => void runLifecycle("status")} className="kg-secondary">{t("Trạng thái ES")}</button>
          <button type="button" disabled={busy || loading} onClick={() => void runLifecycle("start")} className="kg-secondary">{t("Bật ES")}</button>
          <button type="button" disabled={busy || loading} onClick={() => void runLifecycle("stop")} className="kg-secondary">{t("Tắt ES")}</button>
        </div>
      </section>
      <aside className="rounded-2xl bg-sand/60 p-6"><h2 className="text-lg">{t("Trước khi thay đổi")}</h2><p className="mt-3 text-sm leading-relaxed text-body">{t("Thay đổi sẽ áp dụng cho các truy vấn mới. Kiểm tra cấu hình trước khi lưu.")}</p><dl className="mt-6 space-y-4 text-sm"><div><dt className="text-subtle">{t("Phiên bản cấu hình")}</dt><dd className="mt-1 font-medium text-strong">{settings.version}</dd></div><div><dt className="text-subtle">{t("Cập nhật gần nhất")}</dt><dd className="mt-1 text-strong">{settings.updatedAt ? new Date(settings.updatedAt).toLocaleString(formatLocale) : t("Chưa có")}</dd></div></dl></aside>
    </div>}
  </div>;
}
export default function AdminSearchPage() { return <RequireAuth><SearchAdmin /></RequireAuth>; }
