import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Eye, EyeOff, MessageSquareReply, ShieldCheck } from "lucide-react";
import { apiGet, apiPost, apiPut, extractApiError } from "@/lib/api";
import { formatRelativeTime } from "@/lib/utils";

type CommentRow = {
  id: string;
  user_id: string | null;
  admin_id: string | null;
  chapter_id: string;
  content: string;
  is_hidden: boolean;
  is_spoiler: boolean;
  moderation_status: string;
  created_at: string;
};

type ReportRow = {
  id: string;
  reporter_id: string | null;
  target_type: string;
  target_id: string;
  reason: string;
  notes: string | null;
  status: string;
  created_at: string;
};

export function ModerationPage() {
  const [tab, setTab] = useState<"reports" | "comments">("reports");
  return (
    <div className="space-y-6">
      <header>
        <h1 className="font-display text-3xl">Moderation</h1>
        <p className="text-sm text-ink-2">Reports SLA: 48 hours. Hidden content stays visible to authors only.</p>
      </header>
      <div className="flex gap-2 border-b border-white/5">
        {(["reports", "comments"] as const).map((t) => (
          <button
            key={t}
            className={`px-4 py-2 text-sm border-b-2 capitalize ${
              tab === t ? "border-accent text-ink-1" : "border-transparent text-ink-3 hover:text-ink-1"
            }`}
            onClick={() => setTab(t)}
          >
            {t}
          </button>
        ))}
      </div>
      {tab === "reports" ? <Reports /> : <Comments />}
    </div>
  );
}

function Reports() {
  const qc = useQueryClient();
  const [status, setStatus] = useState("pending");
  const reports = useQuery({
    queryKey: ["mod-reports", status],
    queryFn: () => apiGet<ReportRow[]>("/admin/moderation/reports", { status, limit: 100 }),
  });
  const resolveMut = useMutation({
    mutationFn: ({ id, action, notes }: { id: string; action: string; notes?: string }) =>
      apiPut(`/admin/moderation/reports/${id}/resolve`, { action, notes }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["mod-reports"] }),
  });

  return (
    <div className="space-y-3">
      <select className="input w-48" value={status} onChange={(e) => setStatus(e.target.value)}>
        <option value="pending">Pending</option>
        <option value="resolved">Resolved</option>
        <option value="dismissed">Dismissed</option>
      </select>
      <section className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="text-left text-ink-3 text-xs uppercase tracking-wide">
            <tr>
              <th className="px-6 py-3">Target</th>
              <th className="px-6 py-3">Reason</th>
              <th className="px-6 py-3">Notes</th>
              <th className="px-6 py-3">Reported</th>
              <th className="px-6 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {(reports.data ?? []).map((r) => (
              <tr key={r.id} className="border-t border-white/5 align-top">
                <td className="px-6 py-3">
                  <div className="text-ink-1 capitalize">{r.target_type}</div>
                  <div className="text-xs text-ink-3 font-mono">{r.target_id}</div>
                </td>
                <td className="px-6 py-3 text-ink-2 capitalize">{r.reason.replace("_", " ")}</td>
                <td className="px-6 py-3 text-ink-2 max-w-md">{r.notes || "—"}</td>
                <td className="px-6 py-3 text-ink-3">{formatRelativeTime(r.created_at)}</td>
                <td className="px-6 py-3 text-right space-x-2">
                  {status === "pending" && (
                    <>
                      <button
                        className="btn-outline"
                        onClick={() => resolveMut.mutate({ id: r.id, action: "dismissed" })}
                      >
                        Dismiss
                      </button>
                      <button
                        className="btn-primary"
                        onClick={() => resolveMut.mutate({ id: r.id, action: "resolved" })}
                      >
                        <ShieldCheck size={14} /> Resolve
                      </button>
                    </>
                  )}
                </td>
              </tr>
            ))}
            {(reports.data ?? []).length === 0 && (
              <tr>
                <td colSpan={5} className="px-6 py-12 text-center text-ink-3">
                  No reports in this state.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  );
}

