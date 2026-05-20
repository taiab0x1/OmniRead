import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiGetWithMeta, apiPost } from "@/lib/api";
import { Calendar, Clock, Rocket } from "lucide-react";
import { formatRelativeTime } from "@/lib/utils";

type ScheduledStory = {
  id: string;
  title: string;
  genre: string;
  status: string;
  scheduled_at: string | null;
  total_chapters: number;
};

export function SchedulingPage() {
  const qc = useQueryClient();
  const { data } = useQuery({
    queryKey: ["admin-stories-scheduled"],
    queryFn: () => apiGetWithMeta<ScheduledStory[]>("/admin/stories", { status: "draft,scheduled", per_page: 100 }),
  });

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [scheduleDate, setScheduleDate] = useState("");
  const [scheduleTime, setScheduleTime] = useState("09:00");

  const scheduleMut = useMutation({
    mutationFn: async () => {
      if (!selectedId || !scheduleDate) return;
      const dt = `${scheduleDate}T${scheduleTime}:00Z`;
      await apiPost(`/admin/stories/${selectedId}/schedule`, { scheduled_at: dt });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["admin-stories-scheduled"] });
      setSelectedId(null);
      setScheduleDate("");
    },
  });

  const publishNowMut = useMutation({
    mutationFn: (id: string) => apiPost(`/admin/stories/${id}/publish`, {}),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-stories-scheduled"] }),
  });

  const drafts = (data?.data ?? []).filter((s) => s.status === "draft" || s.status === "scheduled");

  return (
    <div className="space-y-6">
      <header>
        <h1 className="font-display text-3xl">Content Scheduling</h1>
        <p className="text-sm text-ink-2">Schedule draft stories for future publication. Celery beat checks every 5 minutes.</p>
      </header>

      <div className="card p-6 space-y-4">
        <div className="font-display text-xl flex items-center gap-2"><Calendar size={18} /> Schedule a story</div>
        <div className="grid grid-cols-3 gap-4">
          <label>
            <div className="text-xs text-ink-2 mb-1">Draft story</div>
            <select className="input" value={selectedId ?? ""} onChange={(e) => setSelectedId(e.target.value || null)}>
              <option value="">Select a draft…</option>
              {drafts.map((s) => (
                <option key={s.id} value={s.id}>{s.title} ({s.total_chapters} ch)</option>
              ))}
            </select>
          </label>
          <label>
            <div className="text-xs text-ink-2 mb-1">Date</div>
            <input className="input" type="date" value={scheduleDate} onChange={(e) => setScheduleDate(e.target.value)} />
          </label>
          <label>
            <div className="text-xs text-ink-2 mb-1">Time (UTC)</div>
            <input className="input" type="time" value={scheduleTime} onChange={(e) => setScheduleTime(e.target.value)} />
          </label>
        </div>
        <div className="flex justify-end">
          <button
            className="btn-primary"
            disabled={!selectedId || !scheduleDate || scheduleMut.isPending}
            onClick={() => scheduleMut.mutate()}
          >
            <Clock size={14} /> Schedule
          </button>
        </div>
      </div>

      <section className="card overflow-hidden">
        <div className="px-6 py-4 border-b border-white/5 font-display text-xl">Draft stories</div>
        <table className="w-full text-sm">
          <thead className="text-left text-ink-3 text-xs uppercase tracking-wide">
            <tr>
              <th className="px-6 py-3">Title</th>
              <th className="px-6 py-3">Genre</th>
              <th className="px-6 py-3">Chapters</th>
              <th className="px-6 py-3">Scheduled</th>
              <th className="px-6 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {drafts.map((s) => (
              <tr key={s.id} className="border-t border-white/5">
                <td className="px-6 py-3 text-ink-1">{s.title}</td>
                <td className="px-6 py-3 text-ink-2 capitalize">{s.genre.replace("_", " ")}</td>
                <td className="px-6 py-3 text-ink-2">{s.total_chapters}</td>
                <td className="px-6 py-3 text-ink-2">{s.scheduled_at ? formatRelativeTime(s.scheduled_at) : "—"}</td>
                <td className="px-6 py-3 text-right">
                  <button
                    className="btn-outline"
                    onClick={() => publishNowMut.mutate(s.id)}
                    disabled={publishNowMut.isPending}
                  >
                    <Rocket size={12} /> Publish now
                  </button>
                </td>
              </tr>
            ))}
            {drafts.length === 0 && (
              <tr><td colSpan={5} className="px-6 py-12 text-center text-ink-3">No drafts to schedule.</td></tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  );
}
