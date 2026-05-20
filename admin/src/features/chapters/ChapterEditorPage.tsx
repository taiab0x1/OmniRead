import { useParams, Link, useNavigate } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { apiGet, apiPut, apiPost, apiDelete, extractApiError } from "@/lib/api";
import { ArrowLeft, Save, Trash2 } from "lucide-react";

type ChapterDetail = {
  id: string;
  story_id: string;
  chapter_number: number;
  title: string | null;
  content: string;
  word_count: number | null;
  is_free: boolean;
  coin_cost: number;
  has_cliffhanger: boolean;
  cliffhanger_preview: string | null;
  status: string;
  published_at: string | null;
};

export function ChapterEditorPage() {
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();
  const navigate = useNavigate();

  const ch = useQuery({
    queryKey: ["admin-chapter", id],
    queryFn: () => apiGet<ChapterDetail>(`/admin/chapters/${id}`),
    enabled: !!id,
  });

  const updateMut = useMutation({
    mutationFn: (body: Partial<ChapterDetail>) => apiPut(`/admin/chapters/${id}`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-chapter", id] }),
  });
  const publishMut = useMutation({
    mutationFn: () => apiPost(`/admin/chapters/${id}/publish`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-chapter", id] }),
  });
  const deleteMut = useMutation({
    mutationFn: () => apiDelete(`/admin/chapters/${id}`),
    onSuccess: (_d, _v, ctx) => {
      if (ch.data?.story_id) navigate(`/stories/${ch.data.story_id}`);
    },
  });

  const form = useForm<Partial<ChapterDetail>>({ values: ch.data });

  if (ch.isLoading || !ch.data) return <div className="text-ink-3">Loading…</div>;
  const c = ch.data;
  const wordCount = (form.watch("content") ?? c.content).split(/\s+/).filter(Boolean).length;

  return (
    <div className="space-y-6">
      <div>
        <Link to={`/stories/${c.story_id}`} className="text-ink-3 hover:text-ink-1 inline-flex items-center gap-1 text-sm">
          <ArrowLeft size={14} /> Story
        </Link>
      </div>

      <header className="flex items-start justify-between gap-6">
        <div>
          <h1 className="font-display text-3xl">
            Chapter {c.chapter_number} <span className="text-ink-3">·</span>{" "}
            <span className="text-ink-1">{c.title || "Untitled"}</span>
          </h1>
          <div className="text-sm text-ink-3 mt-1">
            {wordCount.toLocaleString()} words · status:{" "}
            <span className="text-ink-1">{c.status}</span>
          </div>
        </div>
        <div className="flex gap-2">
          <button
            className="btn-outline text-danger hover:bg-danger/10"
            onClick={() => {
              if (confirm("Delete this chapter? This cannot be undone.")) deleteMut.mutate();
            }}
          >
            <Trash2 size={14} /> Delete
          </button>
          {c.status !== "published" && (
            <button className="btn-primary" onClick={() => publishMut.mutate()}>
              Publish
            </button>
          )}
        </div>
      </header>

      <form
        onSubmit={form.handleSubmit(async (values) => {
          try {
            await updateMut.mutateAsync(values);
          } catch (e) {
            alert(extractApiError(e));
          }
        })}
        className="space-y-5"
      >
        <div className="card p-6 space-y-4">
          <div className="grid grid-cols-3 gap-4">
            <label>
              <div className="text-xs text-ink-2 mb-1">Title</div>
              <input className="input" {...form.register("title")} />
            </label>
            <label>
              <div className="text-xs text-ink-2 mb-1">Coin cost</div>
              <input className="input" type="number" {...form.register("coin_cost", { valueAsNumber: true })} />
            </label>
            <label className="inline-flex items-center gap-3 pt-6">
              <input type="checkbox" className="accent-accent" {...form.register("is_free")} />
              <span>Free chapter</span>
            </label>
          </div>
          <label>
            <div className="text-xs text-ink-2 mb-1">Cliffhanger preview (shown when locked)</div>
            <textarea
              className="input min-h-[80px]"
              {...form.register("cliffhanger_preview")}
              placeholder="A teaser shown to non-unlocked readers"
            />
          </label>
        </div>

        <div className="card p-0 overflow-hidden">
          <div className="px-6 py-3 border-b border-white/5 text-xs text-ink-3 uppercase tracking-wide flex items-center justify-between">
            <span>Content</span>
            <span>{wordCount.toLocaleString()} words</span>
          </div>
          <textarea
            className="w-full bg-transparent border-0 outline-none px-6 py-5 text-ink-1 leading-relaxed font-serif resize-y min-h-[420px] focus:ring-0"
            {...form.register("content")}
          />
        </div>

        <div className="flex justify-end">
          <button className="btn-primary" disabled={updateMut.isPending}>
            <Save size={14} />
            {updateMut.isPending ? "Saving…" : "Save chapter"}
          </button>
        </div>
      </form>
    </div>
  );
}
