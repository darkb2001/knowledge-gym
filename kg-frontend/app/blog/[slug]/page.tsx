"use client";
import { useLocale } from "@/components/locale";

import Link from "next/link";
import { useParams } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { apiRequest, getAccessToken, getApiBase } from "@/lib/api-client";
import { sanitizeAnswerHtml } from "@/lib/sanitize-html";
import { PublicShell, ContentLanguageNotice } from "@/components/ui";

type BlogPost={id:string;title:string;slug:string;body:string;excerpt:string|null;publishedAt:string;likeCount:number;viewCount:number;tags:string[]};
type Comment={id:string;userId:string;content:string;createdAt:string};

export default function BlogPostPage(){
  const { t, formatLocale } = useLocale();
  const params=useParams<{slug:string}>();const slug=decodeURIComponent(params.slug);
  const [post,setPost]=useState<BlogPost|null>(null);const [comments,setComments]=useState<Comment[]>([]);const [content,setContent]=useState("");const [message,setMessage]=useState("");
  useEffect(()=>{const controller=new AbortController();fetch(`${getApiBase()}/blog/posts/${encodeURIComponent(slug)}`,{signal:controller.signal}).then(async r=>{if(!r.ok)throw new Error("Không tìm thấy bài viết");return r.json() as Promise<BlogPost>;}).then(setPost).catch(e=>{if(!controller.signal.aborted)setMessage(e instanceof Error?e.message:"Lỗi tải bài viết");});fetch(`${getApiBase()}/blog/posts/${encodeURIComponent(slug)}/comments`,{signal:controller.signal}).then(r=>r.ok?r.json():[]).then(setComments).catch(()=>{});return()=>controller.abort();},[slug]);
  useEffect(()=>{if(!post||!getAccessToken())return;void apiRequest(`/blog/posts/${encodeURIComponent(slug)}/view`,{method:"POST"}).catch(()=>{});},[post,slug]);
  async function like(){try{const result=await apiRequest<{liked:boolean;likeCount:number}>(`/blog/posts/${encodeURIComponent(slug)}/like`,{method:"POST"});setPost(p=>p?{...p,likeCount:result.likeCount}:p);setMessage("Đã thích bài viết");}catch(e){setMessage(e instanceof Error?e.message:"Đăng nhập để thích bài viết");}}
  async function comment(e:FormEvent){e.preventDefault();try{const created=await apiRequest<Comment>(`/blog/posts/${encodeURIComponent(slug)}/comments`,{method:"POST",body:{content}});setComments(old=>[...old,created]);setContent("");setMessage("");}catch(err){setMessage(err instanceof Error?err.message:"Đăng nhập để bình luận");}}
  if (!post) return <PublicShell><p role="status" className="text-subtle">{t(message || "Đang tải bài viết…")}</p></PublicShell>;
  return <PublicShell><div className="kg-reading space-y-7">
    <Link className="inline-flex min-h-11 items-center text-sm text-accent hover:underline" href="/blog">{t("← Tất cả bài viết")}</Link>
    <header className="space-y-4 border-b border-line pb-6"><div className="flex flex-wrap gap-2">{post.tags.map(tag => <span key={tag} className="rounded-md bg-sage/60 px-2.5 py-1 text-xs text-positive">{tag}</span>)}</div><h1 className="text-3xl sm:text-4xl">{post.title}</h1><p className="text-sm text-subtle">{new Date(post.publishedAt).toLocaleDateString(formatLocale)} · {post.viewCount} {t("lượt xem")}</p><button type="button" className="kg-secondary" onClick={() => void like()}>{t("Thích ·")} {post.likeCount}</button><ContentLanguageNotice /></header>
    <article className="answer-html" dangerouslySetInnerHTML={{ __html: sanitizeAnswerHtml(post.body) }} />
    <section className="space-y-4 border-t border-line pt-7"><h2 className="text-2xl">{t("Bình luận")} ({comments.length})</h2>{comments.map(comment => <div key={comment.id} className="border-b border-line/70 py-4"><p className="whitespace-pre-wrap text-sm leading-relaxed">{comment.content}</p><time className="mt-2 block text-xs text-subtle">{new Date(comment.createdAt).toLocaleString(formatLocale)}</time></div>)}<form className="space-y-3" onSubmit={comment}><label htmlFor="blog-comment" className="block text-sm font-medium">{t("Viết bình luận…")}</label><textarea id="blog-comment" className="kg-field min-h-28" value={content} onChange={event => setContent(event.target.value)} placeholder={t("Viết bình luận…")} maxLength={5000} required /><button className="kg-button" type="submit">{t("Gửi bình luận")}</button></form>{message && <p role="status">{t(message)}</p>}</section>
  </div></PublicShell>;
}
