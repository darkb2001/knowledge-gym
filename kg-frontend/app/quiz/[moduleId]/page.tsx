"use client";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { RequireAuth, inputClass } from "@/components/ui";
import QuizQuestion from "@/components/QuizQuestion";
import { generateQuiz, submitQuiz, quizHistory, type Quiz, type QuizResult, type QuizStrategy, type QuizSummary } from "@/lib/quiz";
function QuizFlow({ moduleId }: { moduleId: string }) {
 const [quiz, setQuiz] = useState<Quiz | null>(null);
 const [result, setResult] = useState<QuizResult | null>(null);
 const [answers, setAnswers] = useState<Record<string, string>>({});
 const [count, setCount] = useState(10);
 const [strategy, setStrategy] = useState<QuizStrategy>("RANDOM");
 const [difficulty, setDifficulty] = useState("");
 const [remaining, setRemaining] = useState(0);
 const [busy, setBusy] = useState(false);
 const [error, setError] = useState("");
 const [history, setHistory] = useState<QuizSummary[]>([]);
 const [page, setPage] = useState(1);
 const [pages, setPages] = useState(0);
 const [historyTick, setHistoryTick] = useState(0);
 const [historyError, setHistoryError] = useState("");
 const deadline = useRef(0);
 const submitting = useRef(false);
 const expiredSubmitted = useRef(false);
 useEffect(() => {
  const ac = new AbortController();
  quizHistory(page, ac.signal).then(h => { if (!ac.signal.aborted) { setHistory(h.items); setPages(h.totalPages); setHistoryError(""); } }).catch(e => { if (!ac.signal.aborted) setHistoryError(e instanceof Error ? e.message : "Không tải được lịch sử"); });
  return () => ac.abort();
 }, [page, historyTick]);
 async function start() {
  setBusy(true); setError("");
  try { const q = await generateQuiz(moduleId, count, strategy, difficulty); setQuiz(q); setAnswers({}); setResult(null); deadline.current = Date.now() + q.timeLimit * 1000; setRemaining(q.timeLimit); expiredSubmitted.current = false; }
  catch(e) { setError(e instanceof Error ? e.message : "Không tạo được quiz"); }
  finally { setBusy(false); }
 }
 const finish = useCallback(async () => {
  if (!quiz || result || submitting.current) return;
  submitting.current = true; setBusy(true); setError("");
  try { const r = await submitQuiz(quiz.id, quiz.questions.map(q => ({ questionId: q.questionId, selectedOptionId: answers[q.questionId] ?? null }))); setResult(r); setHistoryTick(n => n + 1); }
  catch(e) { setError(e instanceof Error ? e.message : "Không nộp được bài. Thử lại."); }
  finally { submitting.current = false; setBusy(false); }
 }, [quiz, result, answers]);
 useEffect(() => {
  if (!quiz || result) return;
  const tick = () => setRemaining(Math.max(0, Math.ceil((deadline.current - Date.now()) / 1000)));
  tick(); const timer = window.setInterval(tick, 500); return () => window.clearInterval(timer);
 }, [quiz, result]);
 useEffect(() => {
  if (quiz && !result && remaining === 0 && !expiredSubmitted.current) { expiredSubmitted.current = true; void finish(); }
 }, [remaining, quiz, result, finish]);
 const answered = Object.keys(answers).length;
 return <div className="space-y-6">
  <Link href={`/questions?moduleId=${encodeURIComponent(moduleId)}`} className="text-ink-400 underline">← Câu hỏi module</Link>
  <h1 className="font-display text-3xl">Luyện trắc nghiệm</h1>
  <p className="text-ink-400">Số câu thực tế phụ thuộc nội dung hiện có. Hết giờ, bài được tự động nộp; câu bỏ trống tính là sai.</p>
  {error && <p role="alert" className="text-ember-400">{error}</p>}
  {!quiz || result ? <form onSubmit={e => {e.preventDefault(); void start();}} className="grid gap-4 sm:grid-cols-4">
   <label>Số câu<input type="number" min={1} max={50} required value={count} onChange={e => setCount(Number(e.target.value))} className={inputClass} /></label>
   <label>Chiến lược<select value={strategy} onChange={e => setStrategy(e.target.value as QuizStrategy)} className={inputClass}><option value="RANDOM">Ngẫu nhiên</option><option value="WEAKNESS">Điểm yếu</option><option value="INTERVIEW">Phỏng vấn</option><option value="SPACED">Đến hạn ôn</option></select></label>
   <label>Độ khó<select value={difficulty} onChange={e => setDifficulty(e.target.value)} className={inputClass}><option value="">Tất cả</option>{["JUNIOR", "MID", "SENIOR"].map(d => <option key={d}>{d}</option>)}</select></label>
   <button disabled={busy} className="rounded-sm bg-ember-500 p-3 text-ink-950 disabled:opacity-50">{busy ? "Đang tạo…" : "Bắt đầu quiz"}</button>
  </form> : null}
  {quiz && <>
   {result ? <p className="font-display text-2xl">Kết quả: {result.score}% · {result.correctCount}/{result.total} câu đúng</p> : <><p role="timer">Còn {Math.floor(remaining / 60)}:{String(remaining % 60).padStart(2, "0")} · Đã chọn {answered}/{quiz.total}</p><progress aria-label="Tiến độ trả lời" max={quiz.total} value={answered} className="w-full" /></>}
   {quiz.questions.map((q, index) => <div key={q.questionId}><p className="mb-2 text-ink-400">Câu {index + 1}{result ? (result.breakdown.find(b => b.questionId === q.questionId)?.correct ? " · Đúng" : " · Sai / bỏ trống") : ""}</p><QuizQuestion question={q} selected={answers[q.questionId]} onSelect={id => setAnswers(a => ({ ...a, [q.questionId]: id }))} disabled={busy || !!result || remaining === 0} correctOptionId={result?.breakdown.find(b => b.questionId === q.questionId)?.correctOptionId} /></div>)}
   {!result && <button disabled={busy} onClick={() => void finish()} className="rounded-sm bg-ember-500 px-5 py-3 text-ink-950 disabled:opacity-50">{busy ? "Đang nộp…" : "Nộp bài"}</button>}
  </>}
  <section className="space-y-3 border-t border-ink-700 pt-6"><h2 className="font-display text-xl">Lịch sử quiz</h2>
   {historyError ? <p role="alert">{historyError} <button onClick={() => setHistoryTick(n => n + 1)} className="underline">Thử lại</button></p> : history.length === 0 ? <p className="text-ink-400">Chưa có phiên quiz.</p> : history.map(h => <p key={h.id}>{new Date(h.startedAt).toLocaleString("vi-VN")} · {h.strategy} · {h.total} câu · {h.finishedAt ? `${h.score}%` : "Chưa nộp"}</p>)}
   <div className="flex gap-4"><button disabled={page <= 1} onClick={() => setPage(p => p - 1)}>← Trước</button><span>{page}/{Math.max(1, pages)}</span><button disabled={page >= pages} onClick={() => setPage(p => p + 1)}>Sau →</button></div>
  </section>
 </div>;
}
export default function QuizPage({ params }: { params: { moduleId: string } }) { return <RequireAuth><QuizFlow key={params.moduleId} moduleId={params.moduleId} /></RequireAuth>; }
