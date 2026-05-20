import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { apiGet, apiPost, extractApiError } from "@/lib/api";
import { Send } from "lucide-react";
import { useGenres } from "@/hooks/useGenres";
import { formatNumber } from "@/lib/utils";

type Segment = {
  id: string;
  name: string;
  cached_user_count: number | null;
};

export function NotificationsPage() {
  const genres = useGenres();
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [targetMode, setTargetMode] = useState<"all" | "genre" | "segment">("all");
  const [targetGenre, setTargetGenre] = useState("");
  const [segmentId, setSegmentId] = useState("");
  const [sent, setSent] = useState<string | null>(null);

  const segments = useQuery({
    queryKey: ["segments"],
    queryFn: () => apiGet<Segment[]>("/admin/growth/segments"),
  });

  const broadcastMut = useMutation({
    mutationFn: () =>
      apiPost<{ sent: number; target_count: number }>("/admin/notifications/broadcast", {
        title,
        body,
        type: "broadcast",
        target_genre: targetMode === "genre" ? targetGenre || undefined : undefined,
        segment_id: targetMode === "segment" ? segmentId || undefined : undefined,
      }),
    onSuccess: (r) => {
      setSent(`Notification queued for ${formatNumber(r.sent)} users.`);
      setTitle("");
      setBody("");
    },
    onError: (e) => setSent(extractApiError(e)),
  });

  return (
    <div className="space-y-6">
      <header>
        <h1 className="font-display text-3xl">Push Notifications</h1>
        <p className="text-sm text-ink-2">Broadcast to all users, a genre audience, or a saved segment.</p>
      </header>

      <div className="card p-6 space-y-4">
        <div className="font-display text-xl">Broadcast</div>
        <div className="grid grid-cols-2 gap-4">
          <label className="col-span-2">
            <div className="text-xs text-ink-2 mb-1">Title (max 120 chars)</div>
            <input
              className="input"
              maxLength={120}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="New chapter alert!"
            />
          </label>
          <label className="col-span-2">
            <div className="text-xs text-ink-2 mb-1">Body (max 500 chars)</div>
            <textarea
              className="input min-h-[100px]"
              maxLength={500}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="Your favorite story just got a new chapter..."
            />
          </label>
          <div className="col-span-2">
            <div className="text-xs text-ink-2 mb-1">Audience</div>
            <div className="flex gap-2">
              <button
                className={`pill cursor-pointer ${
                  targetMode === "all" ? "bg-accent text-white" : "bg-bg-3 text-ink-2"
                }`}
                onClick={() => setTargetMode("all")}
              >
                All users
              </button>
              <button
                className={`pill cursor-pointer ${
                  targetMode === "genre" ? "bg-accent text-white" : "bg-bg-3 text-ink-2"
                }`}
                onClick={() => setTargetMode("genre")}
              >
                By genre
              </button>
              <button
                className={`pill cursor-pointer ${
                  targetMode === "segment" ? "bg-accent text-white" : "bg-bg-3 text-ink-2"
                }`}
                onClick={() => setTargetMode("segment")}
              >
                By segment
              </button>
            </div>
          </div>
          {targetMode === "genre" && (
            <label className="col-span-2">
              <div className="text-xs text-ink-2 mb-1">Genre</div>
              <select className="input" value={targetGenre} onChange={(e) => setTargetGenre(e.target.value)}>
                <option value="">— select genre —</option>
                {genres.map((g) => (
                  <option key={g} value={g}>{g.replace("_", " ")}</option>
                ))}
              </select>
            </label>
          )}
          {targetMode === "segment" && (
            <label className="col-span-2">
              <div className="text-xs text-ink-2 mb-1">Segment</div>
              <select className="input" value={segmentId} onChange={(e) => setSegmentId(e.target.value)}>
                <option value="">— select segment —</option>
                {(segments.data ?? []).map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                    {s.cached_user_count != null && ` (~${formatNumber(s.cached_user_count)} users)`}
                  </option>
                ))}
              </select>
              {segments.data?.length === 0 && (
                <div className="text-xs text-ink-3 mt-1">
                  No segments saved yet. Build one in Segments.
                </div>
              )}
            </label>
          )}
        </div>
        {sent && (
          <div className={`text-sm ${sent.includes("queued") ? "text-success" : "text-danger"}`}>{sent}</div>
        )}
        <div className="flex justify-end">
          <button
            className="btn-primary"
            disabled={
              !title ||
              !body ||
              broadcastMut.isPending ||
              (targetMode === "genre" && !targetGenre) ||
              (targetMode === "segment" && !segmentId)
            }
            onClick={() => broadcastMut.mutate()}
          >
            <Send size={14} /> {broadcastMut.isPending ? "Sending…" : "Send broadcast"}
          </button>
        </div>
      </div>

      <div className="card p-6 space-y-3">
        <div className="font-display text-xl">Notification Rules</div>
        <p className="text-sm text-ink-2">
          Automated notifications are configured via App Config → <code>notification_rules</code> key.
        </p>
        <ul className="text-sm text-ink-2 list-disc pl-5 space-y-1">
          <li><strong>new_chapter</strong> — sent when a bookmarked story gets a new published chapter</li>
          <li><strong>streak_reminder</strong> — sent at 7pm local if user hasn't opened app today</li>
          <li><strong>personalized_rec</strong> — AI-picked story for user's preferred genres (max 1/day)</li>
          <li><strong>coin_offer</strong> — special bonus event (max 2/week)</li>
        </ul>
        <p className="text-sm text-ink-3">Edit rules in Config → notification_rules. Changes apply on next Celery beat cycle.</p>
      </div>
    </div>
  );
}
