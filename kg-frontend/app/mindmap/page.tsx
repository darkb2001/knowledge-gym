"use client";
import { useLocale } from "@/components/locale";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { RequireAuth, PageHeading } from "@/components/ui";
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
  const { t } = useLocale();
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

  if (error) return <div role="alert" className="rounded-sm border border-accent/50 p-4 text-warning">{t(error)}</div>;
  if (!data) return <p className="animate-soft-pulse text-subtle">{t("Đang tải sơ đồ kiến thức…")}</p>;
  if (data.nodes.length === 0) return <p className="text-subtle">{t("Chưa có module để hiển thị.")}</p>;

  const width = 960;
  const height = Math.max(240, Math.ceil(data.nodes.length / 4) * 175 + 55);
  const positions = new Map<string, { x: number; y: number }>();
  data.nodes.forEach((node, index) => {
    const columns = Math.min(4, data.nodes.length);
    const row = Math.floor(index / columns);
    const column = index % columns;
    const rowCount = Math.min(columns, data.nodes.length - row * columns);
    positions.set(node.id, {
      x: ((column + 1) * width) / (rowCount + 1),
      y: 85 + row * 175,
    });
  });

  return <div className="space-y-5">
    <PageHeading title="Sơ đồ kiến thức" description="Kích thước nút biểu thị số câu hỏi; màu biểu thị mastery. Các đường nối thể hiện module cùng topic." />
    <div className="overflow-x-auto rounded-sm border border-line bg-surface/50 p-4">
      <svg role="group" aria-label={t("Sơ đồ module kiến thức")} viewBox={`0 0 ${width} ${height}`} className="min-w-[640px] w-full">
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
          const radius = Math.max(40, Math.min(60, 32 + Math.sqrt(node.questionCount) * 3));
          const mastery = Math.max(0, Math.min(100, Number(node.masteryPct) || 0));
          const fill = mastery >= 75 ? "#dce7d9" : mastery >= 40 ? "#e3edf0" : "#e8dfcd";
          return <g key={node.id} role="button" tabIndex={0} aria-label={`${node.name}, ${t("Mức độ nắm vững")} ${mastery}%, ${node.questionCount} ${t("câu hỏi")}`} onClick={() => router.push(`/quiz/${node.id}`)} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); router.push(`/quiz/${node.id}`); } }} className="cursor-pointer focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent"><title>{node.name}</title>
            <circle cx={point.x} cy={point.y} r={radius} fill={fill} stroke="#7d877f" strokeWidth="1" />
            <text x={point.x} y={point.y + 5} textAnchor="middle" fill="#273c4a" fontSize="18" fontWeight="600">{mastery.toFixed(0)}%</text>
            <text x={point.x} y={point.y + radius + 22} textAnchor="middle" fill="#273c4a" fontSize="13" fontWeight="600">{node.name.length > 22 ? `${node.name.slice(0, 20)}…` : node.name}</text>
            <text x={point.x} y={point.y + radius + 40} textAnchor="middle" fill="#43525a" fontSize="12">{node.questionCount} {t("câu hỏi")}</text>
          </g>;
        })}
      </svg>
    </div>
    <p className="text-sm text-body">{t("Chọn một module để bắt đầu quiz.")}</p><p className="text-xs text-subtle lg:hidden">{t("Trên màn hình nhỏ, vuốt ngang để xem các module.")}</p>
  </div>;
}

export default function MindmapPage() {
  return <RequireAuth><Mindmap /></RequireAuth>;
}
