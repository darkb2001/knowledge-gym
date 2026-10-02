"use client";

import { useCallback, useEffect, useState } from "react";
import DOMPurify from "isomorphic-dompurify";
import { apiRequest } from "@/lib/api-client";
import { RequireAuth } from "@/components/ui";

type Settings = { enabled: boolean; localTime: string; timezone: string; dailyLimit: number; policy: "MANUAL_REVIEW" | "AUTO_PUBLISH_QUALIFIED"; qualityThreshold: number; lastScheduledDate: string | null; lastRunAt: string | null };
type WriterRun = { id: string; status: string; attempts: number; errorMessage: string | null };
type WriterStats = { pendingReview: number; generatedToday: number; tokensToday: number; costToday: number; costThisMonth: number; failedJobs: number };
type BlogPost = { id: string; title: string; slug: string; body: string; excerpt: string | null; status: string; createdAt: string; tags: string[] };
type Revision = { id: string; version: number; title: string; body: string; excerpt: string | null; seoTitle: string | null; seoDescription: string | null; seoKeywords: string[]; instruction: string | null; sourceIds: string[]; model: string | null; qualityScore: number | null; tokensUsed: number | null; costUsd: number | null; createdAt: string };
type Draft = { title: string; body: string; excerpt: string };

const initial: Settings = { enabled: false, localTime: "06:00:00", timezone: "Asia/Jakarta", dailyLimit: 1, policy: "MANUAL_REVIEW", qualityThreshold: 85, lastScheduledDate: null, lastRunAt: null };

