import { Navigate, useLocation } from "react-router-dom";
import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { apiGet } from "@/lib/api";
import { useAuthStore, isTokenLive, type AdminMe } from "@/lib/auth-store";

type Props = {
  children: React.ReactNode;
  roles?: AdminMe["role"][];
};

export function RequireAuth({ children, roles }: Props) {
  const location = useLocation();
  const accessToken = useAuthStore((s) => s.accessToken);
  const me = useAuthStore((s) => s.me);
  const setMe = useAuthStore((s) => s.setMe);

  const live = isTokenLive();

  const { data: fetchedMe, isLoading } = useQuery({
    queryKey: ["admin-me"],
    queryFn: () => apiGet<AdminMe>("/admin/auth/me"),
    enabled: live && !me,
  });

  useEffect(() => {
    if (fetchedMe) setMe(fetchedMe);
  }, [fetchedMe, setMe]);

  useEffect(() => {
    if (!live) {
      useAuthStore.getState().logout();
    }
  }, [live]);

  if (!accessToken || !live) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  if (!me) {
    if (isLoading) return <FullPageLoader />;
    return <FullPageLoader />;
  }
  if (roles && me.role !== "super_admin" && !roles.includes(me.role)) {
    return <ForbiddenPage />;
  }
  return <>{children}</>;
}

function FullPageLoader() {
  return (
    <div className="min-h-screen bg-bg-0 grid place-items-center text-ink-2">
      <div className="flex items-center gap-3">
        <div className="w-3 h-3 rounded-full bg-accent animate-pulse" />
        <span>Loading…</span>
      </div>
    </div>
  );
}

function ForbiddenPage() {
  return (
    <div className="min-h-screen bg-bg-0 grid place-items-center">
      <div className="card p-8 text-center max-w-md">
        <div className="text-2xl font-display mb-2">Forbidden</div>
        <div className="text-ink-2 text-sm">You don't have permission to view this section.</div>
      </div>
    </div>
  );
}
