"use client";
import { useLocale } from "@/components/locale";

import { useCallback, useEffect, useState } from "react";
import DOMPurify from "isomorphic-dompurify";
import { apiRequest } from "@/lib/api-client";
import { RequireAuth, PageHeading, ContentLanguageNotice } from "@/components/ui";

type Settings = { enabled: boolean; localTime: string; timezone: string; dailyLimit: number; policy: "MANUAL_REVIEW" | "AUTO_PUBLISH_QUALIFIED"; qualityThreshold: number; lastScheduledDate: string | null; lastRunAt: string | null };
type WriterRun = { id: string; status: string; attempts: number; errorMessage: string | null };
type WriterStats = { pendingReview: number; generatedToday: number; tokensToday: number; costToday: number; costThisMonth: number; failedJobs: number };
type BlogPost = { id: string; title: string; slug: string; body: string; excerpt: string | null; status: string; createdAt: string; tags: string[] };
type Revision = { id: string; version: number; title: string; body: string; excerpt: string | null; seoTitle: string | null; seoDescription: string | null; seoKeywords: string[]; instruction: string | null; sourceIds: string[]; model: string | null; qualityScore: number | null; tokensUsed: number | null; costUsd: number | null; createdAt: string };
type Draft = { title: string; body: string; excerpt: string };

const initial: Settings = { enabled: false, localTime: "06:00:00", timezone: "Asia/Jakarta", dailyLimit: 1, policy: "MANUAL_REVIEW", qualityThreshold: 85, lastScheduledDate: null, lastRunAt: null };

