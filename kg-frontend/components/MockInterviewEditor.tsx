"use client";
import { useLocale } from "@/components/locale";
import { useRef, useState } from "react";
import { inputClass } from "@/components/ui";
import { answerInterview, type InterviewAnswer } from "@/lib/interview";
export default function MockInterviewEditor({ sessionId, question, disabled, onBusy }: { sessionId: string; question: { questionId: string; title: string }; disabled: boolean; onBusy: (busy: boolean) => void }) {
  const { t } = useLocale();
 const [text, setText] = useState(""); const [feedback, setFeedback] = useState<InterviewAnswer | null>(null); const [busy, setBusy] = useState(false); const [error, setError] = useState("");const inFlight=useRef(false);
 async function submit() { if(inFlight.current)return;inFlight.current=true;setBusy(true); onBusy(true); setError(""); try {setFeedback(await answerInterview(sessionId, question.questionId, text));} catch(e) {setError(e instanceof Error ? e.message : "Không lưu được câu trả lời");} finally {inFlight.current=false;setBusy(false); onBusy(false);} }
 return <section className="space-y-3 rounded-sm border border-line p-5"><h2 className="font-display text-xl">{question.title}</h2><label className="block">{t("Câu trả lời của bạn")}<textarea className={inputClass} rows={6} maxLength={20000} value={text} disabled={disabled || busy} onChange={e => setText(e.target.value)} /></label><button disabled={disabled || busy || !text.trim()} onClick={() => void submit()} className="rounded-sm border border-accent px-4 py-2 disabled:opacity-50">{busy ? t("Đang chấm…") : feedback ? t("Lưu lại câu trả lời") : t("Lưu và chấm")}</button>{error && <p role="alert" className="text-warning">{t(error)}</p>}{feedback && <div className="space-y-2"><p>{t("Điểm từ khóa: ")}{feedback.keywordScore}{t("%")}</p><p>{feedback.feedback}</p><details><summary>{t("Đáp án tham khảo")}</summary><p className="whitespace-pre-wrap text-body">{feedback.sampleAnswer}</p></details></div>}</section>;
}
