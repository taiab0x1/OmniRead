import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiPost, apiPut, apiDelete, extractApiError } from "@/lib/api";
import { Plus, Trash2, Save } from "lucide-react";

type CoinPkg = {
  id: string;
  sku: string;
  name: string;
  coins: number;
  bonus_coins: number;
  price_usd: number;
  is_best_value: boolean;
  is_active: boolean;
  sort_order: number;
};

export function CoinPackagesPage() {
  const qc = useQueryClient();
  const { data: packages, isLoading } = useQuery({
    queryKey: ["admin-coin-packages"],
    queryFn: () => apiGet<CoinPkg[]>("/admin/coins/packages"),
  });

  const [editing, setEditing] = useState<CoinPkg | null>(null);
  const [adding, setAdding] = useState(false);

  const saveMut = useMutation({
    mutationFn: (pkg: Record<string, unknown>) =>
      editing
        ? apiPut(`/admin/coins/packages/${editing.id}`, pkg)
        : apiPost("/admin/coins/packages", pkg),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin-coin-packages"] });
      setAdding(false);
      setEditing(null);
    },
  });

  const deactivateMut = useMutation({
    mutationFn: (id: string) => apiDelete(`/admin/coins/packages/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-coin-packages"] }),
  });

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between">
        <div>
          <h1 className="font-display text-3xl">Coin Packages</h1>
          <p className="text-sm text-ink-2">Manage in-app purchase coin packs. Changes reflect immediately in the app.</p>
        </div>
        <button className="btn-primary" onClick={() => setAdding(true)}>
          <Plus size={16} /> Add package
        </button>
      </header>

      {adding && (
        <PackageForm
          onSave={async (pkg) => {
            await saveMut.mutateAsync(pkg);
          }}
          onCancel={() => setAdding(false)}
        />
      )}

      {editing && (
        <PackageForm
          initial={editing}
          onSave={async (pkg) => {
            await saveMut.mutateAsync(pkg);
          }}
          onCancel={() => setEditing(null)}
        />
      )}

      <section className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="text-left text-ink-3 text-xs uppercase tracking-wide">
            <tr>
              <th className="px-6 py-3">SKU</th>
              <th className="px-6 py-3">Name</th>
              <th className="px-6 py-3 text-right">Coins</th>
              <th className="px-6 py-3 text-right">Bonus</th>
              <th className="px-6 py-3 text-right">Price (USD)</th>
              <th className="px-6 py-3">Best Value</th>
              <th className="px-6 py-3">Active</th>
              <th className="px-6 py-3">Order</th>
              <th className="px-6 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {(packages ?? []).map((pkg) => (
              <tr key={pkg.id} className="border-t border-white/5">
                <td className="px-6 py-3 font-mono text-xs">{pkg.sku}</td>
                <td className="px-6 py-3 text-ink-1">{pkg.name}</td>
                <td className="px-6 py-3 text-right text-gold">{pkg.coins}</td>
                <td className="px-6 py-3 text-right text-success">{pkg.bonus_coins || 0}</td>
                <td className="px-6 py-3 text-right">${pkg.price_usd}</td>
                <td className="px-6 py-3">{pkg.is_best_value ? <span className="pill bg-gold/15 text-gold">Yes</span> : "—"}</td>
                <td className="px-6 py-3">{pkg.is_active ? <span className="pill bg-success/15 text-success">Active</span> : <span className="pill bg-bg-3 text-ink-3">Inactive</span>}</td>
                <td className="px-6 py-3 text-ink-2">{pkg.sort_order}</td>
                <td className="px-6 py-3 text-right">
                  <button className="btn-ghost text-xs" onClick={() => setEditing(pkg)}>
                    Edit
                  </button>
                  {pkg.is_active && (
                    <button
                      className="btn-ghost text-xs text-danger"
                      disabled={deactivateMut.isPending}
                      onClick={() => {
                        if (confirm(`Deactivate ${pkg.name}?`)) deactivateMut.mutate(pkg.id);
                      }}
                    >
                      <Trash2 size={12} /> Deactivate
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {!isLoading && (packages ?? []).length === 0 && (
              <tr><td colSpan={9} className="px-6 py-12 text-center text-ink-3">No packages configured.</td></tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  );
}

function PackageForm({
  initial,
  onSave,
  onCancel,
}: {
  initial?: CoinPkg;
  onSave: (pkg: Record<string, unknown>) => Promise<void>;
  onCancel: () => void;
}) {
  const [form, setForm] = useState({
    sku: initial?.sku ?? "",
    name: initial?.name ?? "",
    coins: initial?.coins ?? 100,
    bonus_coins: initial?.bonus_coins ?? 0,
    price_usd: initial?.price_usd ?? 1.99,
    is_best_value: initial?.is_best_value ?? false,
    is_active: initial?.is_active ?? true,
    sort_order: initial?.sort_order ?? 0,
  });
  return (
    <div className="card p-6 space-y-4">
      <div className="font-display text-xl">{initial ? "Edit package" : "Add package"}</div>
      <div className="grid grid-cols-4 gap-4">
        <label><div className="text-xs text-ink-2 mb-1">SKU</div><input className="input font-mono" value={form.sku} onChange={(e) => setForm({ ...form, sku: e.target.value })} /></label>
        <label><div className="text-xs text-ink-2 mb-1">Name</div><input className="input" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
        <label><div className="text-xs text-ink-2 mb-1">Coins</div><input className="input" type="number" value={form.coins} onChange={(e) => setForm({ ...form, coins: +e.target.value })} /></label>
        <label><div className="text-xs text-ink-2 mb-1">Bonus</div><input className="input" type="number" value={form.bonus_coins} onChange={(e) => setForm({ ...form, bonus_coins: +e.target.value })} /></label>
        <label><div className="text-xs text-ink-2 mb-1">Price USD</div><input className="input" type="number" step="0.01" value={form.price_usd} onChange={(e) => setForm({ ...form, price_usd: +e.target.value })} /></label>
        <label><div className="text-xs text-ink-2 mb-1">Sort order</div><input className="input" type="number" value={form.sort_order} onChange={(e) => setForm({ ...form, sort_order: +e.target.value })} /></label>
        <label className="flex items-center gap-2 pt-6"><input type="checkbox" className="accent-accent" checked={form.is_best_value} onChange={(e) => setForm({ ...form, is_best_value: e.target.checked })} /><span className="text-sm">Best Value</span></label>
        <label className="flex items-center gap-2 pt-6"><input type="checkbox" className="accent-accent" checked={form.is_active} onChange={(e) => setForm({ ...form, is_active: e.target.checked })} /><span className="text-sm">Active</span></label>
      </div>
      <div className="flex gap-2 justify-end">
        <button className="btn-ghost" onClick={onCancel}>Cancel</button>
        <button className="btn-primary" onClick={() => onSave(form)} disabled={!form.sku || !form.name}><Save size={14} /> Save</button>
      </div>
    </div>
  );
}
