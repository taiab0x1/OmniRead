import { useParams, Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { useRef, useState } from "react";
import { api, apiGet, apiPut, apiPost, extractApiError } from "@/lib/api";
import { ArrowLeft, BookPlus, Sparkles, Trash2, Upload } from "lucide-react";
import { formatRelativeTime } from "@/lib/utils";

type StoryDetail = {
  id: string;
  title: string;
  slug: string;
  genre: string;
  status: string;
  is_featured: boolean;
  is_trending: boolean;
  is_premium: boolean;
  total_chapters: number;
  view_count: number;
  avg_rating: number;
  hook_line: string | null;
  summary: string | null;
  tags: string[] | null;
  cover_url: string | null;
  display_order?: number;
  chapter_count_total: number;
  created_at: string;
  published_at: string | null;
};

type ChapterRow = {
  id: string;
  chapter_number: number;
  title: string | null;
  is_free: boolean;
  coin_cost: number;
  status: string;
  word_count: number | null;
  has_cliffhanger: boolean;
  published_at: string | null;
};

export function StoryEditorPage() {
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();

  const story = useQuery({
    queryKey: ["admin-story", id],
    queryFn: () => apiGet<StoryDetail>(`/admin/stories/${id}`),
    enabled: !!id,
  });
  const chapters = useQuery({
    queryKey: ["admin-chapters", id],
    queryFn: () => apiGet<ChapterRow[]>("/admin/chapters", { story_id: id, limit: 100 }),
    enabled: !!id,
  });

  const updateMut = useMutation({
    mutationFn: (body: Partial<StoryDetail>) => apiPut(`/admin/stories/${id}`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-story", id] }),
  });
  const publishMut = useMutation({
    mutationFn: () => apiPost(`/admin/stories/${id}/publish`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-story", id] }),
  });
  const unpublishMut = useMutation({
    mutationFn: () => apiPost(`/admin/stories/${id}/unpublish`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-story", id] }),
  });
  const bulkPublishMut = useMutation({
    mutationFn: () => apiPost("/admin/chapters/bulk-publish", { story_id: id }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin-chapters", id] });
      qc.invalidateQueries({ queryKey: ["admin-story", id] });
    },
  });

  const form = useForm<Partial<StoryDetail>>({
    values: story.data,
  });

  const [adding, setAdding] = useState(false);
  const [newChapterNum, setNewChapterNum] = useState(1);

  const createChapterMut = useMutation({
    mutationFn: () =>
      apiPost("/admin/chapters", {
        story_id: id,
        chapter_number: newChapterNum,
        content: "[draft]",
        is_free: newChapterNum <= (story.data?.chapter_count_total ?? 3) ? false : false,
        coin_cost: 5,
        has_cliffhanger: true,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin-chapters", id] });
      setAdding(false);
    },
  });

  if (story.isLoading || !story.data) {
    return <div className="text-ink-3">Loading…</div>;
  }
  const s = story.data;
  const draftCount = (chapters.data ?? []).filter((c) => c.status === "draft").length;

  return (
    <div className="space-y-6">
      <div>
        <Link to="/stories" className="text-ink-3 hover:text-ink-1 inline-flex items-center gap-1 text-sm">
          <ArrowLeft size={14} /> Stories
        </Link>
      </div>

      <header className="flex items-start justify-between gap-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="font-display text-3xl">{s.title}</h1>
            <StatusPill status={s.status} />
          </div>
          <div className="text-sm text-ink-3 mt-1">
            slug: <span className="font-mono">{s.slug}</span> · created{" "}
            {formatRelativeTime(s.created_at)}
          </div>
        </div>
        <div className="flex gap-2">
          {s.status !== "published" ? (
            <button
              className="btn-primary"
              disabled={publishMut.isPending}
              onClick={() => publishMut.mutate()}
            >
              Publish
            </button>
          ) : (
            <button
              className="btn-outline"
              disabled={unpublishMut.isPending}
              onClick={() => unpublishMut.mutate()}
            >
              Unpublish
            </button>
          )}
        </div>
      </header>

      <CoverUploader
        currentUrl={s.cover_url}
        onUploaded={async (url) => {
          await updateMut.mutateAsync({ cover_url: url });
        }}
      />

      <form
        onSubmit={form.handleSubmit(async (values) => {
          try {
            await updateMut.mutateAsync(values);
          } catch (e) {
            alert(extractApiError(e));
          }
        })}
        className="card p-6 space-y-4"
      >
        <div className="grid grid-cols-2 gap-4">
          <Field label="Title">
            <input className="input" {...form.register("title")} />
          </Field>
          <Field label="Genre">
            <input className="input" {...form.register("genre")} />
          </Field>
          <Field label="Hook line">
            <input className="input" {...form.register("hook_line")} />
          </Field>
          <Field label="Tags (comma separated)">
            <input
              className="input"
              defaultValue={(s.tags ?? []).join(", ")}
              onBlur={(e) =>
                form.setValue(
                  "tags",
                  e.target.value
                    .split(",")
                    .map((t) => t.trim())
                    .filter(Boolean)
                )
              }
            />
          </Field>
        </div>
        <Field label="Summary">
          <textarea className="input min-h-[120px]" {...form.register("summary")} />
        </Field>
        <div className="flex items-center gap-6 text-sm text-ink-2">
          <Toggle label="Featured" {...form.register("is_featured")} />
          <Toggle label="Trending" {...form.register("is_trending")} />
          <Toggle label="Premium" {...form.register("is_premium")} />
        </div>
        <div className="flex justify-end">
          <button className="btn-primary" disabled={updateMut.isPending}>
            Save changes
          </button>
        </div>
      </form>

      <section className="card overflow-hidden">
        <div className="px-6 py-4 border-b border-white/5 flex items-center justify-between">
          <div className="font-display text-xl">Chapters</div>
          <div className="flex items-center gap-2">
            {draftCount > 0 && (
              <button
                className="btn-outline"
                disabled={bulkPublishMut.isPending}
                onClick={async () => {
                  if (!confirm(`Publish all ${draftCount} draft chapter(s)?`)) return;
                  try {
                    const r = (await bulkPublishMut.mutateAsync()) as { published: number };
                    alert(`Published ${r.published} chapter(s).`);
                  } catch (e) {
                    alert(extractApiError(e));
                  }
                }}
              >
                Publish all drafts ({draftCount})
              </button>
            )}
            <Link to="/ai" className="btn-outline">
              <Sparkles size={14} /> AI Studio
            </Link>
            <button className="btn-primary" onClick={() => setAdding((v) => !v)}>
              <BookPlus size={14} /> Add chapter
            </button>
          </div>
        </div>
        {adding && (
          <div className="px-6 py-3 border-b border-white/5 flex items-end gap-3">
            <Field label="Chapter number" className="w-40">
              <input
                className="input"
                type="number"
                value={newChapterNum}
                onChange={(e) => setNewChapterNum(parseInt(e.target.value, 10) || 1)}
              />
            </Field>
            <button
              className="btn-primary"
              disabled={createChapterMut.isPending}
              onClick={() => createChapterMut.mutate()}
            >
              Create draft
            </button>
          </div>
        )}
        <table className="w-full text-sm">
          <thead className="text-left text-ink-3 text-xs uppercase tracking-wide">
            <tr>
              <th className="px-6 py-3 w-16">#</th>
              <th className="px-6 py-3">Title</th>
              <th className="px-6 py-3">Status</th>
              <th className="px-6 py-3 text-right">Words</th>
              <th className="px-6 py-3 text-right">Cost</th>
              <th className="px-6 py-3">Published</th>
            </tr>
          </thead>
          <tbody>
            {(chapters.data ?? []).map((c) => (
              <tr key={c.id} className="border-t border-white/5 hover:bg-white/[0.02]">
                <td className="px-6 py-3 text-ink-3">{c.chapter_number}</td>
                <td className="px-6 py-3">
                  <Link to={`/chapters/${c.id}`} className="text-ink-1 hover:text-accent-soft">
                    {c.title || `Chapter ${c.chapter_number}`}
                  </Link>
                </td>
                <td className="px-6 py-3">
                  <StatusPill status={c.status} />
                </td>
                <td className="px-6 py-3 text-right text-ink-2">{c.word_count ?? "—"}</td>
                <td className="px-6 py-3 text-right">
                  {c.is_free ? (
                    <span className="pill bg-success/15 text-success">Free</span>
                  ) : (
                    <span className="text-gold">{c.coin_cost} 🪙</span>
                  )}
                </td>
                <td className="px-6 py-3 text-ink-3">{formatRelativeTime(c.published_at)}</td>
              </tr>
            ))}
            {!chapters.isLoading && (chapters.data ?? []).length === 0 && (
              <tr>
                <td className="px-6 py-12 text-center text-ink-3" colSpan={6}>
                  No chapters yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  );
}

function CoverUploader({
  currentUrl,
  onUploaded,
}: {
  currentUrl: string | null;
  onUploaded: (url: string) => void | Promise<void>;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  return (
    <section className="card p-4">
      <div className="flex gap-4 items-start">
        <div className="w-36 h-52 rounded-xl bg-bg-3 overflow-hidden flex-shrink-0">
          {currentUrl ? (
            <img src={currentUrl} alt="Cover" className="w-full h-full object-cover" />
          ) : (
            <div className="w-full h-full flex items-center justify-center text-ink-3 text-xs">
              No cover
            </div>
          )}
        </div>
        <div className="flex-1 space-y-2">
          <div className="font-display text-lg">Cover image</div>
          <p className="text-sm text-ink-3">JPEG, PNG, or WebP. Max 5 MB. Recommended 2:3 aspect.</p>
          <input
            ref={inputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            className="hidden"
            onChange={async (e) => {
              const file = e.target.files?.[0];
              if (!file) return;
              setError(null);
              setUploading(true);
              try {
                const fd = new FormData();
                fd.append("file", file);
                const resp = await api.post<{ data: { url: string } }>(
                  "/admin/stories/upload-cover",
                  fd,
                  { headers: { "Content-Type": "multipart/form-data" } }
                );
                await onUploaded(resp.data.data.url);
              } catch (err) {
                setError(extractApiError(err));
              } finally {
                setUploading(false);
                if (inputRef.current) inputRef.current.value = "";
              }
            }}
          />
          <button
            className="btn-outline"
            disabled={uploading}
            onClick={() => inputRef.current?.click()}
          >
            <Upload size={14} /> {uploading ? "Uploading…" : "Upload new"}
          </button>
          {error && <div className="text-xs text-danger">{error}</div>}
        </div>
      </div>
    </section>
  );
}

function Field({ label, children, className }: { label: string; children: React.ReactNode; className?: string }) {
  return (
    <label className={`block ${className ?? ""}`}>
      <div className="text-xs text-ink-2 mb-1">{label}</div>
      {children}
    </label>
  );
}

const Toggle = (
  props: React.InputHTMLAttributes<HTMLInputElement> & { label: string }
) => {
  const { label, ...rest } = props;
  return (
    <label className="inline-flex items-center gap-2 select-none">
      <input type="checkbox" className="accent-accent" {...rest} />
      <span>{label}</span>
    </label>
  );
};

function StatusPill({ status }: { status: string }) {
  const map: Record<string, string> = {
    draft: "bg-bg-3 text-ink-2",
    scheduled: "bg-warning/15 text-warning",
    published: "bg-success/15 text-success",
  };
  return <span className={`pill ${map[status] || "bg-bg-3 text-ink-2"}`}>{status}</span>;
}
