import { apiGet } from "@/lib/api";
import { useQuery } from "@tanstack/react-query";

type GenreTab = { key: string; label: string; emoji: string | null };
type AppConfig = {
  genres?: { tabs?: GenreTab[] };
  [key: string]: unknown;
};

export function useGenres(): string[] {
  const { data } = useQuery({
    queryKey: ["app-config-genres"],
    queryFn: () => apiGet<AppConfig>("/config/app"),
    staleTime: 300_000,
  });
  const tabs = data?.genres?.tabs;
  if (tabs && tabs.length > 0) {
    return tabs.filter((t) => t.key !== "all" && t.key !== "discount").map((t) => t.key);
  }
  return ["dark_romance", "mafia", "billionaire", "werewolf", "vampire", "revenge", "fantasy", "horror", "scifi", "lgbtq", "ceo_romance", "war_military", "drama"];
}