function WriterAdmin() {
  const [settings, setSettings] = useState<Settings>(initial);
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
      setPosts(queue);
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
      setRun(queued); setMessage(`Đã đưa lượt tạo vào hàng đợi (${queued.status}, ${queued.id}). Hàng chờ duyệt tự cập nhật.`); setTopic("");
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
    if (!selected) return;
    setBusy(true);
    try { await apiRequest(path, { method: "POST" }); setMessage(success); setSelected(null); setRevisions([]); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : "Thao tác thất bại"); }
    finally { setBusy(false); }
  }
  async function restore(version: number) {
    if (!selected) return;
    try {
      await apiRequest(`/admin/blog/writer/posts/${selected.id}/revisions/${version}/restore`, { method: "POST" });
      const post=await apiRequest<BlogPost>(`/admin/blog/writer/posts/${selected.id}`);await choose(post);setMessage(`Đã khôi phục nội dung phiên bản ${version} thành phiên bản mới.`);
    } catch (e) { setError(e instanceof Error ? e.message : "Không khôi phục được phiên bản"); }
  }

  return <main className="mx-auto max-w-7xl space-y-7 p-6">
    <header className="flex flex-wrap items-end justify-between gap-3"><div><p className="text-xs uppercase tracking-[0.2em] text-ember-400">Quản trị nội dung</p><h1 className="font-display text-3xl text-ink-50">AI Writer</h1></div><a href="/dashboard" className="rounded-sm border border-ink-700 px-3 py-2 text-sm">Dashboard</a></header>
    {!configured && <p role="status" className="rounded-sm border border-ember-500/50 p-3 text-ember-300">OPENAI_API_KEY chưa được cấu hình ở backend. Không gửi khóa bí mật từ trình duyệt.</p>}
    {configured && !writerEnabled && <p role="status" className="rounded-sm border border-ember-500/50 p-3 text-ember-300">Worker AI writer đang tắt (`app.blog.writer.enabled=false`). Lượt Generate now sẽ bị từ chối cho đến khi bật worker.</p>}
    {error && <p role="alert" className="rounded-sm border border-ember-500/50 p-3 text-ember-300">{error}</p>}{message && <p role="status" className="rounded-sm border border-moss-600 p-3 text-moss-300">{message}</p>}
    <section className="grid gap-5 lg:grid-cols-[1.2fr_1fr]">
      <div className="space-y-4 rounded-sm border border-ink-700 bg-ink-900/50 p-5">
        <h2 className="font-display text-xl">Lịch và chính sách xuất bản</h2>
        <label className="flex items-center gap-3 text-sm"><input type="checkbox" checked={settings.enabled} onChange={(e)=>setSettings({...settings,enabled:e.target.checked})}/> Bật tạo bài tự động mỗi ngày</label>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="space-y-1 text-sm">Giờ địa phương<input className="w-full rounded-sm border border-ink-700 bg-ink-950 p-2" type="time" value={settings.localTime.slice(0,5)} onChange={(e)=>setSettings({...settings,localTime:e.target.value+":00"})}/></label>
          <label className="space-y-1 text-sm">Múi giờ IANA<input className="w-full rounded-sm border border-ink-700 bg-ink-950 p-2" value={settings.timezone} onChange={(e)=>setSettings({...settings,timezone:e.target.value})} placeholder="Asia/Jakarta"/></label>
          <label className="space-y-1 text-sm">Số bài tối đa mỗi ngày<input className="w-full rounded-sm border border-ink-700 bg-ink-950 p-2" type="number" min={1} max={10} value={settings.dailyLimit} onChange={(e)=>setSettings({...settings,dailyLimit:Number(e.target.value)})}/></label>
          <label className="space-y-1 text-sm">Ngưỡng chất lượng auto-publish<input className="w-full rounded-sm border border-ink-700 bg-ink-950 p-2" type="number" min={0} max={100} value={settings.qualityThreshold} onChange={(e)=>setSettings({...settings,qualityThreshold:Number(e.target.value)})}/></label>
        </div>
        <label className="block space-y-1 text-sm">Chính sách bài mới<select className="w-full rounded-sm border border-ink-700 bg-ink-950 p-2" value={settings.policy} onChange={(e)=>setSettings({...settings,policy:e.target.value as Settings["policy"]})}><option value="MANUAL_REVIEW">Duyệt thủ công · xem preview và chỉnh AI</option><option value="AUTO_PUBLISH_QUALIFIED">Tự xuất bản bài đạt điều kiện</option></select></label>
        <p className="text-xs text-ink-400">Chính sách áp dụng cho lượt mới và được đọc lại trước khi auto-publish. Bài không đạt điều kiện sẽ vào hàng chờ duyệt.</p>
        <button disabled={busy} onClick={()=>void updateSettings()} className="rounded-sm bg-moss-600 px-4 py-2 text-sm text-white disabled:opacity-50">Lưu cài đặt</button>
        <div className="border-t border-ink-700 pt-4"><h3 className="mb-2 font-display">Tạo ngay</h3><div className="flex flex-wrap gap-2"><input className="min-w-60 flex-1 rounded-sm border border-ink-700 bg-ink-950 p-2" value={topic} onChange={(e)=>setTopic(e.target.value)} placeholder="Chủ đề (để trống để AI chọn từ kho tri thức)"/><button disabled={busy||!configured||!writerEnabled} onClick={()=>void generateNow()} className="rounded-sm border border-ember-500 px-4 py-2 text-sm text-ember-300 disabled:opacity-50">Đưa vào hàng đợi</button></div></div>
        <p className="text-xs text-ink-400">Lượt chạy gần nhất: {settings.lastRunAt ? new Date(settings.lastRunAt).toLocaleString() : "chưa chạy"} · Lịch kế tiếp: {nextRunAt ? new Date(nextRunAt).toLocaleString() : "đang tắt"}</p>
        {run&&<p className={`text-xs ${run.status==="FAILED"?"text-ember-300":"text-moss-300"}`}>Lượt tạo ngay {run.id}: {run.status}{run.errorMessage?` · ${run.errorMessage}`:""}</p>}
      </div>
      <div className="space-y-3 rounded-sm border border-ink-700 bg-ink-900/50 p-5"><h2 className="font-display text-xl">Hàng chờ duyệt ({posts.length})</h2>{posts.length===0&&<p className="text-sm text-ink-400">Chưa có bài cần duyệt.</p>}{posts.map((post)=><button key={post.id} onClick={()=>void choose(post)} className={`block w-full rounded-sm border p-3 text-left ${selected?.id===post.id?"border-moss-500":"border-ink-700 hover:border-ink-500"}`}><span className="block font-medium text-ink-100">{post.title}</span><span className="text-xs text-ink-400">{post.status} · {new Date(post.createdAt).toLocaleString()}</span></button>)}</div>
    </section>
    {stats&&<section className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6">{[["Chờ duyệt",stats.pendingReview],["Lượt AI hôm nay",stats.generatedToday],["Tokens hôm nay",stats.tokensToday.toLocaleString("vi-VN")],["Chi phí hôm nay",`$${stats.costToday.toFixed(4)}`],["Chi phí tháng này",`$${stats.costThisMonth.toFixed(2)}`],["Run lỗi",stats.failedJobs]].map(([label,value])=><div key={String(label)} className="rounded-sm border border-ink-700 bg-ink-900/50 p-3"><p className="text-xs text-ink-400">{label}</p><p className="font-display text-lg">{value}</p></div>)}</section>}
    {stats&&stats.costThisMonth>monthlyAlert&&<p role="alert" className="rounded-sm border border-ember-500/50 p-3 text-ember-300">Chi phí AI tháng này đã vượt ngưỡng cảnh báo ${monthlyAlert.toFixed(2)}.</p>}
    {selected&&<section className="grid gap-5 xl:grid-cols-2">
      <div className="space-y-3 rounded-sm border border-ink-700 bg-ink-900/50 p-5"><h2 className="font-display text-xl">Preview · phiên bản #{revisions.at(-1)?.version ?? 1}</h2><label className="block space-y-1 text-sm">Tiêu đề<input className="w-full rounded-sm border border-ink-700 bg-ink-950 p-2" value={draft.title} onChange={(e)=>setDraft({...draft,title:e.target.value})}/></label><label className="block space-y-1 text-sm">Nội dung HTML<textarea className="h-72 w-full rounded-sm border border-ink-700 bg-ink-950 p-2 font-mono text-xs" value={draft.body} onChange={(e)=>setDraft({...draft,body:e.target.value})}/></label><label className="block space-y-1 text-sm">Tóm tắt<input className="w-full rounded-sm border border-ink-700 bg-ink-950 p-2" value={draft.excerpt} onChange={(e)=>setDraft({...draft,excerpt:e.target.value})}/></label><div className="flex flex-wrap gap-2"><button disabled={busy} onClick={()=>void saveEdit()} className="rounded-sm border border-ink-600 px-3 py-2 text-sm">Lưu chỉnh sửa</button><button disabled={busy} onClick={()=>void action(`/admin/blog/writer/posts/${selected.id}/publish`,"Đã xuất bản bài viết.")} className="rounded-sm bg-moss-600 px-3 py-2 text-sm text-white">Duyệt & xuất bản</button><button disabled={busy} onClick={()=>void action(`/admin/blog/writer/posts/${selected.id}/reject`,"Đã từ chối bài viết.")} className="rounded-sm border border-ember-500 px-3 py-2 text-sm text-ember-300">Từ chối</button></div><article className="prose prose-invert max-w-none rounded-sm border border-ink-700 bg-ink-950 p-4" dangerouslySetInnerHTML={{__html:DOMPurify.sanitize(draft.body)}}/></div>
      <div className="space-y-4 rounded-sm border border-ink-700 bg-ink-900/50 p-5"><h2 className="font-display text-xl">Yêu cầu AI chỉnh sửa</h2><textarea className="h-24 w-full rounded-sm border border-ink-700 bg-ink-950 p-2" value={instruction} onChange={(e)=>setInstruction(e.target.value)} placeholder="Ví dụ: giải thích rõ hơn transaction isolation và thêm ví dụ code…"/><button disabled={busy||!configured||!instruction.trim()} onClick={()=>void revise()} className="rounded-sm border border-ember-500 px-4 py-2 text-sm text-ember-300 disabled:opacity-50">Tạo bản chỉnh sửa</button><div className="space-y-2 border-t border-ink-700 pt-4"><h3 className="font-display">Lịch sử phiên bản (không ghi đè)</h3>{revisions.map((revision)=><div key={revision.id} className="flex flex-wrap items-center justify-between gap-2 rounded-sm border border-ink-700 p-3"><div><p className="text-sm">#{revision.version} · score {revision.qualityScore ?? "—"} · {revision.tokensUsed ?? 0} tokens · ${revision.costUsd?.toFixed(4) ?? "0.0000"}</p><p className="text-xs text-ink-400">{revision.instruction ?? "Initial generation"} · {new Date(revision.createdAt).toLocaleString()}</p></div><div className="flex gap-3">{revision.version!==(revisions.at(-1)?.version)&&<><button onClick={()=>setCompareRevision(revision)} className="text-xs text-ink-200 underline">So sánh</button><button onClick={()=>void restore(revision.version)} className="text-xs text-moss-300 underline">Khôi phục</button></>}</div></div>)}</div>
        {compareRevision&&<div className="space-y-2 border-t border-ink-700 pt-4"><h3 className="font-display">So sánh bản hiện tại với #{compareRevision.version}</h3><div className="grid gap-3 md:grid-cols-2"><article className="prose prose-invert max-h-96 overflow-auto rounded-sm border border-ink-700 p-3" dangerouslySetInnerHTML={{__html:DOMPurify.sanitize(draft.body)}}/><article className="prose prose-invert max-h-96 overflow-auto rounded-sm border border-ink-700 p-3" dangerouslySetInnerHTML={{__html:DOMPurify.sanitize(compareRevision.body)}}/></div></div>}
      </div>
    </section>}
  </main>;
}

export default function AdminWriterPage(){return <RequireAuth><WriterAdmin/></RequireAuth>;}
