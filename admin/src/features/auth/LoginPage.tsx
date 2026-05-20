import { useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { apiGet, apiPost, extractApiError } from "@/lib/api";
import { useAuthStore, type AdminMe } from "@/lib/auth-store";

const schema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
  totp_code: z.string().optional(),
});

type LoginResp = {
  access_token: string;
  expires_at: string;
  requires_totp_setup: boolean;
};

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore((s) => s.setAuth);
  const setMe = useAuthStore((s) => s.setMe);
  const [error, setError] = useState<string | null>(null);
  const [needsTotp, setNeedsTotp] = useState(false);

  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", password: "", totp_code: "" },
  });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      const res = await apiPost<LoginResp>("/admin/auth/login", values);
      setAuth(res.access_token, res.expires_at);
      const me = await apiGet<AdminMe>("/admin/auth/me").catch(() => null);
      if (me) setMe(me);
      const dest = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname || "/";
      navigate(dest, { replace: true });
    } catch (e) {
      const code = (e as { response?: { data?: { error?: { code?: string } } } }).response?.data?.error?.code;
      if (code === "totp_required") {
        setNeedsTotp(true);
        setError("Enter the 6-digit code from your authenticator.");
        return;
      }
      setError(extractApiError(e));
    }
  });

  return (
    <div className="min-h-screen bg-bg-0 grid place-items-center p-6">
      <div className="w-full max-w-md">
        <div className="text-center mb-6">
          <div className="font-display text-3xl text-ink-0">OmniRead</div>
          <div className="text-xs text-ink-3 uppercase tracking-widest mt-1">Admin Console</div>
        </div>
        <form onSubmit={onSubmit} className="card p-6 space-y-4">
          <div>
            <label className="text-xs text-ink-2 mb-1 block">Email</label>
            <input className="input" type="email" autoComplete="email" {...form.register("email")} />
          </div>
          <div>
            <label className="text-xs text-ink-2 mb-1 block">Password</label>
            <input className="input" type="password" autoComplete="current-password" {...form.register("password")} />
          </div>
          {needsTotp && (
            <div>
              <label className="text-xs text-ink-2 mb-1 block">2FA code</label>
              <input
                className="input tracking-[0.3em] text-center"
                inputMode="numeric"
                maxLength={6}
                {...form.register("totp_code")}
                autoFocus
              />
            </div>
          )}
          {error && <div className="text-sm text-danger">{error}</div>}
          <button className="btn-primary w-full" disabled={form.formState.isSubmitting}>
            {form.formState.isSubmitting ? "Signing in…" : "Sign in"}
          </button>
        </form>
        <div className="text-center text-xs text-ink-3 mt-4">
          Restricted access. All actions are audited.
        </div>
      </div>
    </div>
  );
}
