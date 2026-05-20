import { useParams, Link } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { ArrowLeft, Ban, Coins, LogOut } from "lucide-react";
import { apiGet, apiPut, apiPost, extractApiError } from "@/lib/api";
import { formatRelativeTime } from "@/lib/utils";

type UserDetail = {
  id: string;
  email: string | null;
  username: string;
  is_guest: boolean;
  is_verified: boolean;
  is_banned: boolean;
  ban_reason: string | null;
  coin_balance: number;
  subscription_tier: string;
  subscription_expires_at: string | null;
  reading_streak: number;
  created_at: string;
  subscription: {
    sku: string;
    state: string;
    expires_at: string;
    auto_renewing: boolean;
  } | null;
  recent_transactions: {
    id: string;
    amount: number;
    type: string;
    balance_after: number;
    created_at: string;
  }[];
};

export function UserDetailPage() {
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();

  const { data: u } = useQuery({
    queryKey: ["admin-user", id],
    queryFn: () => apiGet<UserDetail>(`/admin/users/${id}`),
    enabled: !!id,
  });

  const banMut = useMutation({
    mutationFn: (reason: string) => apiPut(`/admin/users/${id}/ban`, { reason }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-user", id] }),
  });
  const unbanMut = useMutation({
    mutationFn: () => apiPut(`/admin/users/${id}/unban`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-user", id] }),
  });
  const adjustMut = useMutation({
    mutationFn: (body: { delta: number; reason: string }) =>
      apiPost(`/admin/users/${id}/coins/adjust`, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-user", id] }),
  });
  const forceLogoutMut = useMutation({
    mutationFn: () => apiPost(`/admin/users/${id}/force-logout`, {}),
  });

  const [delta, setDelta] = useState(0);
  const [adjReason, setAdjReason] = useState("");
  const [banReason, setBanReason] = useState("");

  if (!u) return <div className="text-ink-3">Loading…</div>;

  return (
    <div className="space-y-6">
      <div>
        <Link to="/users" className="text-ink-3 hover:text-ink-1 inline-flex items-center gap-1 text-sm">
          <ArrowLeft size={14} /> Users
        </Link>
      </div>

      <header className="flex items-start justify-between">
        <div>
          <h1 className="font-display text-3xl">{u.username}</h1>
          <div className="text-sm text-ink-3 mt-1">{u.email}</div>
          <div className="flex gap-2 mt-2">
            {u.is_guest && <span className="pill bg-bg-3 text-ink-2">Guest</span>}
            {u.is_verified && <span className="pill bg-success/15 text-success">Verified</span>}
            {u.is_banned && <span className="pill bg-danger/15 text-danger">Banned</span>}
            <span className={`pill ${u.subscription_tier === "premium" ? "bg-gold/15 text-gold" : "bg-bg-3 text-ink-2"}`}>
              {u.subscription_tier}
            </span>
          </div>
        </div>
        <div className="flex gap-2">
          <button className="btn-outline" onClick={() => forceLogoutMut.mutate()}>
            <LogOut size={14} /> Force logout
          </button>
        </div>
      </header>

      <div className="grid grid-cols-3 gap-4">
        <Stat label="Coin balance" value={u.coin_balance.toLocaleString()} />
        <Stat label="Reading streak" value={String(u.reading_streak)} />
        <Stat label="Joined" value={formatRelativeTime(u.created_at)} />
      </div>

      <section className="card p-6 space-y-4">
        <div className="font-display text-xl flex items-center gap-2">
          <Coins size={18} /> Coin adjustment
        </div>
        <div className="flex items-end gap-3">
          <label className="flex-1">
            <div className="text-xs text-ink-2 mb-1">Delta (positive credits, negative debits)</div>
            <input
              className="input"
              type="number"
              value={delta}
              onChange={(e) => setDelta(parseInt(e.target.value, 10) || 0)}
            />
          </label>
          <label className="flex-[2]">
            <div className="text-xs text-ink-2 mb-1">Reason</div>
            <input className="input" value={adjReason} onChange={(e) => setAdjReason(e.target.value)} />
          </label>
          <button
            className="btn-primary"
            disabled={!delta || !adjReason || adjustMut.isPending}
            onClick={async () => {
              try {
                await adjustMut.mutateAsync({ delta, reason: adjReason });
                setDelta(0);
                setAdjReason("");
              } catch (e) {
                alert(extractApiError(e));
              }
            }}
          >
            Adjust
          </button>
        </div>
      </section>

      <section className="card p-6 space-y-4">
        <div className="font-display text-xl flex items-center gap-2">
          <Ban size={18} /> Account access
        </div>
        {u.is_banned ? (
          <div className="space-y-3">
            <div className="text-sm text-ink-2">
              Banned. Reason: <span className="text-ink-1">{u.ban_reason || "(none)"}</span>
            </div>
            <button className="btn-outline" onClick={() => unbanMut.mutate()}>
              Unban user
            </button>
          </div>
        ) : (
          <div className="flex items-end gap-3">
            <label className="flex-1">
              <div className="text-xs text-ink-2 mb-1">Ban reason</div>
              <input className="input" value={banReason} onChange={(e) => setBanReason(e.target.value)} />
            </label>
            <button
              className="btn-outline text-danger hover:bg-danger/10"
              disabled={!banReason || banReason.length < 3}
              onClick={() => {
                if (confirm("Ban this user?")) banMut.mutate(banReason);
              }}
            >
              Ban user
            </button>
          </div>
        )}
      </section>

      <section className="card overflow-hidden">
        <div className="px-6 py-4 border-b border-white/5 font-display text-xl">Recent coin transactions</div>
        <table className="w-full text-sm">
          <thead className="text-left text-ink-3 text-xs uppercase tracking-wide">
            <tr>
              <th className="px-6 py-3">Type</th>
              <th className="px-6 py-3 text-right">Amount</th>
              <th className="px-6 py-3 text-right">Balance after</th>
              <th className="px-6 py-3">When</th>
            </tr>
          </thead>
          <tbody>
            {u.recent_transactions.map((t) => (
              <tr key={t.id} className="border-t border-white/5">
                <td className="px-6 py-2.5 text-ink-2">{t.type}</td>
                <td className={`px-6 py-2.5 text-right ${t.amount >= 0 ? "text-success" : "text-danger"}`}>
                  {t.amount > 0 ? `+${t.amount}` : t.amount}
                </td>
                <td className="px-6 py-2.5 text-right text-gold">{t.balance_after}</td>
                <td className="px-6 py-2.5 text-ink-3">{formatRelativeTime(t.created_at)}</td>
              </tr>
            ))}
            {u.recent_transactions.length === 0 && (
              <tr>
                <td colSpan={4} className="px-6 py-8 text-center text-ink-3">
                  No transactions yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="card p-4">
      <div className="text-xs text-ink-3 uppercase tracking-wide">{label}</div>
      <div className="mt-2 text-2xl font-display">{value}</div>
    </div>
  );
}