function Comments() {
  const qc = useQueryClient();
  const [hidden, setHidden] = useState<string>("false");
  const [replyOpen, setReplyOpen] = useState<string | null>(null);
  const [replyText, setReplyText] = useState("");
  const comments = useQuery({
    queryKey: ["mod-comments", hidden],
    queryFn: () =>
      apiGet<CommentRow[]>("/admin/moderation/comments", {
        moderation_status: "approved",
        is_hidden: hidden,
        limit: 100,
      }),
  });
  const hideMut = useMutation({
    mutationFn: (id: string) => apiPut(`/admin/moderation/comments/${id}/hide`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["mod-comments"] }),
  });
  const restoreMut = useMutation({
    mutationFn: (id: string) => apiPut(`/admin/moderation/comments/${id}/restore`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["mod-comments"] }),
  });
  const replyMut = useMutation({
    mutationFn: ({ chapterId, parentId, content }: { chapterId: string; parentId: string; content: string }) =>
      apiPost("/admin/moderation/comments/reply", {
        chapter_id: chapterId,
        parent_id: parentId,
        content,
      }),
    onSuccess: () => {
      setReplyOpen(null);
      setReplyText("");
      qc.invalidateQueries({ queryKey: ["mod-comments"] });
    },
  });

  return (
    <div className="space-y-3">
      <select className="input w-48" value={hidden} onChange={(e) => setHidden(e.target.value)}>
        <option value="false">Visible</option>
        <option value="true">Hidden</option>
      </select>
      <section className="space-y-3">
        {(comments.data ?? []).map((c) => (
          <div key={c.id} className="card p-4">
            <div className="flex gap-4">
              <div className="flex-1">
                <div className="text-xs text-ink-3 mb-2 font-mono">
                  {c.admin_id ? "admin reply · " : `user ${c.user_id?.slice(0, 8) ?? "?"}… · `}
                  chapter {c.chapter_id.slice(0, 8)}… · {formatRelativeTime(c.created_at)}
                </div>
                <p className="text-ink-1 whitespace-pre-wrap">{c.content}</p>
                {c.is_spoiler && <span className="pill bg-warning/15 text-warning mt-2">Spoiler</span>}
                {c.admin_id && <span className="pill bg-accent/15 text-accent-soft mt-2">Author</span>}
              </div>
              <div className="flex flex-col gap-2">
                {!c.is_hidden && (
                  <button
                    className="btn-outline"
                    onClick={() => {
                      setReplyOpen(replyOpen === c.id ? null : c.id);
                      setReplyText("");
                    }}
                  >
                    <MessageSquareReply size={14} /> Reply
                  </button>
                )}
                {c.is_hidden ? (
                  <button className="btn-outline" onClick={() => restoreMut.mutate(c.id)}>
                    <Eye size={14} /> Restore
                  </button>
                ) : (
                  <button className="btn-outline text-danger" onClick={() => hideMut.mutate(c.id)}>
                    <EyeOff size={14} /> Hide
                  </button>
                )}
              </div>
            </div>
            {replyOpen === c.id && (
              <div className="mt-3 pt-3 border-t border-white/5">
                <textarea
                  className="input min-h-[80px]"
                  placeholder="Reply as Author…"
                  value={replyText}
                  onChange={(e) => setReplyText(e.target.value)}
                />
                <div className="mt-2 flex justify-end gap-2">
                  <button
                    className="btn-outline"
                    onClick={() => {
                      setReplyOpen(null);
                      setReplyText("");
                    }}
                  >
                    Cancel
                  </button>
                  <button
                    className="btn-primary"
                    disabled={!replyText.trim() || replyMut.isPending}
                    onClick={async () => {
                      try {
                        await replyMut.mutateAsync({
                          chapterId: c.chapter_id,
                          parentId: c.id,
                          content: replyText.trim(),
                        });
                      } catch (e) {
                        alert(extractApiError(e));
                      }
                    }}
                  >
                    Send reply
                  </button>
                </div>
              </div>
            )}
          </div>
        ))}
        {(comments.data ?? []).length === 0 && (
          <div className="card p-12 text-center text-ink-3">No comments to review.</div>
        )}
      </section>
    </div>
  );
}
