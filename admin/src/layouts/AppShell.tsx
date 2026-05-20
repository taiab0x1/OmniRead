import { Outlet, NavLink, useNavigate } from "react-router-dom";
import {
  LayoutDashboard,
  Library,
  Sparkles,
  Users,
  Shield,
  Settings,
  ScrollText,
  LogOut,
  Bell,
  Coins,
  Tags,
  Calendar,
  BarChart3,
  Upload,
  CircleDollarSign,
  Beaker,
  Filter,
} from "lucide-react";
import { useAuthStore } from "@/lib/auth-store";
import { cn } from "@/lib/utils";

const navItems = [
  { to: "/", label: "Dashboard", icon: LayoutDashboard, roles: ["super_admin", "editor", "moderator", "analytics"] },
  { to: "/analytics", label: "Analytics", icon: BarChart3, roles: ["super_admin", "analytics"] },
  { to: "/revenue", label: "Revenue", icon: CircleDollarSign, roles: ["super_admin", "analytics"] },
  { to: "/stories", label: "Stories", icon: Library, roles: ["super_admin", "editor", "moderator"] },
  { to: "/scheduling", label: "Scheduling", icon: Calendar, roles: ["super_admin", "editor"] },
  { to: "/ai", label: "AI Studio", icon: Sparkles, roles: ["super_admin", "editor"] },
  { to: "/import", label: "Content Import", icon: Upload, roles: ["super_admin", "editor"] },
  { to: "/genres", label: "Genres", icon: Tags, roles: ["super_admin", "editor"] },
  { to: "/users", label: "Users", icon: Users, roles: ["super_admin", "moderator", "analytics"] },
  { to: "/segments", label: "Segments", icon: Filter, roles: ["super_admin", "editor", "analytics"] },
  { to: "/experiments", label: "Experiments", icon: Beaker, roles: ["super_admin", "editor", "analytics"] },
  { to: "/moderation", label: "Moderation", icon: Shield, roles: ["super_admin", "moderator"] },
  { to: "/notifications", label: "Notifications", icon: Bell, roles: ["super_admin", "editor"] },
  { to: "/coins", label: "Coin Packages", icon: Coins, roles: ["super_admin"] },
  { to: "/config", label: "App Config", icon: Settings, roles: ["super_admin"] },
  { to: "/audit", label: "Audit Log", icon: ScrollText, roles: ["super_admin"] },
] as const;

export function AppShell() {
  const me = useAuthStore((s) => s.me);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();

  const visible = navItems.filter(
    (n) => !me || me.role === "super_admin" || (n.roles as readonly string[]).includes(me.role)
  );

  return (
    <div className="min-h-screen bg-bg-0">
      <aside className="fixed left-0 top-0 bottom-0 w-60 border-r border-white/5 bg-bg-1 px-3 py-5 flex flex-col">
        <div className="px-3 mb-6">
          <div className="font-display text-2xl text-ink-0 tracking-tight">OmniRead</div>
          <div className="text-xs text-ink-3 uppercase tracking-widest">Admin</div>
        </div>
        <nav className="flex flex-col gap-1 text-sm flex-1">
          {visible.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === "/"}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 px-3 py-2 rounded-lg text-ink-2 hover:text-ink-0 hover:bg-white/5",
                  isActive && "bg-accent/15 text-ink-0"
                )
              }
            >
              <Icon size={16} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto pt-3 border-t border-white/5">
          <div className="px-3 py-2 text-xs text-ink-2">
            <div className="text-ink-1 truncate">{me?.email}</div>
            <div className="text-ink-3 capitalize">{me?.role?.replace("_", " ")}</div>
          </div>
          <button
            type="button"
            onClick={() => {
              logout();
              navigate("/login");
            }}
            className="btn-ghost w-full justify-start text-ink-2"
          >
            <LogOut size={16} />
            Sign out
          </button>
        </div>
      </aside>
      <main className="pl-60">
        <div className="px-8 py-6 max-w-[1400px] mx-auto">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
