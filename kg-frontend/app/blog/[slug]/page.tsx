"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { apiRequest, getAccessToken, getApiBase } from "@/lib/api-client";
import { sanitizeAnswerHtml } from "@/lib/sanitize-html";

type BlogPost={id:string;title:string;slug:string;body:string;excerpt:string|null;publishedAt:string;likeCount:number;viewCount:number;tags:string[]};
type Comment={id:string;userId:string;content:string;createdAt:string};

export default function BlogPostPage(){
  const params=useParams<{slug:string}>();const slug=decodeURIComponent(params.slug);
  const [post,setPost]=useState<BlogPost|null>(null);const [comments,setComments]=useState<Comment[]>([]);const [content,setContent]=useState("");const [message,setMessage]=useState("");
  useEffect(()=>{const controller=new AbortController();fetch(`${getApiBase()}/blog/posts/${encodeURIComponent(slug)}`,{signal:controller.signal}).then(async r=>{if(!r.ok)throw new Error("Không tìm thấy bài viết");return r.json() as Promise<BlogPost>;}).then(setPost).catch(e=>{if(!controller.signal.aborted)setMessage(e instanceof Error?e.message:"Lỗi tải bài viết");});fetch(`${getApiBase()}/blog/posts/${encodeURIComponent(slug)}/comments`,{signal:controller.signal}).then(r=>r.ok?r.json():[]).then(setComments).catch(()=>{});return()=>controller.abort();},[slug]);
  useEffect(()=>{if(!post||!getAccessToken())return;void apiRequest(`/blog/posts/${encodeURIComponent(slug)}/view`,{method:"POST"}).catch(()=>{});},[post,slug]);
  async function like(){try{const result=await apiRequest<{liked:boolean;likeCount:number}>(`/blog/posts/${encodeURIComponent(slug)}/like`,{method:"POST"});setPost(p=>p?{...p,likeCount:result.likeCount}:p);setMessage("Đã thích bài viết");}catch(e){setMessage(e instanceof Error?e.message:"Đăng nhập để thích bài viết");}}
  async function comment(e:FormEvent){e.preventDefault();try{const created=await apiRequest<Comment>(`/blog/posts/${encodeURIComponent(slug)}/comments`,{method:"POST",body:{content}});setComments(old=>[...old,created]);setContent("");setMessage("");}catch(err){setMessage(err instanceof Error?err.message:"Đăng nhập để bình luận");}}
  if(!post)return <main className="mx-auto max-w-4xl p-6 text-ink-100">{message||"Đang tải bài viết…"}</main>;
  return <main className="mx-auto max-w-4xl space-y-7 p-6 text-ink-100"><Link className="text-sm text-moss-300 underline" href="/blog">← Tất cả bài viết</Link><header className="space-y-3 border-b border-ink-700 pb-5"><div className="flex gap-2">{post.tags.map(t=><span key={t} className="rounded border border-moss-700 px-2 py-0.5 text-xs">{t}</span>)}</div><h1 className="font-display text-3xl">{post.title}</h1><p className="text-sm text-ink-400">{new Date(post.publishedAt).toLocaleDateString("vi-VN")} · {post.viewCount} lượt xem</p><button className="rounded border border-ink-600 px-3 py-1 text-sm" onClick={()=>void like()}>Thích · {post.likeCount}</button></header>
    <article className="prose prose-invert max-w-none" dangerouslySetInnerHTML={{__html:sanitizeAnswerHtml(post.body)}} />
    <section className="space-y-3 border-t border-ink-700 pt-5"><h2 className="font-display text-2xl">Bình luận ({comments.length})</h2>{comments.map(c=><div key={c.id} className="rounded border border-ink-700 p-3"><p className="whitespace-pre-wrap">{c.content}</p><time className="text-xs text-ink-400">{new Date(c.createdAt).toLocaleString("vi-VN")}</time></div>)}<form className="space-y-2" onSubmit={comment}><textarea className="min-h-24 w-full rounded border border-ink-600 bg-ink-900 p-3" value={content} onChange={e=>setContent(e.target.value)} placeholder="Viết bình luận…" maxLength={5000}/><button className="rounded bg-moss-700 px-4 py-2" type="submit">Gửi bình luận</button></form>{message&&<p role="status" className="text-sm text-ember-300">{message}</p>}</section>
  </main>;
}
