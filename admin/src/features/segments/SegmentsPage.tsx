import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiDelete, apiGet, apiPost, apiPut, extractApiError } from "@/lib/api";
import { Plus, Trash2, Users } from "lucide-react";
import { formatNumber, formatRelativeTime } from "@/lib/utils";
import { useGenres } from "@/hooks/useGenres";

type SegmentFilter = {
  subscription_tier?: string[];
  is_guest?: boolean;
  preferred_genres_any?: string[];
  locale?: string[];
  region?: string[];
  min_reading_streak?: number;
  min_coin_balance?: number;
  max_coin_balance?: number;
  days_since_signup_min?: number;
  days_since_signup_max?: number;
  days_since_last_login_max?: number;
};

type Segment = {
  id: string;
  name: string;
  description: string | null;
  filter: SegmentFilter;
  cached_user_count: number | null;
  cached_at: string | null;
  created_at: string;
  updated_at: string;
};

type PreviewResult = {
  count: number;
  sample: { id: string; username: string; tier: string }[];
};

const EMPTY_FILTER: SegmentFilter = {};

export function SegmentsPage() {
  const qc = useQueryClient();
  const segments = useQuery({
    queryKey: ["segments"],
    queryFn: () => apiGet<Segment[]>("/admin/growth/segments"),
  });

  const [selected, setSelected] = useState<Segment | null>(null);
  const [draft, setDraft] = useState<{
    name: string;
    description: string;
    filter: SegmentFilter;
    id: string | null;
  }>({ name: "", description: "", filter: EMPTY_FILTER, id: null });

  useEffect(() => {
    if (selected) {
      setDraft({
        id: selected.id,
        name: selected.name,
        description: selected.description ?? "",
        filter: selected.filter ?? {},
      });
    }
  }, [selected]);

  const previewMut = useMutation({
    mutationFn: () =>
      apiPost<PreviewResult>("/admin/growth/segments/preview", {
        name: draft.name || "preview",
        description: draft.description || null,
        filter: draft.filter,
      }),
  });

  const saveMut = useMutation({
    mutationFn: () => {
      const body = {
        name: draft.name,
        description: draft.description || null,
        filter: draft.filter,
      };
      return draft.id
        ? apiPut<Segment>(`/admin/growth/segments/${draft.id}`, body)
        : apiPost<Segment>("/admin/growth/segments", body);
    },
    onSuccess: (saved) => {
      qc.invalidateQueries({ queryKey: ["segments"] });
      setSelected(saved);
    },
  });

  const deleteMut = useMutation({
    mutationFn: (id: string) => apiDelete(`/admin/growth/segments/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["segments"] });
      setSelected(null);
      setDraft({ name: "", description: "", filter: EMPTY_FILTER, id: null });
    },
  });

  return (
    <div className="space-y-6">
      <header>
        <h1 className="font-display text-3xl">User segments</h1>
        <p className="text-sm text-ink-2">
          Build user filters once, reuse them in notifications and experiments.
        </p>
      </header>

      <div className="grid grid-cols-3 gap-4">
        <section className="card overflow-hidden col-span-1">
          <div className="px-4 py-3 border-b border-white/5 flex items-center justify-between">
            <div className="text-sm text-ink-2">Saved segments</div>
            <button
              className="btn-outline"
              onClick={() => {
                setSelected(null);
                setDraft({ name: "", description: "", filter: EMPTY_FILTER, id: null });
              }}
            >
              <Plus size={14} /> New
            </button>
          </div>
          <ul className="divide-y divide-white/5">
            {(segments.data ?? []).map((s) => (
              <li
                key={s.id}
                className={`px-4 py-3 cursor-pointer hover:bg-white/[0.03] ${
                  selected?.id === s.id ? "bg-accent/10" : ""
                }`}
                onClick={() => setSelected(s)}
              >
                <div className="text-ink-1 font-medium">{s.name}</div>
                <div className="text-xs text-ink-3 flex items-center gap-2 mt-1">
                  <Users size={12} />
                  {s.cached_user_count == null
                    ? "—"
                    : `${formatNumber(s.cached_user_count)} users`}
                  <span>· {formatRelativeTime(s.updated_at)}</span>
                </div>
              </li>
            ))}
            {segments.data && segments.data.length === 0 && (
              <li className="px-4 py-12 text-center text-ink-3 text-sm">
                No segments yet. Build one →
              </li>
            )}
          </ul>
        </section>

        <section className="card p-6 col-span-2 space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <Field label="Name">
              <input
                className="input"
                value={draft.name}
                onChange={(e) => setDraft((d) => ({ ...d, name: e.target.value }))}
                placeholder="VIP renewal candidates"
              />
            </Field>
            <Field label="Description">
              <input
                className="input"
                value={draft.description}
                onChange={(e) => setDraft((d) => ({ ...d, description: e.target.value }))}
              />
            </Field>
          </div>

          <FilterBuilder
            filter={draft.filter}
            onChange={(filter) => setDraft((d) => ({ ...d, filter }))}
          />

          <div className="flex items-center justify-between pt-3 border-t border-white/5">
            <div>
              <button
                className="btn-outline"
                disabled={previewMut.isPending}
                onClick={() => previewMut.mutate()}
              >
                {previewMut.isPending ? "Counting…" : "Preview count"}
              </button>
              {previewMut.data && (
                <span className="ml-3 text-sm text-ink-1">
                  <strong>{formatNumber(previewMut.data.count)}</strong> users match
                  {previewMut.data.sample.length > 0 && (
                    <span className="text-ink-3 ml-2">
                      e.g. {previewMut.data.sample.map((s) => s.username).join(", ")}
                    </span>
                  )}
                </span>
              )}
              {previewMut.error && (
                <span className="ml-3 text-sm text-danger">{extractApiError(previewMut.error)}</span>
              )}
            </div>
            <div className="flex gap-2">
              {draft.id && (
                <button
                  className="btn-outline text-danger"
                  disabled={deleteMut.isPending}
                  onClick={() => {
                    if (confirm(`Delete segment "${draft.name}"?`)) deleteMut.mutate(draft.id!);
                  }}
                >
                  <Trash2 size={14} /> Delete
                </button>
              )}
              <button
                className="btn-primary"
                disabled={!draft.name.trim() || saveMut.isPending}
                onClick={async () => {
                  try {
                    await saveMut.mutateAsync();
                  } catch (e) {
                    alert(extractApiError(e));
                  }
                }}
              >
                {saveMut.isPending ? "Saving…" : draft.id ? "Save changes" : "Create segment"}
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}

function FilterBuilder({
  filter,
  onChange,
}: {
  filter: SegmentFilter;
  onChange: (next: SegmentFilter) => void;
}) {
  const genres = useGenres();
  const setKey = <K extends keyof SegmentFilter>(key: K, value: SegmentFilter[K] | undefined) => {
    const next = { ...filter };
    const isEmpty =
      value === undefined ||
      (typeof value === "string" && value === "") ||
      (Array.isArray(value) && value.length === 0);
    if (isEmpty) {
      delete next[key];
    } else {
      next[key] = value;
    }
    onChange(next);
  };

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-4">
        <Field label="Subscription tier">
          <MultiCheck
            options={["free", "vip", "premium"]}
            value={filter.subscription_tier ?? []}
            onChange={(v) => setKey("subscription_tier", v)}
          />
        </Field>
        <Field label="Guest status">
          <select
            className="input"
            value={
              filter.is_guest === true
                ? "guest"
                : filter.is_guest === false
                ? "registered"
                : ""
            }
            onChange={(e) => {
              if (e.target.value === "guest") setKey("is_guest", true);
              else if (e.target.value === "registered") setKey("is_guest", false);
              else setKey("is_guest", undefined);
            }}
          >
            <option value="">Any</option>
            <option value="guest">Guest only</option>
            <option value="registered">Registered only</option>
          </select>
        </Field>
      </div>

      <Field label="Preferred genres (matches ANY)">
        <MultiCheck
          options={genres}
          value={filter.preferred_genres_any ?? []}
          onChange={(v) => setKey("preferred_genres_any", v)}
        />
      </Field>

      <div className="grid grid-cols-3 gap-3">
        <Field label="Min reading streak (days)">
          <NumberInput
            value={filter.min_reading_streak}
            onChange={(v) => setKey("min_reading_streak", v)}
          />
        </Field>
        <Field label="Min coin balance">
          <NumberInput
            value={filter.min_coin_balance}
            onChange={(v) => setKey("min_coin_balance", v)}
          />
        </Field>
        <Field label="Max coin balance">
          <NumberInput
            value={filter.max_coin_balance}
            onChange={(v) => setKey("max_coin_balance", v)}
          />
        </Field>
      </div>

      <div className="grid grid-cols-3 gap-3">
        <Field label="Signed up ≥ N days ago">
          <NumberInput
            value={filter.days_since_signup_min}
            onChange={(v) => setKey("days_since_signup_min", v)}
          />
        </Field>
        <Field label="Signed up ≤ N days ago">
          <NumberInput
            value={filter.days_since_signup_max}
            onChange={(v) => setKey("days_since_signup_max", v)}
          />
        </Field>
        <Field label="Active in last N days">
          <NumberInput
            value={filter.days_since_last_login_max}
            onChange={(v) => setKey("days_since_last_login_max", v)}
          />
        </Field>
      </div>

      <Field label="Locales (comma-separated, blank = all)">
        <input
          className="input"
          value={(filter.locale ?? []).join(", ")}
          onChange={(e) =>
            setKey(
              "locale",
              e.target.value
                .split(",")
                .map((s) => s.trim())
                .filter(Boolean)
            )
          }
          placeholder="en, es, fr"
        />
      </Field>
    </div>
  );
}

function MultiCheck({
  options,
  value,
  onChange,
}: {
  options: string[];
  value: string[];
  onChange: (next: string[]) => void;
}) {
  const toggle = (opt: string) => {
    onChange(value.includes(opt) ? value.filter((v) => v !== opt) : [...value, opt]);
  };
  return (
    <div className="flex flex-wrap gap-1.5">
      {options.map((opt) => {
        const on = value.includes(opt);
        return (
          <button
            key={opt}
            type="button"
            className={`pill cursor-pointer ${
              on ? "bg-accent text-white" : "bg-bg-3 text-ink-2 hover:text-ink-1"
            }`}
            onClick={() => toggle(opt)}
          >
            {opt.replace("_", " ")}
          </button>
        );
      })}
    </div>
  );
}

function NumberInput({
  value,
  onChange,
}: {
  value: number | undefined;
  onChange: (v: number | undefined) => void;
}) {
  return (
    <input
      className="input"
      type="number"
      min={0}
      value={value ?? ""}
      onChange={(e) => {
        const raw = e.target.value;
        if (raw === "") onChange(undefined);
        else {
          const n = parseInt(raw, 10);
          if (!isNaN(n) && n >= 0) onChange(n);
        }
      }}
    />
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
