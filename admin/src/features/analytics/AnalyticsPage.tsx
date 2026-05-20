import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { apiGet } from "@/lib/api";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
  BarChart,
  Bar,
  Cell,
} from "recharts";
import { formatUsdMicros } from "@/lib/utils";

type RevenuePoint = { date: string; purchases: number; micros: number };
type Retention = Record<string, { cohort_size: number; active: number; rate: number }>;
type ContentItem = {
  id: string;
  title: string;
  genre: string;
  views: number;
  likes: number;
  bookmarks: number;
  avg_rating: number;
  total_ratings: number;
};

type DropoffChapter = {
  chapter_id: string;
  chapter_number: number;
  title: string | null;
  is_free: boolean;
  readers_reached: number;
  completed: number;
  completion_rate: number;
  dropoff_rate: number;
};

type DropoffResponse = {
  story_id: string;
  story_title: string;
  chapters: DropoffChapter[];
};

export function AnalyticsPage() {
  const revenue = useQuery({
    queryKey: ["analytics-revenue"],
    queryFn: () => apiGet<RevenuePoint[]>("/admin/analytics/revenue", { days: 30 }),
  });
  const retention = useQuery({
    queryKey: ["analytics-retention"],
    queryFn: () => apiGet<Retention>("/admin/analytics/retention"),
  });
  const content = useQuery({
    queryKey: ["analytics-content"],
    queryFn: () => apiGet<ContentItem[]>("/admin/analytics/content-performance", { limit: 10 }),
  });

  const [dropoffStoryId, setDropoffStoryId] = useState<string>("");
  const dropoff = useQuery({
    queryKey: ["analytics-dropoff", dropoffStoryId],
    queryFn: () => apiGet<DropoffResponse>(`/admin/analytics/chapter-dropoff/${dropoffStoryId}`),
    enabled: !!dropoffStoryId,
  });

  // Default to the top-views story when content loads
  useEffect(() => {
    if (!dropoffStoryId && content.data && content.data.length > 0) {
      setDropoffStoryId(content.data[0].id);
    }
  }, [content.data, dropoffStoryId]);

  const retentionPoints = retention.data
    ? Object.entries(retention.data).map(([k, v]) => ({ window: k, rate: v.rate * 100 }))
    : [];

  return (
    <div className="space-y-6">
      <header>
        <h1 className="font-display text-3xl">Analytics</h1>
        <p className="text-sm text-ink-2">Revenue, retention, content performance, drop-off.</p>
      </header>

      <section className="card p-6">
        <div className="font-display text-xl mb-3">Revenue (last 30 days)</div>
        <div className="h-64">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={revenue.data ?? []}>
              <CartesianGrid stroke="rgba(255,255,255,0.05)" />
              <XAxis dataKey="date" stroke="#666680" />
              <YAxis stroke="#666680" tickFormatter={(v) => `$${(v / 1_000_000).toFixed(0)}`} />
              <Tooltip
                contentStyle={{ background: "#14141A", border: "1px solid rgba(255,255,255,0.06)" }}
                labelStyle={{ color: "#9999B3" }}
                formatter={(v: number) => [formatUsdMicros(v), "Revenue"]}
              />
              <Line type="monotone" dataKey="micros" stroke="#8B5CF6" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </section>

      <section className="card p-6">
        <div className="font-display text-xl mb-3">Retention by cohort window</div>
        <div className="h-64">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={retentionPoints}>
              <CartesianGrid stroke="rgba(255,255,255,0.05)" />
              <XAxis dataKey="window" stroke="#666680" />
              <YAxis stroke="#666680" tickFormatter={(v) => `${v}%`} />
              <Tooltip
                contentStyle={{ background: "#14141A", border: "1px solid rgba(255,255,255,0.06)" }}
                formatter={(v: number) => [`${v.toFixed(1)}%`, "Active"]}
              />
              <Bar dataKey="rate" fill="#8B5CF6" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </section>

      <section className="card p-6">
        <div className="flex items-center justify-between mb-3">
          <div className="font-display text-xl">Chapter drop-off</div>
          <select
            className="input w-72"
            value={dropoffStoryId}
            onChange={(e) => setDropoffStoryId(e.target.value)}
          >
            <option value="">Select a story…</option>
            {(content.data ?? []).map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
        </div>
        {dropoff.data && dropoff.data.chapters.length > 0 ? (
          <>
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={dropoff.data.chapters}>
                  <CartesianGrid stroke="rgba(255,255,255,0.05)" />
                  <XAxis dataKey="chapter_number" stroke="#666680" />
                  <YAxis stroke="#666680" />
                  <Tooltip
                    contentStyle={{ background: "#14141A", border: "1px solid rgba(255,255,255,0.06)" }}
                    labelFormatter={(label) => `Chapter ${label}`}
                    formatter={(v: number, key: string) => [v.toLocaleString(), key === "readers_reached" ? "Readers" : key]}
                  />
                  <Bar dataKey="readers_reached" radius={[6, 6, 0, 0]}>
                    {dropoff.data.chapters.map((ch) => (
                      <Cell
                        key={ch.chapter_id}
                        fill={ch.dropoff_rate > 0.3 ? "#EF4444" : ch.is_free ? "#10B981" : "#8B5CF6"}
                      />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
            <div className="text-xs text-ink-3 mt-2 flex gap-4">
              <span><span className="inline-block w-2 h-2 rounded-full bg-success mr-1" /> Free chapter</span>
              <span><span className="inline-block w-2 h-2 rounded-full bg-accent mr-1" /> Paid chapter</span>
              <span><span className="inline-block w-2 h-2 rounded-full bg-danger mr-1" /> &gt;30% drop-off</span>
            </div>
            <table className="w-full text-sm mt-4">
              <thead className="text-left text-ink-3 text-xs uppercase tracking-wide">
                <tr>
                  <th className="px-2 py-2 w-16">#</th>
                  <th className="px-2 py-2">Title</th>
                  <th className="px-2 py-2 text-right">Readers</th>
                  <th className="px-2 py-2 text-right">Completed</th>
                  <th className="px-2 py-2 text-right">Drop-off</th>
                </tr>
              </thead>
              <tbody>
                {dropoff.data.chapters.map((ch) => (
                  <tr key={ch.chapter_id} className="border-t border-white/5">
                    <td className="px-2 py-2 text-ink-3">{ch.chapter_number}</td>
                    <td className="px-2 py-2 text-ink-1">{ch.title || `Chapter ${ch.chapter_number}`}</td>
                    <td className="px-2 py-2 text-right text-ink-2">{ch.readers_reached.toLocaleString()}</td>
                    <td className="px-2 py-2 text-right text-ink-2">
                      {ch.completed.toLocaleString()} ({(ch.completion_rate * 100).toFixed(0)}%)
                    </td>
                    <td className={`px-2 py-2 text-right ${ch.dropoff_rate > 0.3 ? "text-danger" : "text-ink-2"}`}>
                      {ch.dropoff_rate > 0 ? `${(ch.dropoff_rate * 100).toFixed(1)}%` : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        ) : (
          <div className="py-12 text-center text-ink-3 text-sm">
            {dropoffStoryId ? (dropoff.isLoading ? "Loading…" : "No reader data yet.") : "Pick a story above."}
          </div>
        )}
      </section>

      <section className="card overflow-hidden">
        <div className="px-6 py-4 border-b border-white/5 flex items-center justify-between">
          <div className="font-display text-xl">Top content</div>
          <div className="text-xs text-ink-3">Top 10 by views</div>
        </div>
        <table className="w-full text-sm">
          <thead className="text-left text-ink-3 text-xs uppercase tracking-wide">
            <tr>
              <th className="px-6 py-3">Title</th>
              <th className="px-6 py-3">Genre</th>
              <th className="px-6 py-3 text-right">Views</th>
              <th className="px-6 py-3 text-right">Likes</th>
              <th className="px-6 py-3 text-right">Bookmarks</th>
              <th className="px-6 py-3 text-right">Rating</th>
            </tr>
          </thead>
          <tbody>
            {(content.data ?? []).map((row) => (
              <tr key={row.id} className="border-t border-white/5">
                <td className="px-6 py-3 text-ink-1">{row.title}</td>
                <td className="px-6 py-3 text-ink-2 capitalize">{row.genre.replace("_", " ")}</td>
                <td className="px-6 py-3 text-right">{row.views.toLocaleString()}</td>
                <td className="px-6 py-3 text-right">{row.likes.toLocaleString()}</td>
                <td className="px-6 py-3 text-right">{row.bookmarks.toLocaleString()}</td>
                <td className="px-6 py-3 text-right text-gold">
                  {row.avg_rating.toFixed(2)} <span className="text-ink-3">({row.total_ratings})</span>
                </td>
              </tr>
            ))}
            {(content.data ?? []).length === 0 && (
              <tr>
                <td className="px-6 py-8 text-center text-ink-3" colSpan={6}>
                  {content.isLoading ? "Loading…" : "No data yet."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  );
}
