import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { apiGetWithMeta } from "@/lib/api";
import { formatRelativeTime } from "@/lib/utils";

type AuditEntry = {
  id: string;
  actor_admin_id: string | null;
  action: string;
  target_type: string | null;
  target_id: string | null;
  metadata: Record<string, unknown> | null;
  ip_address: string | null;
  created_at: string;
};

export function AuditPage() {
  const [page, setPage] = useState(1);
  const [action, setAction] = useState("");
  const [targetType, setTargetType] = useState("");

  const { data } = useQuery({
    queryKey: ["audit", { page, action, targetType }],
    queryFn: () =>
      apiGetWithMeta<AuditEntry[]>("/admin/audit", {
        page,
        per_page: 50,
        action: action || undefined,
        target_type: targetType || undefined,
      }),
  });

  return (
    <div className="space-y-6">
      <header>
        <h1 className="font-display text-3xl">Audit log</h1>
        <p className="text-sm text-ink-2">Every admin action is recorded here. Read-only.</p>
      </header>
      <div className="card p-3 flex items-center gap-3">
        <input
          className="input w-64"
          placeholder="Filter by action…"
          value={action}
          onChange={(e) => setAction(e.target.value)}
        />
        <select
          className="input w-44"
          value={targetType}
          onChange={(e) => setTargetType(e.target.value)}
        >
          <option value="">Any target</option>
          <option value="story">Story</option>
          <option value="chapter">Chapter</option>
          <option value="user">User</option>
          <option value="config">Config</option>
          <option value="comment">Comment</option>
          <option value="report">Report</option>
        </select>
      </div>
      <section className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="text-left text-ink-3 text-xs uppercase tracking-wide">
            <tr>
              <th className="px-6 py-3">When</th>
              <th className="px-6 py-3">Action</th>
              <th className="px-6 py-3">Target</th>
              <th className="px-6 py-3">Actor</th>
              <th className="px-6 py-3">IP</th>
            </tr>
          </thead>
          <tbody>
            {(data?.data ?? []).map((e) => (
              <tr key={e.id} className="border-t border-white/5 align-top">
                <td className="px-6 py-2.5 text-ink-3 whitespace-nowrap">
                  {formatRelativeTime(e.created_at)}
                </td>
                <td className="px-6 py-2.5 text-ink-1 font-mono text-xs">{e.action}</td>
                <td className="px-6 py-2.5 text-ink-2">
                  <div className="capitalize">{e.target_type}</div>
                  <div className="text-xs text-ink-3 font-mono">{e.target_id}</div>
                </td>
                <td className="px-6 py-2.5 text-ink-3 font-mono text-xs">
                  {e.actor_admin_id ? `${e.actor_admin_id.slice(0, 8)}…` : "system"}
                </td>
                <td className="px-6 py-2.5 text-ink-3 font-mono text-xs">{e.ip_address || "—"}</td>
              </tr>
            ))}
            {(data?.data ?? []).length === 0 && (
              <tr>
                <td colSpan={5} className="px-6 py-12 text-center text-ink-3">
                  No entries.
                </td>
              </tr>
            )}
          </tbody>
        </table>
        <div className="px-6 py-3 border-t border-white/5 flex items-center justify-between text-xs text-ink-3">
          <span>Page {page}</span>
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
    </div>
  );
}
