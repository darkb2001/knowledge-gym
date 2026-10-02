"use client";

import { useEffect, useState } from "react";
import { RequireAuth } from "@/components/ui";
import { apiRequest } from "@/lib/api-client";

type Mode = "POSTGRES" | "ELASTICSEARCH" | "AUTO";
type Settings = { mode: Mode; version: number; updatedAt: string; updatedBy: string | null; elasticsearchConfigured: boolean };

/**
 * Both ES modes need the cluster; AUTO only differs from POSTGRES by preferring it and falling
 * back on failure. Choosing AUTO while ES is disabled would silently behave exactly like POSTGRES,
 * so both are unavailable rather than just ELASTICSEARCH.
 */
const needsElasticsearch = (mode: Mode) => mode !== "POSTGRES";

function SearchAdmin() {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [mode, setMode] = useState<Mode>("POSTGRES");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [lifecycle, setLifecycle] = useState("");

  async function load() {
    try {
      const next = await apiRequest<Settings>("/admin/search/settings");
      setSettings(next); setMode(next.mode); setError("");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Không tải được cấu hình search"); }
  }
  useEffect(() => { void load(); }, []);

  async function runLifecycle(action: "status" | "start" | "stop") {
    if (busy) return;
    setBusy(true);
    setError("");
    setLifecycle("");
    try {
      if (action === "stop") {
        const next = await apiRequest<Settings>("/admin/search/elasticsearch/stop", { method: "POST" });
        setSettings(next);
        setMode(next.mode);
        setLifecycle("Elasticsearch đã dừng; search chuyển về PostgreSQL.");
      } else if (action === "start") {
        const result = await apiRequest<{ output: string }>("/admin/search/elasticsearch/start", { method: "POST" });
        setLifecycle(result.output || "Elasticsearch đã khởi động.");
      } else {
        const result = await apiRequest<{ output: string }>("/admin/search/elasticsearch/status");
        setLifecycle(result.output || "Đã lấy trạng thái Elasticsearch.");
      }
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Không điều khiển được Elasticsearch");
    } finally {
      setBusy(false);
    }
  }

  async function save() {
    if (!settings) return;
    if (needsElasticsearch(mode) && !settings.elasticsearchConfigured) {
      setError("Elasticsearch chưa được bật ở backend, không thể chọn mode này.");
      return;
    }
    if (!window.confirm(`Chuyển search sang ${mode}?`)) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const next = await apiRequest<Settings>("/admin/search/settings", {
        method: "PUT", body: { mode, version: settings.version },
      });
      setSettings(next); setMode(next.mode); setMessage("Đã cập nhật search backend.");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Không lưu được cấu hình search"); await load(); }
    finally { setBusy(false); }
  }

  return <main className="mx-auto max-w-3xl space-y-6 p-6">
    <header><p className="text-xs uppercase tracking-[0.2em] text-ember-400">Quản trị hạ tầng</p><h1 className="font-display text-3xl text-ink-50">Search backend</h1><p className="mt-2 text-sm text-ink-400">Chuyển backend runtime mà không cần restart ứng dụng.</p></header>
    {error && <p role="alert" className="rounded-sm border border-ember-500/50 p-3 text-ember-300">{error}</p>}
    {message && <p role="status" className="rounded-sm border border-moss-600 p-3 text-moss-300">{message}</p>}
    {lifecycle && <p role="status" className="whitespace-pre-wrap rounded-sm border border-ink-600 p-3 text-ink-200">{lifecycle}</p>}
    {settings && <section className="space-y-5 rounded-sm border border-ink-700 bg-ink-900/50 p-5">
      <div className="grid gap-3">
        {(["POSTGRES", "AUTO", "ELASTICSEARCH"] as Mode[]).map((candidate) => <label key={candidate} className="flex cursor-pointer items-start gap-3 rounded-sm border border-ink-700 p-4">
          <input type="radio" name="search-mode" checked={mode === candidate} disabled={needsElasticsearch(candidate) && !settings.elasticsearchConfigured} onChange={() => setMode(candidate)} />
          <span><strong className="block text-ink-100">{candidate}</strong><small className="text-ink-400">{candidate === "POSTGRES" ? "Ổn định nhất, không phụ thuộc Elasticsearch." : candidate === "AUTO" ? "Ưu tiên ES, tự fallback PostgreSQL khi ES lỗi." : settings.elasticsearchConfigured ? "Dùng ranking của Elasticsearch." : "ES chưa được bật ở capability/config."}</small></span>
        </label>)}
      </div>
      <div className="flex items-center justify-between border-t border-ink-700 pt-4 text-xs text-ink-400"><span>ES capability: {settings.elasticsearchConfigured ? "configured" : "disabled"}</span><button disabled={busy || mode === settings.mode} onClick={() => void save()} className="rounded-sm bg-moss-600 px-4 py-2 text-sm text-white disabled:opacity-50">{busy ? "Đang lưu…" : "Lưu mode"}</button></div>
      <div className="flex flex-wrap gap-3 border-t border-ink-700 pt-4">
        <button type="button" disabled={busy} onClick={() => void runLifecycle("status")} className="rounded-sm border border-ink-600 px-4 py-2 text-sm text-ink-100 hover:border-ember-400 disabled:opacity-50">Trạng thái ES</button>
        <button type="button" disabled={busy} onClick={() => void runLifecycle("start")} className="rounded-sm border border-ink-600 px-4 py-2 text-sm text-ink-100 hover:border-ember-400 disabled:opacity-50">Bật ES</button>
        <button type="button" disabled={busy} onClick={() => void runLifecycle("stop")} className="rounded-sm border border-ink-600 px-4 py-2 text-sm text-ink-100 hover:border-ember-400 disabled:opacity-50">Tắt ES</button>
      </div>
    </section>}
  </main>;
}

export default function AdminSearchPage() { return <RequireAuth><SearchAdmin /></RequireAuth>; }
