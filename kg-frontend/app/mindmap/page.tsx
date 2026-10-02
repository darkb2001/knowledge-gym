"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { RequireAuth } from "@/components/ui";
import { apiRequest } from "@/lib/api-client";

type MindmapNode = {
  id: string;
  name: string;
  slug: string;
  topicId: string | null;
  questionCount: number;
  masteryPct: number;
};
type MindmapData = {
  nodes: MindmapNode[];
  edges: { source: string; target: string; relation: string }[];
};

function Mindmap() {
  const [data, setData] = useState<MindmapData | null>(null);
  const [error, setError] = useState("");
  const router = useRouter();

  useEffect(() => {
    const controller = new AbortController();
    apiRequest<MindmapData>("/mindmap", { signal: controller.signal })
      .then((result) => { if (!controller.signal.aborted) setData(result); })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : "Không tải được mindmap");
      });
    return () => controller.abort();
  }, []);

  if (error) return <div role="alert" className="rounded-sm border border-ember-500/50 p-4 text-ember-300">{error}</div>;
  if (!data) return <p className="animate-soft-pulse text-ink-400">Đang tải sơ đồ kiến thức…</p>;
  if (data.nodes.length === 0) return <p className="text-ink-400">Chưa có module để hiển thị.</p>;

  const width = 960;
  const height = Math.max(560, Math.ceil(data.nodes.length / 4) * 150);
  const positions = new Map<string, { x: number; y: number }>();
  data.nodes.forEach((node, index) => {
    const columns = Math.min(4, data.nodes.length);
    const row = Math.floor(index / columns);
    const column = index % columns;
    const rowCount = Math.min(columns, data.nodes.length - row * columns);
    positions.set(node.id, {
      x: ((column + 1) * width) / (rowCount + 1),
      y: 90 + row * 145,
    });
  });

  return <div className="space-y-5">
    <header><p className="text-xs uppercase tracking-[0.2em] text-ember-400">Bản đồ kiến thức</p><h1 className="font-display text-3xl text-ink-50">Mindmap</h1><p className="mt-2 text-sm text-ink-400">Kích thước nút biểu thị số câu hỏi; màu biểu thị mastery. Các đường nối thể hiện module cùng topic.</p></header>
    <div className="overflow-x-auto rounded-sm border border-ink-700 bg-ink-900/50 p-4">
      <svg role="img" aria-label="Sơ đồ module kiến thức" viewBox={`0 0 ${width} ${height}`} className="min-w-[720px] w-full">
        <g aria-hidden="true">
          {data.edges.map((edge) => {
            const source = positions.get(edge.source);
            const target = positions.get(edge.target);
            if (!source || !target) return null;
            return <line key={`${edge.source}-${edge.target}`} x1={source.x} y1={source.y} x2={target.x} y2={target.y} stroke="#59636f" strokeWidth="2" strokeDasharray="5 5" />;
          })}
        </g>
        {data.nodes.map((node) => {
          const point = positions.get(node.id)!;
          const radius = Math.max(27, Math.min(54, 25 + Math.sqrt(node.questionCount) * 3));
          const mastery = Math.max(0, Math.min(100, Number(node.masteryPct) || 0));
          const fill = mastery >= 75 ? "#477c63" : mastery >= 40 ? "#8d713d" : "#71494a";
          return <g key={node.id} role="button" tabIndex={0} aria-label={`${node.name}, ${mastery}% mastery, ${node.questionCount} câu hỏi`} onClick={() => router.push(`/quiz/${node.id}`)} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") router.push(`/quiz/${node.id}`); }} className="cursor-pointer outline-none">
            <circle cx={point.x} cy={point.y} r={radius} fill={fill} stroke="#e8d5b5" strokeOpacity=".75" strokeWidth="2" />
            <text x={point.x} y={point.y - 3} textAnchor="middle" fill="#fff9ed" fontSize="12" fontWeight="600">{node.name.length > 19 ? `${node.name.slice(0, 17)}…` : node.name}</text>
            <text x={point.x} y={point.y + 15} textAnchor="middle" fill="#f1e9dc" fontSize="11">{mastery.toFixed(0)}% · {node.questionCount} câu</text>
          </g>;
        })}
      </svg>
    </div>
    <p className="text-xs text-ink-500">Chọn một module để bắt đầu quiz.</p>
  </div>;
}

export default function MindmapPage() {
  return <RequireAuth><Mindmap /></RequireAuth>;
}
