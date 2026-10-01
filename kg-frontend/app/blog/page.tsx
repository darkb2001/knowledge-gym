"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getApiBase } from "@/lib/api-client";

type BlogPost = { id:string; title:string; slug:string; excerpt:string|null; publishedAt:string; likeCount:number; viewCount:number; tags:string[] };

export default function BlogPage(){
  const [posts,setPosts]=useState<BlogPost[]>([]);const [error,setError]=useState("");const [loading,setLoading]=useState(true);
  useEffect(()=>{const abort=new AbortController();fetch(`${getApiBase()}/blog/posts?page=1&size=20`,{signal:abort.signal}).then(async response=>{if(!response.ok)throw new Error("Không tải được bài viết");return response.json() as Promise<BlogPost[]>;}).then(setPosts).catch(e=>{if(!abort.signal.aborted)setError(e instanceof Error?e.message:"Lỗi tải blog");}).finally(()=>{if(!abort.signal.aborted)setLoading(false);});return()=>abort.abort();},[]);
  return <main className="mx-auto max-w-5xl space-y-6 p-6 text-ink-100"><div className="flex items-end justify-between"><div><p className="text-xs uppercase tracking-[0.2em] text-ember-400">Kho tri thức</p><h1 className="font-display text-3xl">Knowledge Gym Blog</h1></div><a className="text-sm underline" href={`${getApiBase()}/blog/feed.rss`}>RSS feed</a></div>
    {loading&&<p>Đang tải bài viết…</p>}{error&&<p role="alert" className="text-ember-400">{error}</p>}{!loading&&!error&&posts.length===0&&<p>Chưa có bài viết được xuất bản.</p>}
    <div className="grid gap-4 md:grid-cols-2">{posts.map(post=><article key={post.id} className="space-y-3 rounded-sm border border-ink-700 bg-ink-900/60 p-5"><div className="flex flex-wrap gap-2">{post.tags.map(tag=><span key={tag} className="rounded border border-moss-700 px-2 py-0.5 text-xs text-moss-300">{tag}</span>)}</div><h2 className="font-display text-xl"><Link className="hover:text-ember-300" href={`/blog/${encodeURIComponent(post.slug)}`}>{post.title}</Link></h2><p className="text-sm text-ink-300">{post.excerpt}</p><div className="text-xs text-ink-400">{new Date(post.publishedAt).toLocaleDateString("vi-VN")} · {post.viewCount} lượt xem · {post.likeCount} lượt thích</div></article>)}</div>
  </main>;
}
