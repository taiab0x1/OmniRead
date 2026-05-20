import { useState, useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiPut, extractApiError } from "@/lib/api";
import { Save } from "lucide-react";

export function ConfigPage() {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ["admin-config"],
    queryFn: () => apiGet<Record<string, unknown>>("/admin/config"),
  });
  const [activeKey, setActiveKey] = useState<string | null>(null);
  const [draft, setDraft] = useState<string>("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (data && !activeKey) {
      const first = Object.keys(data)[0] || null;
      setActiveKey(first);
    }
  }, [data, activeKey]);

  useEffect(() => {
    if (data && activeKey && data[activeKey] != null) {
      setDraft(JSON.stringify(data[activeKey], null, 2));
      setError(null);
    }
  }, [activeKey, data]);

  const saveMut = useMutation({
    mutationFn: () => {
      let parsed: unknown;
      try {
        parsed = JSON.parse(draft);
      } catch (e) {
        throw new Error("Invalid JSON: " + (e as Error).message);
      }
      return apiPut(`/admin/config/${activeKey}`, { value: parsed });
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-config"] }),
    onError: (e: Error) => setError(extractApiError(e) || e.message),
  });

  if (isLoading) return <div className="text-ink-3">Loading…</div>;
  const keys = Object.keys(data ?? {});

  return (
    <div className="space-y-6">
      <header>
        <h1 className="font-display text-3xl">App config</h1>
        <p className="text-sm text-ink-2">
          Live remote config. Changes apply on next client poll. JSON is validated on save.
        </p>
      </header>
      <div className="grid grid-cols-12 gap-6">
        <aside className="col-span-3 card p-3 h-fit">
          {keys.map((k) => (
            <button
              key={k}
              onClick={() => setActiveKey(k)}
              className={`w-full text-left px-3 py-2 rounded-lg text-sm ${
                activeKey === k ? "bg-accent/15 text-ink-0" : "text-ink-2 hover:bg-white/5"
              }`}
            >
              {k}
            </button>
          ))}
          {keys.length === 0 && <div className="text-ink-3 text-sm p-3">No config keys yet.</div>}
        </aside>

        <div className="col-span-9 space-y-3">
          {activeKey ? (
            <>
              <div className="font-display text-xl">{activeKey}</div>
              <textarea
                className="input min-h-[480px] font-mono text-xs leading-relaxed"
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
              />
              {error && <div className="text-sm text-danger">{error}</div>}
              <div className="flex justify-end">
                <button
                  className="btn-primary"
                  disabled={saveMut.isPending}
                  onClick={() => saveMut.mutate()}
                >
                  <Save size={14} /> {saveMut.isPending ? "Saving…" : "Save key"}
                </button>
              </div>
            </>
          ) : (
            <div className="card p-12 text-center text-ink-3">No key selected.</div>
          )}
        </div>
      </div>
    </div>
  );
}
