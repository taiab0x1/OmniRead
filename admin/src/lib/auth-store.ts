import { create } from "zustand";
import { persist } from "zustand/middleware";

export type AdminMe = {
  id: string;
  email: string;
  name: string | null;
  role: "super_admin" | "editor" | "moderator" | "analytics";
  permissions: Record<string, unknown> | null;
  last_login_at: string | null;
};

type AuthState = {
  accessToken: string | null;
  expiresAt: string | null;
  me: AdminMe | null;
  setAuth: (token: string, expiresAt: string) => void;
  setMe: (me: AdminMe) => void;
  logout: () => void;
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      expiresAt: null,
      me: null,
      setAuth: (accessToken, expiresAt) => set({ accessToken, expiresAt }),
      setMe: (me) => set({ me }),
      logout: () => set({ accessToken: null, expiresAt: null, me: null }),
    }),
    { name: "omniread-admin-auth" }
  )
);

export function isTokenLive() {
  const { accessToken, expiresAt } = useAuthStore.getState();
  if (!accessToken || !expiresAt) return false;
  return new Date(expiresAt).getTime() > Date.now() + 5_000;
}

export function hasRole(role: AdminMe["role"] | undefined, allowed: AdminMe["role"][]) {
  if (!role) return false;
  if (role === "super_admin") return true;
  return allowed.includes(role);
}
