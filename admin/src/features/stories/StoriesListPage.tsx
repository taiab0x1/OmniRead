import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiGetWithMeta, apiPost, extractApiError } from "@/lib/api";
import { GripVertical, Plus, Search } from "lucide-react";
import { formatRelativeTime } from "@/lib/utils";
import { useGenres } from "@/hooks/useGenres";

type AdminStoryItem = {
  id: string;
  title: string;
  slug: string;
  genre: string;
  status: "draft" | "scheduled" | "published";
  is_featured: boolean;
  is_trending: boolean;
  is_premium: boolean;
  display_order: number;
  total_chapters: number;
  view_count: number;
  avg_rating: number;
  created_at: string;
  published_at: string | null;
};

export function StoriesListPage() {
  const qc = useQueryClient();
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState<string>("");
  const [genre, setGenre] = useState<string>("");
  const [q, setQ] = useState("");
  const [reorderMode, setReorderMode] = useState<"off" | "featured" | "trending">("off");
  const genres = useGenres();

  // In reorder mode we always fetch the relevant subset, regardless of filters.
  const queryParams =
    reorderMode === "off"
      ? {
          page,
          per_page: 20,
          status: status || undefined,
          genre: genre || undefined,
          q: q || undefined,
        }
      : {
          page: 1,
          per_page: 100,
          is_featured: reorderMode === "featured" ? true : undefined,
          is_trending: reorderMode === "trending" ? true : undefined,
        };

  const { data, isLoading } = useQuery({
    queryKey: ["admin-stories", queryParams],
    queryFn: () => apiGetWithMeta<AdminStoryItem[]>("/admin/stories", queryParams),
  });

  const createMut = useMutation({
    mutationFn: (body: { title: string; genre: string }) => apiPost("/admin/stories", body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-stories"] }),
  });

  const reorderMut = useMutation({
    mutationFn: (order: string[]) => apiPost("/admin/stories/reorder", { order }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-stories"] }),
  });

  const [showNew, setShowNew] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [newGenre, setNewGenre] = useState("dark_romance");

  // Working copy of order while dragging.
  const fetched = data?.data ?? [];
  const sorted = useMemo(() => {
    if (reorderMode === "off") return fetched;
    return [...fetched].sort((a, b) => a.display_order - b.display_order);
  }, [fetched, reorderMode]);
  const [draft, setDraft] = useState<AdminStoryItem[]>(sorted);
  useEffect(() => setDraft(sorted), [sorted]);

  return (
    <div className="space-y-5">
      <header className="flex items-end justify-between">
        <div>
          <h1 className="font-display text-3xl">Stories</h1>
          <p className="text-sm text-ink-2">Manage drafts, schedule publishes, feature on the home feed.</p>
        </div>
        <div className="flex gap-2">
          <select
            className="input w-44"
            value={reorderMode}
            onChange={(e) => setReorderMode(e.target.value as "off" | "featured" | "trending")}
          >
            <option value="off">Normal view</option>
            <option value="featured">Reorder: featured</option>
            <option value="trending">Reorder: trending</option>
          </select>
          {reorderMode === "off" && (
            <button className="btn-primary" onClick={() => setShowNew((v) => !v)}>
              <Plus size={16} /> New story
            </button>
          )}
        </div>
      </header>

      {showNew && reorderMode === "off" && (
        <div className="card p-4 flex items-end gap-3">
          <div className="flex-1">
            <label className="text-xs text-ink-2 block mb-1">Title</label>
            <input className="input" value={newTitle} onChange={(e) => setNewTitle(e.target.value)} />
          </div>
          <div className="w-48">
            <label className="text-xs text-ink-2 block mb-1">Genre</label>
            <select className="input" value={newGenre} onChange={(e) => setNewGenre(e.target.value)}>
              {genres.map((g) => (
                <option key={g} value={g}>
                  {g.replace("_", " ")}
                </option>
              ))}
            </select>
          </div>
          <button
            className="btn-primary"
            disabled={!newTitle || createMut.isPending}
            onClick={async () => {
              try {
                await createMut.mutateAsync({ title: newTitle, genre: newGenre });
                setNewTitle("");
                setShowNew(false);
              } catch (e) {
                alert(extractApiError(e));
              }
            }}
          >
            Create draft
          </button>
        </div>
      )}

      {reorderMode === "off" && (
        <div className="card p-3 flex items-center gap-3">
          <div className="relative flex-1">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-3" />
            <input
              className="input pl-9"
              placeholder="Search title…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </div>
          <select className="input w-44" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All statuses</option>
            <option value="draft">Draft</option>
            <option value="scheduled">Scheduled</option>
            <option value="published">Published</option>
          </select>
          <select className="input w-44" value={genre} onChange={(e) => setGenre(e.target.value)}>
            <option value="">All genres</option>
            {genres.map((g) => (
              <option key={g} value={g}>
                {g.replace("_", " ")}
              </option>
            ))}
          </select>
        </div>
      )}

      {reorderMode !== "off" ? (
        <ReorderTable
          rows={draft}
          mode={reorderMode}
          onReorder={setDraft}
          onSave={async () => {
            try {
              await reorderMut.mutateAsync(draft.map((r) => r.id));
            } catch (e) {
              alert(extractApiError(e));
            }
          }}
          saving={reorderMut.isPending}
        />
      ) : (
        <section className="card overflow-hidden">
          <table className="w-full text-sm">
            <thead className="text-left text-ink-3 text-xs uppercase tracking-wide">
              <tr>
                <th className="px-6 py-3">Title</th>
                <th className="px-6 py-3">Genre</th>
                <th className="px-6 py-3">Status</th>
                <th className="px-6 py-3 text-right">Chapters</th>
                <th className="px-6 py-3 text-right">Views</th>
                <th className="px-6 py-3">Created</th>
              </tr>
            </thead>
            <tbody>
              {(data?.data ?? []).map((s) => (
                <tr key={s.id} className="border-t border-white/5 hover:bg-white/[0.02]">
                  <td className="px-6 py-3">
                    <Link to={`/stories/${s.id}`} className="text-ink-1 hover:text-accent-soft font-medium">
                      {s.title}
                    </Link>
                    <div className="flex gap-1 mt-1">
                      {s.is_featured && <span className="pill bg-accent/15 text-accent-soft">Featured</span>}
                      {s.is_trending && <span className="pill bg-gold/15 text-gold">Trending</span>}
                      {s.is_premium && <span className="pill bg-bg-3 text-ink-2">Premium</span>}
                    </div>
                  </td>
                  <td className="px-6 py-3 text-ink-2 capitalize">{s.genre.replace("_", " ")}</td>
                  <td className="px-6 py-3">
                    <StatusPill status={s.status} />
                  </td>
                  <td className="px-6 py-3 text-right text-ink-2">{s.total_chapters}</td>
                  <td className="px-6 py-3 text-right text-ink-2">{s.view_count.toLocaleString()}</td>
                  <td className="px-6 py-3 text-ink-2">{formatRelativeTime(s.created_at)}</td>
                </tr>
              ))}
              {!isLoading && (data?.data ?? []).length === 0 && (
                <tr>
                  <td className="px-6 py-12 text-center text-ink-3" colSpan={6}>
                    No stories yet. Create a draft or generate one in AI Studio.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
          <div className="px-6 py-3 border-t border-white/5 flex items-center justify-between text-xs text-ink-3">
            <span>
              Page {data?.meta?.page ?? page} of{" "}
              {data?.meta?.total ? Math.max(1, Math.ceil((data.meta.total ?? 0) / (data.meta.per_page ?? 20))) : 1}
            </span>
            <div className="flex gap-2">
              <button className="btn-outline" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
                Prev
              </button>
              <button className="btn-outline" onClick={() => setPage((p) => p + 1)}>
                Next
              </button>
            </div>
          </div>
        </section>
      )}
    </div>
  );
}

function ReorderTable({
  rows,
  mode,
  onReorder,
  onSave,
  saving,
}: {
  rows: AdminStoryItem[];
  mode: "featured" | "trending";
  onReorder: (next: AdminStoryItem[]) => void;
  onSave: () => void;
  saving: boolean;
}) {
  const [dragId, setDragId] = useState<string | null>(null);
  const [overId, setOverId] = useState<string | null>(null);

  const move = (sourceId: string, targetId: string) => {
    if (sourceId === targetId) return;
    const next = [...rows];
    const fromIdx = next.findIndex((r) => r.id === sourceId);
    const toIdx = next.findIndex((r) => r.id === targetId);
    if (fromIdx < 0 || toIdx < 0) return;
    const [moved] = next.splice(fromIdx, 1);
    next.splice(toIdx, 0, moved);
    onReorder(next);
  };

  return (
    <section className="card overflow-hidden">
      <div className="px-6 py-3 border-b border-white/5 flex items-center justify-between">
        <div className="text-sm text-ink-2">
          Drag rows to reorder {mode === "featured" ? "featured" : "trending"} carousel. Top = first.
        </div>
        <button className="btn-primary" disabled={saving} onClick={onSave}>
          {saving ? "Saving…" : "Save order"}
        </button>
      </div>
      <ul>
        {rows.length === 0 ? (
          <li className="px-6 py-12 text-center text-ink-3 text-sm">
            No stories flagged as {mode}.
          </li>
        ) : (
          rows.map((r, idx) => (
            <li
              key={r.id}
              draggable
              onDragStart={() => setDragId(r.id)}
              onDragOver={(e) => {
                e.preventDefault();
                if (overId !== r.id) setOverId(r.id);
              }}
              onDragLeave={() => setOverId(null)}
              onDrop={(e) => {
                e.preventDefault();
                if (dragId) move(dragId, r.id);
                setDragId(null);
                setOverId(null);
              }}
              className={`flex items-center gap-3 px-6 py-3 border-t border-white/5 ${
                overId === r.id ? "bg-accent/10" : ""
              } ${dragId === r.id ? "opacity-50" : ""}`}
            >
              <GripVertical size={16} className="text-ink-3 cursor-grab" />
              <span className="text-ink-3 w-8 text-right">{idx + 1}</span>
              <Link to={`/stories/${r.id}`} className="text-ink-1 hover:text-accent-soft flex-1">
                {r.title}
              </Link>
              <span className="text-xs text-ink-3 capitalize">{r.genre.replace("_", " ")}</span>
            </li>
          ))
        )}
      </ul>
    </section>
  );
}

function StatusPill({ status }: { status: string }) {
  const map: Record<string, string> = {
    draft: "bg-bg-3 text-ink-2",
    scheduled: "bg-warning/15 text-warning",
    published: "bg-success/15 text-success",
  };
  return <span className={`pill ${map[status] || "bg-bg-3 text-ink-2"}`}>{status}</span>;
}
