"use client";
import type { QuizQuestion as Question } from "@/lib/quiz";
export default function QuizQuestion({ question, selected, onSelect, disabled = false, correctOptionId }: { question: Question; selected?: string | null; onSelect: (id: string) => void; disabled?: boolean; correctOptionId?: string | null }) {
 return <fieldset disabled={disabled} className="rounded-sm border border-ink-700 p-5">
  <legend className="px-2 font-display text-xl">{question.title}</legend>
  <div className="space-y-3">{question.options.map(o => <label key={o.id} className={`flex cursor-pointer gap-3 rounded-sm border p-3 ${correctOptionId === o.id ? "border-moss-600 text-moss-400" : selected === o.id ? "border-ember-400" : "border-ink-700"}`}>
   <input type="radio" name={question.questionId} value={o.id} checked={selected === o.id} onChange={() => onSelect(o.id)} />
   <span>{o.content}{correctOptionId === o.id ? " ✓ Đáp án đúng" : ""}</span>
  </label>)}</div>
 </fieldset>;
}
