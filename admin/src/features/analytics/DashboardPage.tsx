import { useQuery } from "@tanstack/react-query";
import { apiGet } from "@/lib/api";
import { Activity, Users, BookOpen, DollarSign, Sparkles, AlertTriangle, Crown, type LucideIcon } from "lucide-react";
import { formatNumber } from "@/lib/utils";

type Dashboard = {
  dau: number;
  new_users_today: number;
  stories_published: number;
  stories_published_today: number;
  active_subscriptions: number;
  coins_credited_today: number;
  ai_jobs_pending: number;
  ai_jobs_failed_today: number;
  as_of: string;
};

export function DashboardPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["admin-dashboard"],
    queryFn: () => apiGet<Dashboard>("/admin/analytics/dashboard"),
    refetchInterval: 60_000,
  });

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between">
        <div>
          <h1 className="font-display text-3xl">Dashboard</h1>
          <p className="text-sm text-ink-2">Live metrics, refreshed every minute.</p>
        </div>
        <div className="text-xs text-ink-3">
          {data ? `as of ${new Date(data.as_of).toLocaleTimeString()}` : ""}
        </div>
      </header>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Stat icon={Activity} label="Daily Active Users" value={data?.dau} loading={isLoading} />
        <Stat icon={Users} label="New users today" value={data?.new_users_today} loading={isLoading} />
        <Stat icon={Crown} label="Active subscriptions" value={data?.active_subscriptions} loading={isLoading} accent="gold" />
        <Stat icon={DollarSign} label="Coins sold today" value={data?.coins_credited_today} loading={isLoading} />
        <Stat icon={BookOpen} label="Stories published" value={data?.stories_published} loading={isLoading} />
        <Stat icon={Sparkles} label="Stories published today" value={data?.stories_published_today} loading={isLoading} />
        <Stat icon={Sparkles} label="AI jobs pending" value={data?.ai_jobs_pending} loading={isLoading} />
        <Stat icon={AlertTriangle} label="AI failures today" value={data?.ai_jobs_failed_today} loading={isLoading} accent="danger" />
      </div>

      <section className="card p-6">
        <div className="font-display text-xl mb-1">What's next</div>
        <ul className="text-sm text-ink-2 space-y-2 list-disc pl-5">
          <li>Use AI Studio to generate new story outlines for the catalog.</li>
          <li>Review the moderation queue daily; reports get a 48h SLA.</li>
          <li>Watch for sustained spikes in AI failures — usually a provider outage.</li>
        </ul>
      </section>
    </div>
  );
}

function Stat({
  icon: Icon,
  label,
  value,
  loading,
  accent,
}: {
  icon: LucideIcon;
  label: string;
  value: number | undefined;
  loading: boolean;
  accent?: "gold" | "danger";
}) {
  const accentClass =
    accent === "gold" ? "text-gold" : accent === "danger" ? "text-danger" : "text-accent-soft";
  return (
    <div className="card p-4">
      <div className="flex items-center justify-between text-ink-3 text-xs uppercase tracking-wide">
        <span>{label}</span>
        <Icon size={14} />
      </div>
      <div className={`mt-3 text-2xl font-semibold ${accentClass}`}>
        {loading ? <span className="text-ink-3">—</span> : formatNumber(value ?? 0)}
      </div>
    </div>
  );
}
