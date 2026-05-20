import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiDelete, apiGet, apiPost, apiPut, extractApiError } from "@/lib/api";
import { BarChart, Bar, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis, Cell } from "recharts";
import { Beaker, Pause, Play, Plus, StopCircle, Trash2 } from "lucide-react";
import { formatNumber, formatRelativeTime } from "@/lib/utils";

type Variant = { key: string; weight: number; config?: Record<string, unknown> | null };

type Experiment = {
  id: string;
  key: string;
  name: string;
  description: string | null;
  status: "draft" | "running" | "paused" | "completed";
  variants: Variant[];
  segment_id: string | null;
  started_at: string | null;
  ended_at: string | null;
  created_at: string;
  updated_at: string;
  breakdown?: { variant: string; assigned: number; converted: number; conversion_rate: number }[];
};

type Segment = { id: string; name: string };

const STATUS_STYLE: Record<Experiment["status"], string> = {
  draft: "bg-bg-3 text-ink-2",
  running: "bg-success/15 text-success",
  paused: "bg-warning/15 text-warning",
  completed: "bg-accent/15 text-accent-soft",
};

const VARIANT_COLORS = ["#8B5CF6", "#EC4899", "#F59E0B", "#10B981", "#38BDF8"];

export function ExperimentsPage() {
  const qc = useQueryClient();
  const list = useQuery({
    queryKey: ["experiments"],
    queryFn: () => apiGet<Experiment[]>("/admin/growth/experiments"),
  });
  const segments = useQuery({
    queryKey: ["segments"],
    queryFn: () => apiGet<Segment[]>("/admin/growth/segments"),
  });

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [showNew, setShowNew] = useState(false);

  const detail = useQuery({
    queryKey: ["experiment", selectedId],
    queryFn: () => apiGet<Experiment>(`/admin/growth/experiments/${selectedId}`),
    enabled: !!selectedId,
  });

  // Auto-select first experiment
  useEffect(() => {
    if (!selectedId && list.data && list.data.length > 0) {
      setSelectedId(list.data[0].id);
    }
  }, [list.data, selectedId]);

  const statusMut = useMutation({
    mutationFn: ({ id, status }: { id: string; status: Experiment["status"] }) =>
      apiPut(`/admin/growth/experiments/${id}`, { status }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["experiments"] });
      qc.invalidateQueries({ queryKey: ["experiment", selectedId] });
    },
  });

  const deleteMut = useMutation({
    mutationFn: (id: string) => apiDelete(`/admin/growth/experiments/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["experiments"] });
      setSelectedId(null);
    },
  });

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between">
        <div>
          <h1 className="font-display text-3xl">A/B experiments</h1>
          <p className="text-sm text-ink-2">
            Define variants, split traffic, and track conversion. Assignments are sticky.
          </p>
        </div>
        <button className="btn-primary" onClick={() => setShowNew(true)}>
          <Plus size={14} /> New experiment
        </button>
      </header>

      <div className="grid grid-cols-3 gap-4">
        <section className="card overflow-hidden col-span-1">
          <ul className="divide-y divide-white/5">
            {(list.data ?? []).map((e) => (
              <li
                key={e.id}
                className={`px-4 py-3 cursor-pointer hover:bg-white/[0.03] ${
                  selectedId === e.id ? "bg-accent/10" : ""
                }`}
                onClick={() => setSelectedId(e.id)}
              >
                <div className="flex items-center justify-between">
                  <div className="text-ink-1 font-medium">{e.name}</div>
                  <span className={`pill ${STATUS_STYLE[e.status]}`}>{e.status}</span>
                </div>
                <div className="text-xs text-ink-3 font-mono mt-1">{e.key}</div>
              </li>
            ))}
            {list.data && list.data.length === 0 && (
              <li className="px-4 py-12 text-center text-ink-3 text-sm">
                No experiments yet.
              </li>
            )}
          </ul>
        </section>

        <section className="col-span-2">
          {detail.data ? (
            <ExperimentDetail
              experiment={detail.data}
              segments={segments.data ?? []}
              onStatusChange={(status) =>
                statusMut.mutate({ id: detail.data!.id, status })
              }
              onDelete={() => {
                if (confirm(`Delete "${detail.data!.name}"? Assignments will be lost.`)) {
                  deleteMut.mutate(detail.data!.id);
                }
              }}
              onSaved={() => {
                qc.invalidateQueries({ queryKey: ["experiments"] });
                qc.invalidateQueries({ queryKey: ["experiment", selectedId] });
              }}
            />
          ) : (
            <div className="card p-12 text-center text-ink-3 text-sm">
              {list.isLoading ? "Loading…" : "Select an experiment or create a new one."}
            </div>
          )}
        </section>
      </div>

      {showNew && (
        <NewExperimentModal
          segments={segments.data ?? []}
          onClose={() => setShowNew(false)}
          onCreated={(created) => {
            qc.invalidateQueries({ queryKey: ["experiments"] });
            setSelectedId(created.id);
            setShowNew(false);
          }}
        />
      )}
    </div>
  );
}

function ExperimentDetail({
  experiment,
  segments,
  onStatusChange,
  onDelete,
  onSaved,
}: {
  experiment: Experiment;
  segments: Segment[];
  onStatusChange: (s: Experiment["status"]) => void;
  onDelete: () => void;
  onSaved: () => void;
}) {
  const [name, setName] = useState(experiment.name);
  const [description, setDescription] = useState(experiment.description ?? "");
  const [variants, setVariants] = useState<Variant[]>(experiment.variants);
  const [segmentId, setSegmentId] = useState<string | null>(experiment.segment_id);
  useEffect(() => {
    setName(experiment.name);
    setDescription(experiment.description ?? "");
    setVariants(experiment.variants);
    setSegmentId(experiment.segment_id);
  }, [experiment.id]);

  const saveMut = useMutation({
    mutationFn: () =>
      apiPut(`/admin/growth/experiments/${experiment.id}`, {
        name,
        description,
        variants,
        segment_id: segmentId,
      }),
    onSuccess: () => onSaved(),
  });

  const totalWeight = variants.reduce((sum, v) => sum + (v.weight || 0), 0);
  const breakdown = experiment.breakdown ?? [];
  const totalAssigned = breakdown.reduce((sum, b) => sum + b.assigned, 0);

  return (
    <div className="space-y-4">
      <div className="card p-6 space-y-3">
        <div className="flex items-start justify-between">
          <div className="flex-1">
            <div className="flex items-center gap-3 mb-2">
              <input
                className="input text-2xl font-display"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
              <span className={`pill ${STATUS_STYLE[experiment.status]}`}>{experiment.status}</span>
            </div>
            <div className="text-xs text-ink-3 font-mono">
              key: {experiment.key} · created {formatRelativeTime(experiment.created_at)}
              {experiment.started_at && ` · started ${formatRelativeTime(experiment.started_at)}`}
            </div>
          </div>
          <div className="flex gap-2">
            {experiment.status !== "running" ? (
              <button
                className="btn-primary"
                onClick={() => onStatusChange("running")}
                disabled={totalWeight !== 100}
                title={totalWeight !== 100 ? "Variant weights must sum to 100 — save first" : ""}
              >
                <Play size={14} /> Start
              </button>
            ) : (
              <>
                <button className="btn-outline" onClick={() => onStatusChange("paused")}>
                  <Pause size={14} /> Pause
                </button>
                <button className="btn-outline" onClick={() => onStatusChange("completed")}>
                  <StopCircle size={14} /> Complete
                </button>
              </>
            )}
            <button className="btn-outline text-danger" onClick={onDelete}>
              <Trash2 size={14} /> Delete
            </button>
          </div>
        </div>

        <Field label="Description">
          <textarea
            className="input min-h-[60px]"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </Field>

        <Field label="Segment scope (optional)">
          <select
            className="input"
            value={segmentId ?? ""}
            onChange={(e) => setSegmentId(e.target.value || null)}
          >
            <option value="">All users</option>
            {segments.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </Field>

        <div>
          <div className="text-xs text-ink-2 mb-1">Variants (weights must sum to 100)</div>
          <VariantsEditor variants={variants} onChange={setVariants} />
          {totalWeight !== 100 && (
            <div className="text-xs text-warning mt-1">Total weight: {totalWeight} (must be 100)</div>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-2 border-t border-white/5">
          <button
            className="btn-primary"
            disabled={!name.trim() || totalWeight !== 100 || saveMut.isPending}
            onClick={async () => {
              try {
                await saveMut.mutateAsync();
              } catch (e) {
                alert(extractApiError(e));
              }
            }}
          >
            {saveMut.isPending ? "Saving…" : "Save changes"}
          </button>
        </div>
      </div>

      {breakdown.length > 0 && (
        <div className="card p-6">
          <div className="flex items-center gap-2 mb-3">
            <Beaker size={16} className="text-accent" />
            <div className="font-display text-xl">Results</div>
            <span className="text-xs text-ink-3 ml-2">
              {formatNumber(totalAssigned)} users assigned
            </span>
          </div>
          <div className="h-48 mb-4">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={breakdown}>
                <CartesianGrid stroke="rgba(255,255,255,0.05)" />
                <XAxis dataKey="variant" stroke="#666680" />
                <YAxis stroke="#666680" tickFormatter={(v) => `${(v * 100).toFixed(0)}%`} />
                <Tooltip
                  contentStyle={{ background: "#14141A", border: "1px solid rgba(255,255,255,0.06)" }}
                  formatter={(v: number) => `${(v * 100).toFixed(2)}%`}
                />
                <Bar dataKey="conversion_rate" radius={[6, 6, 0, 0]}>
                  {breakdown.map((_, idx) => (
                    <Cell key={idx} fill={VARIANT_COLORS[idx % VARIANT_COLORS.length]} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
          <table className="w-full text-sm">
            <thead className="text-left text-ink-3 text-xs uppercase tracking-wide">
              <tr>
                <th className="px-3 py-2">Variant</th>
                <th className="px-3 py-2 text-right">Assigned</th>
                <th className="px-3 py-2 text-right">Converted</th>
                <th className="px-3 py-2 text-right">Conversion rate</th>
              </tr>
            </thead>
            <tbody>
              {breakdown.map((b) => (
                <tr key={b.variant} className="border-t border-white/5">
                  <td className="px-3 py-2 text-ink-1 font-medium">{b.variant}</td>
                  <td className="px-3 py-2 text-right text-ink-2">{formatNumber(b.assigned)}</td>
                  <td className="px-3 py-2 text-right text-ink-2">{formatNumber(b.converted)}</td>
                  <td className="px-3 py-2 text-right text-accent-soft">
                    {(b.conversion_rate * 100).toFixed(2)}%
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function VariantsEditor({
  variants,
  onChange,
}: {
  variants: Variant[];
  onChange: (next: Variant[]) => void;
}) {
  const update = (idx: number, patch: Partial<Variant>) => {
    onChange(variants.map((v, i) => (i === idx ? { ...v, ...patch } : v)));
  };
  const remove = (idx: number) => onChange(variants.filter((_, i) => i !== idx));
  const add = () => onChange([...variants, { key: `variant_${variants.length}`, weight: 0 }]);

  return (
    <div className="space-y-2">
      {variants.map((v, idx) => (
        <div key={idx} className="flex items-center gap-2">
          <div
            className="w-2 h-10 rounded"
            style={{ background: VARIANT_COLORS[idx % VARIANT_COLORS.length] }}
          />
          <input
            className="input flex-1"
            value={v.key}
            onChange={(e) => update(idx, { key: e.target.value })}
            placeholder="control"
          />
          <input
            className="input w-24"
            type="number"
            min={0}
            max={100}
            value={v.weight}
            onChange={(e) => update(idx, { weight: parseInt(e.target.value, 10) || 0 })}
          />
          <span className="text-ink-3 text-sm">%</span>
          {variants.length > 2 && (
            <button className="btn-outline" onClick={() => remove(idx)} title="Remove variant">
              <Trash2 size={14} />
            </button>
          )}
        </div>
      ))}
      <button className="btn-outline" onClick={add} disabled={variants.length >= 10}>
        <Plus size={14} /> Add variant
      </button>
    </div>
  );
}

function NewExperimentModal({
  segments,
  onClose,
  onCreated,
}: {
  segments: Segment[];
  onClose: () => void;
  onCreated: (created: Experiment) => void;
}) {
  const [key, setKey] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [variants, setVariants] = useState<Variant[]>([
    { key: "control", weight: 50 },
    { key: "treatment", weight: 50 },
  ]);
  const [segmentId, setSegmentId] = useState<string | null>(null);

  const createMut = useMutation({
    mutationFn: () =>
      apiPost<Experiment>("/admin/growth/experiments", {
        key,
        name,
        description: description || null,
        variants,
        segment_id: segmentId,
      }),
    onSuccess: (created) => onCreated(created),
  });

  const totalWeight = variants.reduce((sum, v) => sum + v.weight, 0);

  return (
    <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4">
      <div className="card p-6 w-full max-w-xl space-y-3 max-h-[90vh] overflow-y-auto">
        <div className="font-display text-xl">New experiment</div>
        <Field label="Key (slug, lowercase + underscores)">
          <input
            className="input font-mono text-sm"
            value={key}
            onChange={(e) => setKey(e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, "_"))}
            placeholder="paywall_v2"
          />
        </Field>
        <Field label="Name">
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Description">
          <textarea
            className="input min-h-[60px]"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </Field>
        <Field label="Segment scope (optional)">
          <select
            className="input"
            value={segmentId ?? ""}
            onChange={(e) => setSegmentId(e.target.value || null)}
          >
            <option value="">All users</option>
            {segments.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </Field>
        <div>
          <div className="text-xs text-ink-2 mb-1">Variants</div>
          <VariantsEditor variants={variants} onChange={setVariants} />
          {totalWeight !== 100 && (
            <div className="text-xs text-warning mt-1">Total weight: {totalWeight} (must be 100)</div>
          )}
        </div>
        {createMut.error && (
          <div className="text-danger text-xs">{extractApiError(createMut.error)}</div>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <button className="btn-outline" onClick={onClose}>Cancel</button>
          <button
            className="btn-primary"
            disabled={!key || !name || totalWeight !== 100 || createMut.isPending}
            onClick={() => createMut.mutate()}
          >
            {createMut.isPending ? "Creating…" : "Create draft"}
          </button>
        </div>
      </div>
    </div>
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