function WriterAdmin() {
  const { t, formatLocale } = useLocale();
  const [settings, setSettings] = useState<Settings>(initial);
  const [loaded, setLoaded] = useState(false);
  const [configured, setConfigured] = useState(false);
  const [writerEnabled, setWriterEnabled] = useState(false);
  const [nextRunAt, setNextRunAt] = useState<string | null>(null);
  const [run, setRun] = useState<WriterRun | null>(null);
  const [posts, setPosts] = useState<BlogPost[]>([]);
  const [stats, setStats] = useState<WriterStats | null>(null);
  const [monthlyAlert, setMonthlyAlert] = useState(10);
  const [selected, setSelected] = useState<BlogPost | null>(null);
  const [draft, setDraft] = useState<Draft>({ title: "", body: "", excerpt: "" });
  const [revisions, setRevisions] = useState<Revision[]>([]);
  const [compareRevision, setCompareRevision] = useState<Revision | null>(null);
  const [instruction, setInstruction] = useState("");
  const [topic, setTopic] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [settingsResponse, queue, usageResponse] = await Promise.all([
        apiRequest<{ settings: Settings; apiKeyConfigured: boolean; writerEnabled: boolean; nextRunAt: string | null }>("/admin/blog/writer/settings"),
        apiRequest<BlogPost[]>("/admin/blog/writer/review"),
        apiRequest<{ stats: WriterStats; monthlyCostAlertUsd: number }>("/admin/blog/writer/stats"),
      ]);
      setSettings(settingsResponse.settings);
      setConfigured(settingsResponse.apiKeyConfigured);
      setWriterEnabled(settingsResponse.writerEnabled);
      setNextRunAt(settingsResponse.nextRunAt);
      setPosts(queue); setLoaded(true);
      setStats(usageResponse.stats);setMonthlyAlert(usageResponse.monthlyCostAlertUsd);
      if (selected?.id) {
        const current = queue.find((item) => item.id === selected.id);
        if (current) setSelected((previous) => previous && (previous.title !== current.title || previous.body !== current.body || previous.status !== current.status) ? current : previous);
      }
      setError("");
    } catch (e) { setError(e instanceof Error ? e.message : "Không tải được AI Writer admin"); }
  }, [selected?.id]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => { const timer = window.setInterval(() => void load(), 12000); return () => window.clearInterval(timer); }, [load]);
  const activeRunId=run?.id;
  const activeRunStatus=run?.status;
  useEffect(() => {
    if (!activeRunId || !activeRunStatus || ["DONE", "FAILED"].includes(activeRunStatus)) return;
    const timer = window.setInterval(() => {
      void apiRequest<WriterRun>(`/admin/blog/writer/runs/${activeRunId}`).then((next) => setRun((current) => current && (current.status !== next.status || current.errorMessage !== next.errorMessage || current.attempts !== next.attempts) ? next : current)).catch(() => {});
    }, 4000);
    return () => window.clearInterval(timer);
  }, [activeRunId,activeRunStatus]);

  async function choose(post: BlogPost) {
    setSelected(post); setDraft({ title: post.title, body: post.body, excerpt: post.excerpt ?? "" });
    try { setRevisions(await apiRequest<Revision[]>(`/admin/blog/writer/posts/${post.id}/revisions`)); }
    catch (e) { setError(e instanceof Error ? e.message : "Không tải được lịch sử phiên bản"); }
  }
  async function updateSettings() {
    setBusy(true); setError("");
    try {
      const result = await apiRequest<{ settings: Settings; apiKeyConfigured: boolean; writerEnabled: boolean; nextRunAt: string | null }>("/admin/blog/writer/settings", {
        method: "PUT", body: { enabled: settings.enabled, localTime: settings.localTime, timezone: settings.timezone, dailyLimit: settings.dailyLimit, publishPolicy: settings.policy, qualityThreshold: settings.qualityThreshold },
      });
      setSettings(result.settings); setConfigured(result.apiKeyConfigured); setWriterEnabled(result.writerEnabled); setNextRunAt(result.nextRunAt); setMessage("Đã lưu lịch và chính sách xuất bản.");
    } catch (e) { setError(e instanceof Error ? e.message : "Không lưu được cấu hình"); }
    finally { setBusy(false); }
  }
  async function generateNow() {
    setBusy(true); setError("");
    try {
      const queued = await apiRequest<WriterRun>("/admin/blog/writer/runs", { method: "POST", body: { topic: topic.trim() || null, requestId: crypto.randomUUID() } });
      setRun(queued); setMessage("Đã đưa lượt tạo vào hàng đợi. Hàng chờ duyệt tự cập nhật."); setTopic("");
    } catch (e) { setError(e instanceof Error ? e.message : "Không thể tạo bài"); }
    finally { setBusy(false); }
  }
  async function saveEdit() {
    if (!selected) return;
    setBusy(true);
    try {
      await apiRequest(`/admin/blog/writer/posts/${selected.id}`, { method: "PUT", body: draft });
      setMessage("Đã lưu một phiên bản chỉnh sửa mới."); await choose({ ...selected, ...draft }); await load();
    } catch (e) { setError(e instanceof Error ? e.message : "Không lưu được chỉnh sửa"); }
    finally { setBusy(false); }
  }
  async function revise() {
    if (!selected || !instruction.trim()) return;
    setBusy(true);
    try {
      await apiRequest(`/admin/blog/writer/posts/${selected.id}/revise`, { method: "POST", body: { instruction } });
      setInstruction(""); setMessage("AI đã tạo phiên bản mới; vui lòng xem preview trước khi xuất bản."); await load();
      const latest = await apiRequest<Revision[]>(`/admin/blog/writer/posts/${selected.id}/revisions`); setRevisions(latest);
      const post = await apiRequest<BlogPost>(`/admin/blog/writer/posts/${selected.id}`); setSelected(post); setDraft({ title: post.title, body: post.body, excerpt: post.excerpt ?? "" });
    } catch (e) { setError(e instanceof Error ? e.message : "AI không thể chỉnh sửa bài"); }
    finally { setBusy(false); }
  }
  async function action(path: string, success: string) {
    if (!selected || busy) return;
    if (!window.confirm(t(path.endsWith("/publish") ? "Xuất bản bài viết này?" : "Từ chối bài viết này?"))) return;
    setBusy(true);
    try { await apiRequest(path, { method: "POST" }); setMessage(success); setSelected(null); setRevisions([]); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : "Thao tác thất bại"); }
    finally { setBusy(false); }
  }
  async function restore(version: number) {
    if (!selected) return;
    try {
      await apiRequest(`/admin/blog/writer/posts/${selected.id}/revisions/${version}/restore`, { method: "POST" });
      const post=await apiRequest<BlogPost>(`/admin/blog/writer/posts/${selected.id}`);await choose(post);setMessage("Đã khôi phục nội dung thành phiên bản mới.");
    } catch (e) { setError(e instanceof Error ? e.message : "Không khôi phục được phiên bản"); }
  }

  if (!loaded) return <div><PageHeading title="Quản trị nội dung" description="Theo dõi lịch tạo bài, duyệt nội dung và quản lý các phiên bản." />{error ? <p role="alert">{t(error)}<button type="button" onClick={() => void load()} className="ml-4 underline">{t("Thử lại")}</button></p> : <p role="status">{t("Đang tải cấu hình…")}</p>}</div>;
  return <div className="space-y-7">
    <PageHeading title="Quản trị nội dung" description="Theo dõi lịch tạo bài, duyệt nội dung và quản lý các phiên bản." />
    {!configured && <p className="kg-notice text-warning">{t("OPENAI_API_KEY chưa được cấu hình ở backend. Không gửi khóa bí mật từ trình duyệt.")}</p>}
    {configured && !writerEnabled && <p className="kg-notice text-warning">{t("Worker AI writer đang tắt (`app.blog.writer.enabled=false`). Lượt Generate now sẽ bị từ chối cho đến khi bật worker.")}</p>}
    {error && <p role="alert">{t(error)}</p>}
    {message && <p role="status">{t(message)}</p>}
    {stats && <dl className="grid grid-cols-2 gap-x-6 gap-y-5 border-y border-line/70 py-5 sm:grid-cols-3 xl:grid-cols-6">{[
      ["Chờ duyệt", stats.pendingReview], ["Lượt AI hôm nay", stats.generatedToday], ["Tokens hôm nay", stats.tokensToday.toLocaleString(formatLocale)], ["Chi phí hôm nay", `$${stats.costToday.toFixed(4)}`], ["Chi phí tháng này", `$${stats.costThisMonth.toFixed(2)}`], ["Run lỗi", stats.failedJobs],
    ].map(([label, value]) => <div key={String(label)}><dt className="text-xs text-subtle">{t(String(label))}</dt><dd className="mt-1 text-lg font-semibold tabular-nums text-strong">{value}</dd></div>)}</dl>}
    {stats && stats.costThisMonth > monthlyAlert && <p role="alert">{t("Chi phí AI tháng này đã vượt ngưỡng cảnh báo $")}{monthlyAlert.toFixed(2)}.</p>}
    <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.25fr)_minmax(0,0.8fr)]">
      <details className="kg-panel">
        <summary className="text-lg text-strong">{t("Lịch và công cụ tạo bài")}</summary>
        <div className="mt-5 space-y-5">
        <h2 className="text-xl">{t("Lịch và chính sách xuất bản")}</h2>
        <label className="flex items-center gap-3 text-sm"><input type="checkbox" checked={settings.enabled} onChange={event => setSettings({ ...settings, enabled: event.target.checked })} />{t("Bật tạo bài tự động mỗi ngày")}</label>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm">{t("Giờ địa phương")}<input className="kg-field" type="time" value={settings.localTime.slice(0, 5)} onChange={event => setSettings({ ...settings, localTime: event.target.value + ":00" })} /></label>
          <label className="block text-sm">{t("Múi giờ IANA")}<input className="kg-field" value={settings.timezone} onChange={event => setSettings({ ...settings, timezone: event.target.value })} placeholder="Asia/Jakarta" /></label>
          <label className="block text-sm">{t("Số bài tối đa mỗi ngày")}<input className="kg-field" type="number" min={1} max={10} value={settings.dailyLimit} onChange={event => setSettings({ ...settings, dailyLimit: Number(event.target.value) })} /></label>
          <label className="block text-sm">{t("Ngưỡng chất lượng auto-publish")}<input className="kg-field" type="number" min={0} max={100} value={settings.qualityThreshold} onChange={event => setSettings({ ...settings, qualityThreshold: Number(event.target.value) })} /></label>
        </div>
        <label className="block text-sm">{t("Chính sách bài mới")}<select className="kg-field" value={settings.policy} onChange={event => setSettings({ ...settings, policy: event.target.value as Settings["policy"] })}><option value="MANUAL_REVIEW">{t("Duyệt thủ công · xem preview và chỉnh AI")}</option><option value="AUTO_PUBLISH_QUALIFIED">{t("Tự xuất bản bài đạt điều kiện")}</option></select></label>
        <p className="text-xs leading-relaxed text-subtle">{t("Chính sách áp dụng cho lượt mới và được đọc lại trước khi auto-publish. Bài không đạt điều kiện sẽ vào hàng chờ duyệt.")}</p>
        <button type="button" disabled={busy} onClick={() => void updateSettings()} className="kg-button">{t("Lưu cài đặt")}</button>
        <div className="border-t border-line pt-5">
          <h3 className="mb-3 text-lg">{t("Tạo ngay")}</h3><label htmlFor="writer-topic" className="sr-only">{t("Chủ đề")}</label><input id="writer-topic" className="kg-field" value={topic} onChange={event => setTopic(event.target.value)} placeholder={t("Chủ đề (để trống để AI chọn từ kho tri thức)")} /><button type="button" disabled={busy || !configured || !writerEnabled} onClick={() => void generateNow()} className="kg-secondary mt-3">{t("Đưa vào hàng đợi")}</button>
          <dl className="mt-5 grid gap-3 text-xs text-subtle"><div><dt className="inline">{t("Lượt chạy gần nhất:")} </dt><dd className="inline">{settings.lastRunAt ? new Date(settings.lastRunAt).toLocaleString(formatLocale) : t("chưa chạy")}</dd></div><div><dt className="inline">{t("Lịch kế tiếp:")} </dt><dd className="inline">{nextRunAt ? new Date(nextRunAt).toLocaleString(formatLocale) : t("đang tắt")}</dd></div></dl>
          {run && <p className="mt-3 break-words text-xs text-body">{t("Lượt tạo ngay")} {run.status}<span className="mt-1 block font-mono text-subtle">{run.id}</span>{run.errorMessage && <span className="mt-1 block text-danger">{run.errorMessage}</span>}</p>}
        </div>
        </div>
      </details>
      <section className="kg-panel"><h2 className="mb-4 text-xl">{t("Hàng chờ duyệt")} <span className="text-subtle">({posts.length})</span></h2>{posts.length === 0 ? <p className="py-5 text-sm text-subtle">{t("Chưa có bài cần duyệt.")}</p> : <div className="space-y-2">{posts.map(post => <button type="button" key={post.id} onClick={() => void choose(post)} aria-pressed={selected?.id === post.id} className={`block w-full rounded-xl border p-4 text-left ${selected?.id === post.id ? "border-positive/40 bg-sage/60" : "border-line hover:bg-muted/50"}`}><span className="block font-medium text-strong">{post.title}</span><span className="mt-2 block text-xs text-subtle">{post.status} · {new Date(post.createdAt).toLocaleString(formatLocale)}</span></button>)}</div>}</section>
    </div>
    {selected && <>
      <section className="kg-panel"><h2 className="mb-6 text-xl">{t("Biên tập bài viết")}</h2><div className="grid items-start gap-7 xl:grid-cols-2">
        <div className="min-w-0 space-y-4">
          <label className="block text-sm">{t("Tiêu đề")}<input className="kg-field" value={draft.title} onChange={event => setDraft({ ...draft, title: event.target.value })} /></label>
          <label className="block text-sm">{t("Nội dung HTML")}<textarea className="kg-field min-h-72 font-mono text-xs" value={draft.body} onChange={event => setDraft({ ...draft, body: event.target.value })} /></label>
          <label className="block text-sm">{t("Tóm tắt")}<input className="kg-field" value={draft.excerpt} onChange={event => setDraft({ ...draft, excerpt: event.target.value })} /></label>
          <div className="flex flex-wrap gap-2"><button type="button" disabled={busy} onClick={() => void saveEdit()} className="kg-secondary">{t("Lưu chỉnh sửa")}</button><button type="button" disabled={busy} onClick={() => void action(`/admin/blog/writer/posts/${selected.id}/publish`, "Đã xuất bản bài viết.")} className="kg-button">{t("Duyệt & xuất bản")}</button><button type="button" disabled={busy} onClick={() => void action(`/admin/blog/writer/posts/${selected.id}/reject`, "Đã từ chối bài viết.")} className="kg-secondary text-danger">{t("Từ chối")}</button></div>
          <div className="border-t border-line pt-5"><label htmlFor="revision-instruction" className="mb-2 block text-sm font-medium">{t("Yêu cầu AI chỉnh sửa")}</label><textarea id="revision-instruction" className="kg-field min-h-28" value={instruction} onChange={event => setInstruction(event.target.value)} placeholder={t("Ví dụ: giải thích rõ hơn transaction isolation và thêm ví dụ code…")} /><button type="button" disabled={busy || !configured || !instruction.trim()} onClick={() => void revise()} className="kg-secondary mt-3">{t("Tạo bản chỉnh sửa")}</button></div>
        </div>
        <div className="min-w-0"><h3 className="mb-4 text-base">{t("Preview · phiên bản #")}{revisions.at(-1)?.version ?? 1}</h3><article className="answer-html rounded-xl bg-muted/45 p-5" dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(draft.body) }} /><ContentLanguageNotice /></div>
      </div></section>
      <section className="kg-panel"><h2 className="mb-5 text-xl">{t("Lịch sử phiên bản (không ghi đè)")}</h2><div className="space-y-3">{revisions.map(revision => <div key={revision.id} className="flex flex-wrap items-center justify-between gap-3 border-b border-line/70 pb-4 last:border-b-0"><div><p className="text-sm font-medium text-strong">#{revision.version} · {t("Điểm")} {revision.qualityScore ?? "-"} · {revision.tokensUsed ?? 0} tokens · ${revision.costUsd?.toFixed(4) ?? "0.0000"}</p><p className="mt-1 text-xs text-subtle">{revision.instruction ?? t("Bản tạo đầu tiên")} · {new Date(revision.createdAt).toLocaleString(formatLocale)}</p></div>{revision.version !== revisions.at(-1)?.version && <div className="flex gap-2"><button type="button" onClick={() => setCompareRevision(revision)} className="kg-secondary">{t("So sánh")}</button><button type="button" disabled={busy} onClick={() => void restore(revision.version)} className="kg-secondary">{t("Khôi phục")}</button></div>}</div>)}</div>
        {compareRevision && <div className="mt-5 border-t border-line pt-5"><h3 className="mb-4 text-lg">{t("So sánh bản hiện tại với #")}{compareRevision.version}</h3><div className="grid gap-5 md:grid-cols-2"><article aria-label={t("Bản hiện tại")} className="answer-html max-h-96 overflow-auto rounded-xl bg-muted/40 p-5" dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(draft.body) }} /><article aria-label={t("Bản trước")} className="answer-html max-h-96 overflow-auto rounded-xl bg-muted/40 p-5" dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(compareRevision.body) }} /></div></div>}
      </section>
    </>}
  </div>;
}

export default function AdminWriterPage(){return <RequireAuth><WriterAdmin/></RequireAuth>;}
