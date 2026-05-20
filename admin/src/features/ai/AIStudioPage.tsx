import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { apiGet, apiPost, extractApiError } from "@/lib/api";
import { RefreshCw, RotateCcw, Sparkles } from "lucide-react";
import { formatRelativeTime } from "@/lib/utils";

type AIJob = {
  id: string;
  type: string;
  status: "pending" | "running" | "succeeded" | "failed";
  story_id: string | null;
  chapter_id: string | null;
  model: string | null;
  tokens_in: number | null;
  tokens_out: number | null;
  cost_usd: number | null;
  error: string | null;
  started_at: string | null;
  completed_at: string | null;
  created_at: string;
};

type StoryGenForm = {
  genre: string;
  tone: string;
  setting: string;
  plot_style: string;
  chapter_count: number;
  free_chapters: number;
  notes: string;
  characters_raw: string;
  model: string;
};

export function AIStudioPage() {
  const qc = useQueryClient();
  const [tab, setTab] = useState<"story" | "chapter">("story");

  const jobs = useQuery({
    queryKey: ["admin-ai-jobs"],
    queryFn: () => apiGet<AIJob[]>("/admin/ai/jobs", { limit: 30 }),
    refetchInterval: 5_000,
  });

  return (
    <div className="space-y-6">
      <header>
        <h1 className="font-display text-3xl">AI Studio</h1>
        <p className="text-sm text-ink-2">
          Generate story outlines, then chapter drafts. All output is queued for human review before publish.
        </p>
      </header>

      <div className="flex gap-2 border-b border-white/5">
        {(["story", "chapter"] as const).map((t) => (
          <button
            key={t}
            className={`px-4 py-2 text-sm border-b-2 ${
              tab === t ? "border-accent text-ink-1" : "border-transparent text-ink-3 hover:text-ink-1"
            }`}
            onClick={() => setTab(t)}
          >
            {t === "story" ? "New story outline" : "Generate chapter"}
          </button>
        ))}
      </div>

      {tab === "story" ? <StoryGenForm onQueued={() => qc.invalidateQueries({ queryKey: ["admin-ai-jobs"] })} /> : <ChapterGenForm onQueued={() => qc.invalidateQueries({ queryKey: ["admin-ai-jobs"] })} />}

      <section className="card overflow-hidden">
        <div className="px-6 py-3 border-b border-white/5 flex items-center justify-between">
          <div className="font-display text-xl">Recent jobs</div>
          <button
            className="btn-ghost text-ink-3 text-xs"
            onClick={() => qc.invalidateQueries({ queryKey: ["admin-ai-jobs"] })}
          >
            <RefreshCw size={12} /> Refresh
          </button>
        </div>
        <table className="w-full text-sm">
          <thead className="text-left text-ink-3 text-xs uppercase tracking-wide">
            <tr>
              <th className="px-6 py-3">Type</th>
              <th className="px-6 py-3">Status</th>
              <th className="px-6 py-3">Model</th>
              <th className="px-6 py-3 text-right">Tokens (in/out)</th>
              <th className="px-6 py-3 text-right">Cost</th>
              <th className="px-6 py-3">Created</th>
              <th className="px-6 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {(jobs.data ?? []).map((j) => (
              <tr key={j.id} className="border-t border-white/5">
                <td className="px-6 py-3 text-ink-2">{j.type}</td>
                <td className="px-6 py-3">
                  <JobStatus status={j.status} error={j.error} />
                </td>
                <td className="px-6 py-3 text-ink-2 font-mono text-xs">{j.model ?? "—"}</td>
                <td className="px-6 py-3 text-right text-ink-2">
                  {(j.tokens_in ?? 0).toLocaleString()} / {(j.tokens_out ?? 0).toLocaleString()}
                </td>
                <td className="px-6 py-3 text-right text-ink-2">
                  {j.cost_usd != null ? `$${j.cost_usd.toFixed(4)}` : "—"}
                </td>
                <td className="px-6 py-3 text-ink-3">{formatRelativeTime(j.created_at)}</td>
                <td className="px-6 py-3 text-right">
                  {j.status === "failed" && (
                    <RetryButton jobId={j.id} onRetried={() => qc.invalidateQueries({ queryKey: ["admin-ai-jobs"] })} />
                  )}
                </td>
              </tr>
            ))}
            {(jobs.data ?? []).length === 0 && (
              <tr>
                <td colSpan={6} className="px-6 py-8 text-center text-ink-3">
                  No jobs yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  );
}

function JobStatus({ status, error }: { status: AIJob["status"]; error: string | null }) {
  const map = {
    pending: "bg-bg-3 text-ink-2",
    running: "bg-accent/15 text-accent-soft",
    succeeded: "bg-success/15 text-success",
    failed: "bg-danger/15 text-danger",
  } as const;
  return (
    <span className={`pill ${map[status]}`} title={error ?? undefined}>
      {status}
    </span>
  );
}

function RetryButton({ jobId, onRetried }: { jobId: string; onRetried: () => void }) {
  const mut = useMutation({
    mutationFn: () => apiPost(`/admin/ai/jobs/${jobId}/retry`, {}),
    onSuccess: onRetried,
  });
  return (
    <button
      className="btn-outline text-xs"
      disabled={mut.isPending}
      onClick={async () => {
        try {
          await mut.mutateAsync();
        } catch (e) {
          alert(extractApiError(e));
        }
      }}
    >
      <RotateCcw size={12} /> {mut.isPending ? "Retrying…" : "Retry"}
    </button>
  );
}

