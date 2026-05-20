import { useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { apiGetWithMeta } from "@/lib/api";
import { formatRelativeTime } from "@/lib/utils";

type UserRow = {
  id: string;
  email: string | null;
  username: string;
  is_guest: boolean;
  is_banned: boolean;
  coin_balance: number;
  subscription_tier: string;
  subscription_expires_at: string | null;
  created_at: string;
};

export function UsersListPage() {
  const [page, setPage] = useState(1);
  const [q, setQ] = useState("");
  const [tier, setTier] = useState("");
  const [banned, setBanned] = useState<string>("");

  const { data, isLoading } = useQuery({
    queryKey: ["admin-users", { page, q, tier, banned }],
    queryFn: () =>
      apiGetWithMeta<UserRow[]>("/admin/users", {
        page,
        per_page: 20,
        q: q || undefined,
        tier: tier || undefined,
        banned: banned === "" ? undefined : banned === "true",
      }),
  });

  return (
    <div className="space-y-5">
      <header>
        <h1 className="font-display text-3xl">Users</h1>
        <p className="text-sm text-ink-2">Search, ban, adjust coins, and audit reading activity.</p>
      </header>

      <div className="card p-3 flex items-center gap-3">
        <div className="relative flex-1">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-3" />
          <input className="input pl-9" placeholder="Email or username…" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <select className="input w-44" value={tier} onChange={(e) => setTier(e.target.value)}>
          <option value="">All tiers</option>
          <option value="free">Free</option>
          <option value="premium">Premium</option>
        </select>
        <select className="input w-44" value={banned} onChange={(e) => setBanned(e.target.value)}>
          <option value="">Any status</option>
          <option value="false">Active</option>
          <option value="true">Banned</option>
        </select>
      </div>

      <section className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="text-left text-ink-3 text-xs uppercase tracking-wide">
            <tr>
              <th className="px-6 py-3">User</th>
              <th className="px-6 py-3">Email</th>
              <th className="px-6 py-3">Tier</th>
              <th className="px-6 py-3 text-right">Coins</th>
              <th className="px-6 py-3">Status</th>
              <th className="px-6 py-3">Joined</th>
            </tr>
          </thead>
          <tbody>
            {(data?.data ?? []).map((u) => (
              <tr key={u.id} className="border-t border-white/5 hover:bg-white/[0.02]">
                <td className="px-6 py-3">
                  <Link to={`/users/${u.id}`} className="text-ink-1 hover:text-accent-soft font-medium">
                    {u.username}
                  </Link>
                  {u.is_guest && <span className="pill bg-bg-3 text-ink-2 ml-2">Guest</span>}
                </td>
                <td className="px-6 py-3 text-ink-2">{u.email || "—"}</td>
                <td className="px-6 py-3">
                  <span className={`pill ${u.subscription_tier === "premium" ? "bg-gold/15 text-gold" : "bg-bg-3 text-ink-2"}`}>
                    {u.subscription_tier}
                  </span>
                </td>
                <td className="px-6 py-3 text-right text-gold">{u.coin_balance.toLocaleString()}</td>
                <td className="px-6 py-3">
                  {u.is_banned ? (
                    <span className="pill bg-danger/15 text-danger">Banned</span>
                  ) : (
                    <span className="pill bg-success/15 text-success">Active</span>
                  )}
                </td>
                <td className="px-6 py-3 text-ink-3">{formatRelativeTime(u.created_at)}</td>
              </tr>
            ))}
            {!isLoading && (data?.data ?? []).length === 0 && (
              <tr>
                <td className="px-6 py-12 text-center text-ink-3" colSpan={6}>
                  No users found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
        <div className="px-6 py-3 border-t border-white/5 flex items-center justify-between text-xs text-ink-3">
          <span>Total {data?.meta?.total ?? 0}</span>
          <div className="flex gap-2">
            <button className="btn-outline" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>Prev</button>
            <button className="btn-outline" onClick={() => setPage((p) => p + 1)}>Next</button>
          </div>
        </div>
      </section>
    </div>
  );
}
