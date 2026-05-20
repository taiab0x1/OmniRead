import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiGet, apiPut } from "@/lib/api";
import { Plus, GripVertical, Trash2 } from "lucide-react";

const DEFAULT_GENRES = [
  { key: "dark_romance", label: "Dark Romance", emoji: "🖤" },
  { key: "werewolf", label: "Werewolf", emoji: "🐺" },
  { key: "billionaire", label: "Billionaire", emoji: "💰" },
  { key: "fantasy", label: "Fantasy", emoji: "⚔️" },
  { key: "scifi", label: "Sci-Fi", emoji: "🚀" },
  { key: "horror", label: "Horror", emoji: "👻" },
  { key: "revenge", label: "Revenge", emoji: "🔥" },
  { key: "mafia", label: "Mafia", emoji: "🎭" },
  { key: "lgbtq", label: "LGBTQ+", emoji: "🌈" },
  { key: "ceo_romance", label: "CEO Romance", emoji: "👔" },
  { key: "vampire", label: "Vampire", emoji: "🧛" },
  { key: "war_military", label: "War/Military", emoji: "⚡" },
];

export function GenresPage() {
  const qc = useQueryClient();
  const { data: configGenres } = useQuery({
    queryKey: ["admin-config"],
    queryFn: () => apiGet<Record<string, unknown>>("/admin/config"),
    select: (d) => (d?.genres_config as { genres: typeof DEFAULT_GENRES } | undefined)?.genres ?? DEFAULT_GENRES,
  });

  const genres = configGenres ?? DEFAULT_GENRES;
  const [newKey, setNewKey] = useState("");
  const [newLabel, setNewLabel] = useState("");
  const [newEmoji, setNewEmoji] = useState("");

  const saveMut = useMutation({
    mutationFn: (updated: typeof DEFAULT_GENRES) =>
      apiPut("/admin/config/genres_config", { value: { genres: updated } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-config"] }),
  });

  const addGenre = () => {
    if (!newKey || !newLabel) return;
    const updated = [...genres, { key: newKey, label: newLabel, emoji: newEmoji || "📖" }];
    saveMut.mutate(updated);
    setNewKey("");
    setNewLabel("");
    setNewEmoji("");
  };

  const removeGenre = (key: string) => {
    saveMut.mutate(genres.filter((g) => g.key !== key));
  };

  return (
    <div className="space-y-6">
      <header>
        <h1 className="font-display text-3xl">Genres</h1>
        <p className="text-sm text-ink-2">Manage genre categories shown in the app's discovery tabs and story creation.</p>
      </header>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="text-left text-ink-3 text-xs uppercase tracking-wide">
            <tr>
              <th className="px-6 py-3 w-12"></th>
              <th className="px-6 py-3">Key</th>
              <th className="px-6 py-3">Label</th>
              <th className="px-6 py-3">Emoji</th>
              <th className="px-6 py-3 w-16"></th>
            </tr>
          </thead>
          <tbody>
            {genres.map((g) => (
              <tr key={g.key} className="border-t border-white/5">
                <td className="px-6 py-3 text-ink-3"><GripVertical size={14} /></td>
                <td className="px-6 py-3 font-mono text-xs text-ink-1">{g.key}</td>
                <td className="px-6 py-3 text-ink-1">{g.label}</td>
                <td className="px-6 py-3 text-xl">{g.emoji}</td>
                <td className="px-6 py-3">
                  <button className="text-danger hover:text-danger/80" onClick={() => removeGenre(g.key)}>
                    <Trash2 size={14} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card p-4 flex items-end gap-3">
        <label className="flex-1">
          <div className="text-xs text-ink-2 mb-1">Key (snake_case)</div>
          <input className="input font-mono" value={newKey} onChange={(e) => setNewKey(e.target.value.toLowerCase().replace(/\s/g, "_"))} placeholder="new_genre" />
        </label>
        <label className="flex-1">
          <div className="text-xs text-ink-2 mb-1">Display label</div>
          <input className="input" value={newLabel} onChange={(e) => setNewLabel(e.target.value)} placeholder="New Genre" />
        </label>
        <label className="w-24">
          <div className="text-xs text-ink-2 mb-1">Emoji</div>
          <input className="input text-center text-xl" value={newEmoji} onChange={(e) => setNewEmoji(e.target.value)} placeholder="📖" />
        </label>
        <button className="btn-primary" onClick={addGenre} disabled={!newKey || !newLabel}>
          <Plus size={14} /> Add
        </button>
      </div>
    </div>
  );
}
