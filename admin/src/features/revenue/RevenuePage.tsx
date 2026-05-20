import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
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
  PieChart,
  Pie,
  Cell,
  Legend,
} from "recharts";
import { apiGet, apiGetWithMeta, apiPost, extractApiError } from "@/lib/api";
import { formatNumber, formatRelativeTime, formatUsdMicros } from "@/lib/utils";
import { CircleDollarSign, MinusCircle, Plus, Users } from "lucide-react";

type RevenueSummary = {
  days: number;
  gross_micros: number;
  refund_micros: number;
  net_micros: number;
  purchase_count: number;
  paying_users: number;
  arpu_micros: number;
  active_subscriptions: number;
  mrr_micros: number;
  arr_micros: number;
  series: { date: string; micros: number; purchases: number }[];
  by_sku: { sku: string; micros: number; purchases: number }[];
};

type RefundRow = {
  id: string;
  purchase_id: string;
  user_id: string;
  sku: string;
  amount_micros: number;
  currency: string | null;
  reason: string | null;
  notes: string | null;
  created_at: string;
};

const SKU_COLORS = ["#8B5CF6", "#EC4899", "#F59E0B", "#10B981", "#38BDF8", "#A78BFA", "#F472B6", "#FBBF24"];

export function RevenuePage() {
  const qc = useQueryClient();
  const [days, setDays] = useState(30);
  const [showRefund, setShowRefund] = useState(false);

  const summary = useQuery({
    queryKey: ["revenue-summary", days],
    queryFn: () => apiGet<RevenueSummary>("/admin/growth/revenue/summary", { days }),
  });
  const refunds = useQuery({
    queryKey: ["revenue-refunds"],
    queryFn: () => apiGet<RefundRow[]>("/admin/growth/revenue/refunds", { limit: 50 }),
  });

  const data = summary.data;

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between">
        <div>
          <h1 className="font-display text-3xl">Revenue</h1>
          <p className="text-sm text-ink-2">MRR, ARR, gross/net, by-SKU breakdown, refunds.</p>
        </div>
        <select
          className="input w-40"
          value={String(days)}
          onChange={(e) => setDays(parseInt(e.target.value, 10))}
        >
          <option value="7">Last 7 days</option>
          <option value="30">Last 30 days</option>
          <option value="90">Last 90 days</option>
          <option value="180">Last 180 days</option>
          <option value="365">Last 365 days</option>
        </select>
      </header>

      <section className="grid grid-cols-4 gap-3">
        <Stat
          label={`Gross (${days}d)`}
          value={formatUsdMicros(data?.gross_micros)}
          icon={<CircleDollarSign size={16} />}
          tone="default"
        />
        <Stat
          label="Refunds"
          value={formatUsdMicros(data?.refund_micros)}
          icon={<MinusCircle size={16} />}
          tone="danger"
        />
        <Stat label="Net" value={formatUsdMicros(data?.net_micros)} tone="success" />
        <Stat
          label="Paying users"
          value={formatNumber(data?.paying_users)}
          icon={<Users size={16} />}
        />
        <Stat label="MRR" value={formatUsdMicros(data?.mrr_micros)} tone="success" />
        <Stat label="ARR" value={formatUsdMicros(data?.arr_micros)} />
        <Stat label="Active subscriptions" value={formatNumber(data?.active_subscriptions)} />
        <Stat label="ARPU" value={formatUsdMicros(data?.arpu_micros)} />
      </section>

      <section className="card p-6">
        <div className="font-display text-xl mb-3">Net revenue by day</div>
        <div className="h-72">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data?.series ?? []}>
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

      <section className="grid grid-cols-2 gap-4">
        <div className="card p-6">
          <div className="font-display text-xl mb-3">Revenue by SKU</div>
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={data?.by_sku ?? []}
                  dataKey="micros"
                  nameKey="sku"
                  outerRadius={100}
                  label={(entry) => entry.sku}
                  labelLine={false}
                >
                  {(data?.by_sku ?? []).map((_, idx) => (
                    <Cell key={idx} fill={SKU_COLORS[idx % SKU_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{ background: "#14141A", border: "1px solid rgba(255,255,255,0.06)" }}
                  formatter={(v: number) => formatUsdMicros(v)}
                />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </div>
        <div className="card p-6">
          <div className="font-display text-xl mb-3">Purchases by SKU</div>
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data?.by_sku ?? []} layout="vertical">
                <CartesianGrid stroke="rgba(255,255,255,0.05)" />
                <XAxis type="number" stroke="#666680" />
                <YAxis type="category" dataKey="sku" stroke="#666680" width={140} />
                <Tooltip
                  contentStyle={{ background: "#14141A", border: "1px solid rgba(255,255,255,0.06)" }}
                  formatter={(v: number, name: string) =>
                    name === "micros" ? [formatUsdMicros(v), "Revenue"] : [v, name]
                  }
                />
                <Legend wrapperStyle={{ color: "#9999B3" }} />
                <Bar dataKey="purchases" fill="#10B981" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </section>

      <section className="card overflow-hidden">
        <div className="px-6 py-4 border-b border-white/5 flex items-center justify-between">
          <div className="font-display text-xl">Recent refunds</div>
          <button className="btn-outline" onClick={() => setShowRefund(true)}>
            <Plus size={14} /> Record refund
          </button>
        </div>
        <table className="w-full text-sm">
          <thead className="text-left text-ink-3 text-xs uppercase tracking-wide">
            <tr>
              <th className="px-6 py-3">Purchase</th>
              <th className="px-6 py-3">User</th>
              <th className="px-6 py-3">SKU</th>
              <th className="px-6 py-3 text-right">Amount</th>
              <th className="px-6 py-3">Reason</th>
              <th className="px-6 py-3">When</th>
            </tr>
          </thead>
          <tbody>
            {(refunds.data ?? []).map((r) => (
              <tr key={r.id} className="border-t border-white/5">
                <td className="px-6 py-3 font-mono text-xs text-ink-3">{r.purchase_id.slice(0, 8)}…</td>
                <td className="px-6 py-3 font-mono text-xs text-ink-3">{r.user_id.slice(0, 8)}…</td>
                <td className="px-6 py-3 text-ink-2">{r.sku}</td>
                <td className="px-6 py-3 text-right text-danger">
                  -{formatUsdMicros(r.amount_micros)}
                </td>
                <td className="px-6 py-3 text-ink-2">{r.reason || "—"}</td>
                <td className="px-6 py-3 text-ink-3">{formatRelativeTime(r.created_at)}</td>
              </tr>
            ))}
            {(refunds.data ?? []).length === 0 && (
              <tr>
                <td className="px-6 py-12 text-center text-ink-3" colSpan={6}>
                  No refunds recorded in this period.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>

      {showRefund && (
        <RefundModal
          onClose={() => setShowRefund(false)}
          onSaved={() => {
            qc.invalidateQueries({ queryKey: ["revenue-refunds"] });
            qc.invalidateQueries({ queryKey: ["revenue-summary"] });
            setShowRefund(false);
          }}
        />
      )}
    </div>
  );
}

function Stat({
  label,
  value,
  icon,
  tone,
}: {
  label: string;
  value: string;
  icon?: React.ReactNode;
  tone?: "default" | "success" | "danger";
}) {
  const color =
    tone === "success" ? "text-success" : tone === "danger" ? "text-danger" : "text-ink-0";
  return (
    <div className="card p-4">
      <div className="flex items-center gap-2 text-xs text-ink-3">
        {icon}
        <span>{label}</span>
      </div>
      <div className={`text-2xl font-display mt-1 ${color}`}>{value}</div>
    </div>
  );
}

function RefundModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [purchaseId, setPurchaseId] = useState("");
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);

  const saveMut = useMutation({
    mutationFn: () =>
      apiPost("/admin/growth/revenue/refunds", {
        purchase_id: purchaseId,
        amount_micros: Math.round(parseFloat(amount) * 1_000_000),
        reason: reason || undefined,
        notes: notes || undefined,
      }),
    onSuccess: () => onSaved(),
    onError: (e) => setError(extractApiError(e)),
  });

  return (
    <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4">
      <div className="card p-6 w-full max-w-md space-y-3">
        <div className="font-display text-xl">Record refund</div>
        <Field label="Purchase ID (UUID)">
          <input className="input font-mono text-xs" value={purchaseId} onChange={(e) => setPurchaseId(e.target.value)} />
        </Field>
        <Field label="Amount (USD)">
          <input className="input" type="number" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
        </Field>
        <Field label="Reason">
          <input className="input" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="chargeback, customer_request, …" />
        </Field>
        <Field label="Notes">
          <textarea className="input min-h-[60px]" value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
        {error && <div className="text-danger text-xs">{error}</div>}
        <div className="flex justify-end gap-2 pt-2">
          <button className="btn-outline" onClick={onClose}>Cancel</button>
          <button
            className="btn-primary"
            disabled={!purchaseId || !amount || saveMut.isPending}
            onClick={() => saveMut.mutate()}
          >
            {saveMut.isPending ? "Saving…" : "Record"}
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
