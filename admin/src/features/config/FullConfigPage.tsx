import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiPut, extractApiError } from "@/lib/api";
import { Save, ToggleLeft, ToggleRight } from "lucide-react";

type AdConfig = {
  rewarded_ads_enabled: boolean;
  rewarded_cooldown_minutes: number;
  coins_per_rewarded_ad: number;
  interstitial_enabled: boolean;
  interstitial_chapter_interval: number;
  banner_enabled: boolean;
  daily_ad_cap: number;
};

type CoinConfig = {
  chapter_base_cost: number;
  premium_chapter_cost: number;
  daily_login_coins: number;
  streak_bonus_multiplier: number;
};

type ContentConfig = {
  free_chapters_default: number;
  feed_algorithm: string;
};

type FeatureFlags = {
  comments_enabled: boolean;
  audio_enabled: boolean;
  offline_mode_enabled: boolean;
  interactive_stories_enabled: boolean;
};

export function FullConfigPage() {
  const qc = useQueryClient();
  const { data: allConfig } = useQuery({
    queryKey: ["admin-config"],
    queryFn: () => apiGet<Record<string, unknown>>("/admin/config"),
  });

  const adConfig = (allConfig?.ad_config ?? {}) as AdConfig;
  const coinConfig = (allConfig?.coin_config ?? {}) as CoinConfig;
  const contentConfig = (allConfig?.content_config ?? {}) as ContentConfig;
  const featureFlags = (allConfig?.feature_flags ?? {}) as FeatureFlags;

  return (
    <div className="space-y-6">
      <header>
        <h1 className="font-display text-3xl">App Configuration</h1>
        <p className="text-sm text-ink-2">Live remote config. Changes apply on next app poll (60s).</p>
      </header>

      <ConfigSection title="Ad Configuration" configKey="ad_config" initial={adConfig} qc={qc}>
        {(form, setField) => (
          <div className="grid grid-cols-3 gap-4">
            <Toggle label="Rewarded Ads Enabled" value={form.rewarded_ads_enabled} onChange={(v) => setField("rewarded_ads_enabled", v)} />
            <Toggle label="Interstitial Enabled" value={form.interstitial_enabled} onChange={(v) => setField("interstitial_enabled", v)} />
            <Toggle label="Banner Enabled" value={form.banner_enabled} onChange={(v) => setField("banner_enabled", v)} />
            <NumField label="Cooldown (minutes)" value={form.rewarded_cooldown_minutes} onChange={(v) => setField("rewarded_cooldown_minutes", v)} />
            <NumField label="Coins per ad" value={form.coins_per_rewarded_ad} onChange={(v) => setField("coins_per_rewarded_ad", v)} />
            <NumField label="Interstitial interval (chapters)" value={form.interstitial_chapter_interval} onChange={(v) => setField("interstitial_chapter_interval", v)} />
            <NumField label="Daily ad cap" value={form.daily_ad_cap ?? 5} onChange={(v) => setField("daily_ad_cap", v)} />
          </div>
        )}
      </ConfigSection>

      <ConfigSection title="Coin Economy" configKey="coin_config" initial={coinConfig} qc={qc}>
        {(form, setField) => (
          <div className="grid grid-cols-3 gap-4">
            <NumField label="Chapter base cost" value={form.chapter_base_cost} onChange={(v) => setField("chapter_base_cost", v)} />
            <NumField label="Premium chapter cost" value={form.premium_chapter_cost} onChange={(v) => setField("premium_chapter_cost", v)} />
            <NumField label="Daily login coins" value={form.daily_login_coins} onChange={(v) => setField("daily_login_coins", v)} />
            <NumField label="Streak bonus multiplier" value={form.streak_bonus_multiplier} onChange={(v) => setField("streak_bonus_multiplier", v)} step={0.1} />
          </div>
        )}
      </ConfigSection>

      <ConfigSection title="Content Settings" configKey="content_config" initial={contentConfig} qc={qc}>
        {(form, setField) => (
          <div className="grid grid-cols-3 gap-4">
            <NumField label="Free chapters default" value={form.free_chapters_default} onChange={(v) => setField("free_chapters_default", v)} />
            <label>
              <div className="text-xs text-ink-2 mb-1">Feed algorithm</div>
              <select className="input" value={form.feed_algorithm} onChange={(e) => setField("feed_algorithm", e.target.value)}>
                <option value="trending">Trending</option>
                <option value="newest">Newest</option>
                <option value="personalized">Personalized</option>
              </select>
            </label>
          </div>
        )}
      </ConfigSection>

      <ConfigSection title="Feature Flags" configKey="feature_flags" initial={featureFlags} qc={qc}>
        {(form, setField) => (
          <div className="grid grid-cols-2 gap-4">
            <Toggle label="Comments" value={form.comments_enabled} onChange={(v) => setField("comments_enabled", v)} />
            <Toggle label="Audio (TTS)" value={form.audio_enabled} onChange={(v) => setField("audio_enabled", v)} />
            <Toggle label="Offline mode" value={form.offline_mode_enabled} onChange={(v) => setField("offline_mode_enabled", v)} />
            <Toggle label="Interactive stories" value={form.interactive_stories_enabled} onChange={(v) => setField("interactive_stories_enabled", v)} />
          </div>
        )}
      </ConfigSection>
    </div>
  );
}

function ConfigSection<T extends Record<string, unknown>>({
  title, configKey, initial, qc, children,
}: {
  title: string; configKey: string; initial: T; qc: ReturnType<typeof useQueryClient>;
  children: (form: T, setField: (k: string, v: unknown) => void) => React.ReactNode;
}) {
  const [form, setForm] = useState<T>(initial);
  const [saved, setSaved] = useState(false);
  const saveMut = useMutation({
    mutationFn: () => apiPut(`/admin/config/${configKey}`, { value: form }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["admin-config"] }); setSaved(true); setTimeout(() => setSaved(false), 2000); },
  });
  const setField = (k: string, v: unknown) => setForm((prev) => ({ ...prev, [k]: v }));

  return (
    <div className="card p-6 space-y-4">
      <div className="flex items-center justify-between">
        <div className="font-display text-xl">{title}</div>
        <button className="btn-primary" onClick={() => saveMut.mutate()} disabled={saveMut.isPending}>
          <Save size={14} /> {saved ? "Saved!" : saveMut.isPending ? "Saving…" : "Save"}
        </button>
      </div>
      {children(form, setField)}
    </div>
  );
}

function Toggle({ label, value, onChange }: { label: string; value: boolean; onChange: (v: boolean) => void }) {
  return (
    <label className="flex items-center gap-3 cursor-pointer select-none">
      <button type="button" onClick={() => onChange(!value)} className="text-accent">
        {value ? <ToggleRight size={28} /> : <ToggleLeft size={28} className="text-ink-3" />}
      </button>
      <span className="text-sm text-ink-1">{label}</span>
    </label>
  );
}

function NumField({ label, value, onChange, step }: { label: string; value: number; onChange: (v: number) => void; step?: number }) {
  return (
    <label>
      <div className="text-xs text-ink-2 mb-1">{label}</div>
      <input className="input" type="number" step={step ?? 1} value={value} onChange={(e) => onChange(+e.target.value)} />
    </label>
  );
}
