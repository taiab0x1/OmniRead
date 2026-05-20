import { createBrowserRouter, Navigate } from "react-router-dom";
import { AppShell } from "@/layouts/AppShell";
import { RequireAuth } from "@/routes/RequireAuth";
import { LoginPage } from "@/features/auth/LoginPage";
import { DashboardPage } from "@/features/analytics/DashboardPage";
import { StoriesListPage } from "@/features/stories/StoriesListPage";
import { StoryEditorPage } from "@/features/stories/StoryEditorPage";
import { ChapterEditorPage } from "@/features/chapters/ChapterEditorPage";
import { AIStudioPage } from "@/features/ai/AIStudioPage";
import { UsersListPage } from "@/features/users/UsersListPage";
import { UserDetailPage } from "@/features/users/UserDetailPage";
import { ModerationPage } from "@/features/moderation/ModerationPage";
import { FullConfigPage } from "@/features/config/FullConfigPage";
import { AuditPage } from "@/features/audit/AuditPage";
import { AnalyticsPage } from "@/features/analytics/AnalyticsPage";
import { NotificationsPage } from "@/features/notifications/NotificationsPage";
import { CoinPackagesPage } from "@/features/coins/CoinPackagesPage";
import { GenresPage } from "@/features/genres/GenresPage";
import { SchedulingPage } from "@/features/scheduling/SchedulingPage";
import { ContentImportPage } from "@/features/imports/ContentImportPage";
import { RevenuePage } from "@/features/revenue/RevenuePage";
import { SegmentsPage } from "@/features/segments/SegmentsPage";
import { ExperimentsPage } from "@/features/experiments/ExperimentsPage";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  {
    element: (
      <RequireAuth>
        <AppShell />
      </RequireAuth>
    ),
    children: [
      { path: "/", element: <DashboardPage /> },
      { path: "/analytics", element: <AnalyticsPage /> },
      { path: "/revenue", element: <RevenuePage /> },
      { path: "/stories", element: <StoriesListPage /> },
      { path: "/stories/:id", element: <StoryEditorPage /> },
      { path: "/chapters", element: <Navigate to="/stories" replace /> },
      { path: "/chapters/:id", element: <ChapterEditorPage /> },
      { path: "/scheduling", element: <SchedulingPage /> },
      { path: "/ai", element: <AIStudioPage /> },
      { path: "/import", element: <ContentImportPage /> },
      { path: "/segments", element: <SegmentsPage /> },
      { path: "/experiments", element: <ExperimentsPage /> },
      { path: "/users", element: <UsersListPage /> },
      { path: "/users/:id", element: <UserDetailPage /> },
      { path: "/moderation", element: <ModerationPage /> },
      { path: "/notifications", element: <NotificationsPage /> },
      { path: "/coins", element: <CoinPackagesPage /> },
      { path: "/genres", element: <GenresPage /> },
      { path: "/config", element: <FullConfigPage /> },
      { path: "/audit", element: <AuditPage /> },
    ],
  },
  { path: "*", element: <Navigate to="/" replace /> },
]);