function StoryGenForm({ onQueued }: { onQueued: () => void }) {
  const form = useForm<StoryGenForm>({
    defaultValues: {
      genre: "dark_romance",
      tone: "emotional, slow-burn, intense",
      setting: "contemporary urban",
      plot_style: "enemies_to_lovers",
      chapter_count: 12,
      free_chapters: 3,
      notes: "",
      characters_raw:
        '[{"name":"Elena","role":"protagonist","trait":"fierce, broken inside"},{"name":"Marcus","role":"love_interest","trait":"cold billionaire with secrets"}]',
      model: "",
    },
  });

  const mut = useMutation({
    mutationFn: async (v: StoryGenForm) => {
      let characters: unknown = [];
      try {
        characters = v.characters_raw ? JSON.parse(v.characters_raw) : [];
      } catch {
        throw new Error("Characters must be valid JSON.");
      }
      return apiPost("/admin/ai/generate-story", {
        genre: v.genre,
        tone: v.tone,
        setting: v.setting,
        plot_style: v.plot_style,
        chapter_count: v.chapter_count,
        free_chapters: v.free_chapters,
        notes: v.notes,
        characters,
        model: v.model || undefined,
      });
    },
    onSuccess: () => onQueued(),
  });

  return (
    <form
      onSubmit={form.handleSubmit(async (v) => {
        try {
          await mut.mutateAsync(v);
        } catch (e) {
          alert(extractApiError(e));
        }
      })}
      className="card p-6 space-y-4"
    >
      <div className="grid grid-cols-3 gap-4">
        <Field label="Genre">
          <input className="input" {...form.register("genre")} />
        </Field>
        <Field label="Tone">
          <input className="input" {...form.register("tone")} />
        </Field>
        <Field label="Setting">
          <input className="input" {...form.register("setting")} />
        </Field>
        <Field label="Plot style">
          <input className="input" {...form.register("plot_style")} />
        </Field>
        <Field label="Chapter count">
          <input className="input" type="number" min={1} max={50} {...form.register("chapter_count", { valueAsNumber: true })} />
        </Field>
        <Field label="Free chapters">
          <input className="input" type="number" min={0} {...form.register("free_chapters", { valueAsNumber: true })} />
        </Field>
      </div>
      <Field label="Characters (JSON array)">
        <textarea className="input min-h-[100px] font-mono text-xs" {...form.register("characters_raw")} />
      </Field>
      <Field label="Notes / instructions">
        <textarea className="input min-h-[80px]" {...form.register("notes")} />
      </Field>
      <Field label="Model override (optional)">
        <input className="input font-mono" placeholder="e.g. claude-sonnet-4-6" {...form.register("model")} />
      </Field>
      <div className="flex justify-end">
        <button className="btn-primary" disabled={mut.isPending}>
          <Sparkles size={14} /> {mut.isPending ? "Queuing…" : "Generate outline"}
        </button>
      </div>
    </form>
  );
}

function ChapterGenForm({ onQueued }: { onQueued: () => void }) {
  const form = useForm({
    defaultValues: {
      story_id: "",
      chapter_number: 1,
      cliffhanger_type: "emotional",
      word_count_target: 750,
      notes: "",
      model: "",
    },
  });
  const mut = useMutation({
    mutationFn: (v: Record<string, unknown>) =>
      apiPost("/admin/ai/generate-chapter", {
        ...v,
        model: v.model || undefined,
      }),
    onSuccess: () => onQueued(),
  });

  return (
    <form
      onSubmit={form.handleSubmit(async (v) => {
        try {
          await mut.mutateAsync(v);
        } catch (e) {
          alert(extractApiError(e));
        }
      })}
      className="card p-6 space-y-4"
    >
      <div className="grid grid-cols-2 gap-4">
        <Field label="Story ID">
          <input className="input font-mono" {...form.register("story_id")} />
        </Field>
        <Field label="Chapter number">
          <input className="input" type="number" min={1} {...form.register("chapter_number", { valueAsNumber: true })} />
        </Field>
        <Field label="Cliffhanger type">
          <select className="input" {...form.register("cliffhanger_type")}>
            <option value="emotional">Emotional</option>
            <option value="action">Action</option>
            <option value="revelation">Revelation</option>
            <option value="danger">Danger</option>
          </select>
        </Field>
        <Field label="Target word count">
          <input className="input" type="number" min={300} max={2000} {...form.register("word_count_target", { valueAsNumber: true })} />
        </Field>
      </div>
      <Field label="Notes">
        <textarea className="input min-h-[80px]" {...form.register("notes")} />
      </Field>
      <Field label="Model override (optional)">
        <input className="input font-mono" {...form.register("model")} />
      </Field>
      <div className="flex justify-end">
        <button className="btn-primary" disabled={mut.isPending}>
          <Sparkles size={14} /> {mut.isPending ? "Queuing…" : "Generate chapter"}
        </button>
      </div>
    </form>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <div className="text-xs text-ink-2 mb-1">{label}</div>
      {children}
    </label>
  );
}
